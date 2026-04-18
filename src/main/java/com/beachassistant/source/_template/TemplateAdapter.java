package com.beachassistant.source._template;

import com.beachassistant.common.enums.SourceType;
import com.beachassistant.source.contract.FetchResult;
import com.beachassistant.source.contract.SourceAdapter;
import com.beachassistant.source.contract.SourceDescriptor;
import com.beachassistant.source.contract.SourceRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Scaffold for new adapters. Copy this file into a new package, rename, and fill in the
 * integration details. Keep the {@link #descriptor()} override — it is the single source of
 * truth for cadence and admin tooling.
 *
 * <p>Wire on via {@code beach.providers.template.enabled=true} — the default is off so
 * scaffolding doesn't run in production.</p>
 */
@Component
@ConditionalOnProperty(prefix = "beach.providers.template", name = "enabled", havingValue = "true")
public class TemplateAdapter implements SourceAdapter<TemplateRecord> {

    @Override
    public SourceType sourceType() {
        // Replace with an existing enum value or add a new one in com.beachassistant.common.enums.SourceType.
        return SourceType.HEALTH_ADVISORY;
    }

    @Override
    public SourceDescriptor descriptor() {
        return new SourceDescriptor(
                "template",
                sourceType(),
                "Template adapter",
                Duration.ofHours(1),
                sourceType().name()
        );
    }

    @Override
    public FetchResult<TemplateRecord> fetch(SourceRequest request) {
        // TODO: call external system here; handle 429/5xx via OutboundHttpService.
        return FetchResult.success(sourceType(), java.util.List.of());
    }
}
