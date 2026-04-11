package com.beachassistant.common.util;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
public class RequestIdFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put(MdcKeys.REQUEST_ID, requestId);
        if (request instanceof HttpServletRequest http) {
            String path = http.getRequestURI();
            String slug = extractSlugFromPath(path);
            if (slug != null) {
                MDC.put(MdcKeys.BEACH_SLUG, slug);
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    private String extractSlugFromPath(String path) {
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if ("beaches".equals(parts[i]) && i + 1 < parts.length) {
                return parts[i + 1];
            }
        }
        return null;
    }
}
