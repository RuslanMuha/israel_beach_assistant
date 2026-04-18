package com.beachassistant.persistence.repository;

import com.beachassistant.persistence.entity.TelegramUserPreferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TelegramUserPreferenceRepository extends JpaRepository<TelegramUserPreferenceEntity, Long> {
}
