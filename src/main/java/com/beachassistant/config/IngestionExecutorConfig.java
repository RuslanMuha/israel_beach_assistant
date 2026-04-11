package com.beachassistant.config;

import com.beachassistant.integration.http.BeachIntegrationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class IngestionExecutorConfig {

    @Bean(name = "beachIngestionExecutor")
    public ThreadPoolTaskExecutor beachIngestionExecutor(BeachIntegrationProperties integration) {
        int n = integration.getIngestion().getMaxConcurrentBeaches();
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(n);
        ex.setMaxPoolSize(n);
        // Bounded queue so large beach batches do not reject tasks (SynchronousQueue with capacity 0 would).
        ex.setQueueCapacity(512);
        ex.setThreadNamePrefix("beach-ingest-");
        ex.initialize();
        return ex;
    }
}
