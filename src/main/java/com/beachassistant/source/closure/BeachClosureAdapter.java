package com.beachassistant.source.closure;

import com.beachassistant.common.enums.SourceType;
import com.beachassistant.source.contract.FetchResult;
import com.beachassistant.source.contract.SourceAdapter;
import com.beachassistant.source.contract.SourceDescriptor;
import com.beachassistant.source.contract.SourceRequest;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Scrapes municipal beach status pages to detect closures (red flag, construction, sewage event).
 * Each beach's target URL + selector/regex is declared in {@link BeachClosureProperties}; the
 * adapter emits one {@link BeachClosureRecord} per beach per fetch.
 *
 * <p>Disabled by default ({@code beach.providers.closure.enabled=false}); enable per environment
 * once the scraping contract has been validated for the specific municipal site.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "beach.providers.closure", name = "enabled", havingValue = "true")
public class BeachClosureAdapter implements SourceAdapter<BeachClosureRecord> {

    private static final ZoneId ISRAEL = ZoneId.of("Asia/Jerusalem");

    private final BeachClosureProperties props;

    public BeachClosureAdapter(BeachClosureProperties props) {
        this.props = props;
    }

    @Override
    public SourceType sourceType() {
        return SourceType.BEACH_CLOSURE;
    }

    @Override
    public SourceDescriptor descriptor() {
        return new SourceDescriptor(
                "beach-closure",
                sourceType(),
                "Municipal beach closure scraper",
                props.getCadence(),
                "BEACH_CLOSURE"
        );
    }

    @Override
    public FetchResult<BeachClosureRecord> fetch(SourceRequest request) {
        BeachClosureProperties.ScrapeTarget target = props.getTargets().get(request.getBeachSlug());
        if (target == null || target.getUrl() == null) {
            return FetchResult.failure(sourceType(), "No closure target configured for " + request.getBeachSlug());
        }
        ZonedDateTime now = ZonedDateTime.now(ISRAEL);
        try {
            Document doc = Jsoup.connect(target.getUrl())
                    .timeout((int) props.getReadTimeout().toMillis())
                    .userAgent("Mozilla/5.0 (BeachAssistant closure-adapter)")
                    .get();
            boolean closed = evaluateClosed(doc, target);
            String reason = closed ? extractReason(doc, target) : null;
            BeachClosureRecord record = BeachClosureRecord.builder()
                    .beachSlug(request.getBeachSlug())
                    .closed(closed)
                    .reason(reason)
                    .source("FEED")
                    .effectiveFrom(now)
                    .effectiveUntil(null)
                    .rawPayloadJson("{\"snippet\":" + quoteJson(doc.title()) + "}")
                    .capturedAt(now)
                    .build();
            return FetchResult.success(sourceType(), List.of(record));
        } catch (Exception e) {
            log.warn("Closure scrape failed for beach={} url={}: {}",
                    request.getBeachSlug(), target.getUrl(), e.getMessage());
            return FetchResult.failure(sourceType(), e.getMessage());
        }
    }

    private static boolean evaluateClosed(Document doc, BeachClosureProperties.ScrapeTarget target) {
        if (target.getClosedSelector() != null && !target.getClosedSelector().isBlank()
                && !doc.select(target.getClosedSelector()).isEmpty()) {
            return true;
        }
        if (target.getClosedRegex() != null && !target.getClosedRegex().isBlank()) {
            Pattern p = Pattern.compile(target.getClosedRegex(), Pattern.CASE_INSENSITIVE);
            return p.matcher(doc.text()).find();
        }
        return false;
    }

    private static String extractReason(Document doc, BeachClosureProperties.ScrapeTarget target) {
        if (target.getClosedSelector() != null) {
            String text = doc.select(target.getClosedSelector()).text();
            if (text != null && !text.isBlank()) {
                return text.length() > 240 ? text.substring(0, 240) : text;
            }
        }
        return target.getDefaultReason() != null ? target.getDefaultReason() : "Closed per municipal source";
    }

    private static String quoteJson(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
