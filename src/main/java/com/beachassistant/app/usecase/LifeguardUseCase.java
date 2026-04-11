package com.beachassistant.app.usecase;

import com.beachassistant.common.enums.FreshnessStatus;
import com.beachassistant.common.enums.SourceType;
import com.beachassistant.decision.freshness.FreshnessService;
import com.beachassistant.persistence.entity.BeachEntity;
import com.beachassistant.persistence.entity.LifeguardScheduleEntity;
import com.beachassistant.persistence.repository.LifeguardScheduleRepository;
import com.beachassistant.web.dto.LifeguardHoursDto;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class LifeguardUseCase {

    private static final ZoneId ISRAEL = ZoneId.of("Asia/Jerusalem");

    private final BeachResolverUseCase beachResolver;
    private final LifeguardScheduleRepository scheduleRepository;
    private final FreshnessService freshnessService;

    public LifeguardUseCase(BeachResolverUseCase beachResolver,
                             LifeguardScheduleRepository scheduleRepository,
                             FreshnessService freshnessService) {
        this.beachResolver = beachResolver;
        this.scheduleRepository = scheduleRepository;
        this.freshnessService = freshnessService;
    }

    public LifeguardHoursDto getHours(String slugOrAlias) {
        BeachEntity beach = beachResolver.resolve(slugOrAlias);
        LocalDate today = LocalDate.now(ISRAEL);
        int dayOfWeek = today.getDayOfWeek().getValue();

        List<LifeguardScheduleEntity> schedules =
                scheduleRepository.findActiveSchedulesForDate(beach.getId(), today);

        LifeguardScheduleEntity schedule = schedules.stream()
                .filter(s -> s.getDayOfWeek() == null || s.getDayOfWeek() == dayOfWeek)
                .findFirst()
                .orElse(null);

        if (schedule == null) {
            return LifeguardHoursDto.builder()
                    .beach(beach.getDisplayName())
                    .onDuty(false)
                    .freshnessStatus(FreshnessStatus.EXPIRED)
                    .build();
        }

        LocalTime now = LocalTime.now(ISRAEL);
        boolean onDuty = schedule.getOpenTime() != null && schedule.getCloseTime() != null
                && !now.isBefore(schedule.getOpenTime()) && now.isBefore(schedule.getCloseTime());

        FreshnessStatus freshness = freshnessService.classify(
                schedule.getCapturedAt(), SourceType.LIFEGUARD_SCHEDULE);

        return LifeguardHoursDto.builder()
                .beach(beach.getDisplayName())
                .onDuty(onDuty)
                .openTime(schedule.getOpenTime() != null ? schedule.getOpenTime().toString() : null)
                .closeTime(schedule.getCloseTime() != null ? schedule.getCloseTime().toString() : null)
                .scheduleType(schedule.getScheduleType().name())
                .freshnessStatus(freshness)
                .capturedAt(schedule.getCapturedAt())
                .build();
    }
}
