package com.beachassistant.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Runs ingestion coordinator work (admin-triggered async cycles) off Tomcat threads.
 * Must be separate from {@code beachIngestionExecutor} to avoid deadlocks when the coordinator
 * {@code join()}s parallel beach tasks that use the beach pool.
 */
@Configuration
public class IngestionCoordinatorConfig {

    @Bean(name = "ingestionCoordinatorExecutor")
    public AsyncTaskExecutor ingestionCoordinatorExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(2);
        ex.setQueueCapacity(16);
        ex.setThreadNamePrefix("beach-ingest-coord-");
        ex.initialize();
        return ex;
    }
}
