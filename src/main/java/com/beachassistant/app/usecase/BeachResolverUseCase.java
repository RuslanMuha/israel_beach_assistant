package com.beachassistant.app.usecase;

import com.beachassistant.common.exception.BeachNotFoundException;
import com.beachassistant.persistence.entity.BeachEntity;
import com.beachassistant.persistence.repository.BeachRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BeachResolverUseCase {

    private final BeachRepository beachRepository;

    public BeachResolverUseCase(BeachRepository beachRepository) {
        this.beachRepository = beachRepository;
    }

    @Cacheable(value = "beachAlias", key = "#identifier.toLowerCase()")
    public BeachEntity resolve(String identifier) {
        String cleaned = identifier.trim().toLowerCase();
        return beachRepository.findBySlugAndActiveTrue(cleaned)
                .or(() -> beachRepository.findByAliasIgnoreCase(cleaned))
                .orElseThrow(() -> new BeachNotFoundException(identifier));
    }

    public List<BeachEntity> listAll() {
        return beachRepository.findAllByActiveTrue();
    }
}
