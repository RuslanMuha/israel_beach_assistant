package com.beachassistant.web.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class BeachListItemDto {

    private final String id;
    private final String displayName;
    private final String city;
    private final List<String> aliases;
    private final boolean hasCamera;
    private final boolean hasLifeguards;
    private final boolean hasJellyfishSource;
    private final boolean isActive;
}
