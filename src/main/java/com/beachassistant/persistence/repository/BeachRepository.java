package com.beachassistant.persistence.repository;

import com.beachassistant.persistence.entity.BeachEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BeachRepository extends JpaRepository<BeachEntity, Long> {

    @Query("SELECT b FROM BeachEntity b JOIN FETCH b.city WHERE b.slug = :slug AND b.active = true")
    Optional<BeachEntity> findBySlugAndActiveTrue(@Param("slug") String slug);

    @Query("SELECT DISTINCT b FROM BeachEntity b JOIN FETCH b.city WHERE b.active = true")
    List<BeachEntity> findAllByActiveTrue();

    @Query("SELECT DISTINCT b FROM BeachEntity b JOIN FETCH b.city JOIN b.aliases a WHERE LOWER(a) = LOWER(:alias) AND b.active = true")
    Optional<BeachEntity> findByAliasIgnoreCase(@Param("alias") String alias);
}
