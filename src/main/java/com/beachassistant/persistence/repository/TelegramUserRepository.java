package com.beachassistant.persistence.repository;

import com.beachassistant.persistence.entity.TelegramUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TelegramUserRepository extends JpaRepository<TelegramUserEntity, Long> {
    Optional<TelegramUserEntity> findByTelegramUserId(Long telegramUserId);
}
