package com.beachassistant.catalog;

import com.beachassistant.persistence.entity.BeachEntity;
import com.beachassistant.persistence.entity.CityEntity;
import com.beachassistant.persistence.repository.BeachRepository;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Upserts beaches described in {@link BeachCatalogProperties} on application startup.
 * Designed to be idempotent: re-running the loader with the same config produces no DB churn,
 * while additions/updates reflect on next boot. Cities must already exist (seeded via Flyway);
 * unknown cities are skipped with a warning so a typo can't silently create a stray city.
 */
@Slf4j
@Component
public class BeachCatalogLoader {

    private final BeachCatalogProperties properties;
    private final BeachRepository beachRepository;
    private final EntityManager entityManager;

    public BeachCatalogLoader(BeachCatalogProperties properties,
                              BeachRepository beachRepository,
                              EntityManager entityManager) {
        this.properties = properties;
        this.beachRepository = beachRepository;
        this.entityManager = entityManager;
    }

    @PostConstruct
    public void load() {
        if (!properties.isEnabled()) {
            return;
        }
        if (properties.getBeaches().isEmpty()) {
            log.info("Beach catalog is enabled but has no entries.");
            return;
        }
        try {
            int upserted = upsertAll();
            log.info("Beach catalog loaded: {} entries upserted", upserted);
        } catch (Exception e) {
            // Loader failure must not prevent startup: Flyway seed still works.
            log.error("Beach catalog load failed, falling back to Flyway seed only", e);
        }
    }

    @Transactional
    public int upsertAll() {
        int count = 0;
        for (BeachCatalogProperties.BeachEntry entry : properties.getBeaches()) {
            if (entry.getSlug() == null || entry.getSlug().isBlank()) {
                log.warn("Skipping catalog entry without slug: {}", entry.getDisplayName());
                continue;
            }
            CityEntity city = findCity(entry.getCity());
            if (city == null) {
                log.warn("Skipping beach '{}' (city '{}' not found in DB; seed it via Flyway first)",
                        entry.getSlug(), entry.getCity());
                continue;
            }
            BeachEntity beach = beachRepository.findBySlugAndActiveTrue(entry.getSlug())
                    .orElseGet(BeachEntity::new);
            beach.setSlug(entry.getSlug());
            beach.setDisplayName(entry.getDisplayName() != null
                    ? entry.getDisplayName() : entry.getSlug());
            beach.setCity(city);
            beach.setLatitude(entry.getLatitude());
            beach.setLongitude(entry.getLongitude());
            beach.setActive(entry.isActive());
            beach.setSupportsSwimming(entry.isSupportsSwimming());
            beach.setHasLifeguards(entry.isHasLifeguards());
            beach.setHasCamera(entry.isHasCamera());
            beach.setHasJellyfishSource(entry.isHasJellyfishSource());
            beach.setNotes(entry.getNotes());
            beach.setAliases(mergeAliases(beach.getAliases(), entry.getAliases()));
            beachRepository.save(beach);
            count++;
        }
        return count;
    }

    private List<String> mergeAliases(List<String> existing, List<String> incoming) {
        Set<String> merged = new LinkedHashSet<>();
        if (existing != null) {
            existing.stream()
                    .filter(s -> s != null && !s.isBlank())
                    .forEach(s -> merged.add(s.toLowerCase(Locale.ROOT)));
        }
        if (incoming != null) {
            incoming.stream()
                    .filter(s -> s != null && !s.isBlank())
                    .forEach(s -> merged.add(s.toLowerCase(Locale.ROOT)));
        }
        return new ArrayList<>(merged);
    }

    private CityEntity findCity(String cityName) {
        if (cityName == null || cityName.isBlank()) {
            return null;
        }
        try {
            Optional<CityEntity> found = entityManager.createQuery(
                            "SELECT c FROM CityEntity c WHERE LOWER(c.name) = LOWER(:n)",
                            CityEntity.class)
                    .setParameter("n", cityName.trim())
                    .getResultStream()
                    .findFirst();
            return found.orElse(null);
        } catch (NoResultException e) {
            return null;
        }
    }
}
