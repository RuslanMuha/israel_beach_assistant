package com.beachassistant.source.contract;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SourceRequest {

    private final String beachSlug;
    private final String externalKey;
    private final String externalName;
}
