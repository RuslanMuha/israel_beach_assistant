package com.beachassistant.web.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;

@Getter
@Setter
public class ClosureOverrideRequestDto {
    private boolean closed;

    @Size(max = 255)
    private String reason;

    private ZonedDateTime effectiveFrom;
    private ZonedDateTime effectiveUntil;
}
