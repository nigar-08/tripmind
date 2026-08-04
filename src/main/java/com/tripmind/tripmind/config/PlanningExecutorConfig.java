package com.tripmind.tripmind.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class PlanningExecutorConfig {

    @Bean(name = "tripPlanningExecutor")
    public Executor tripPlanningExecutor(
            @Value("${tripmind.planning.executor.core-size:4}") int coreSize,
            @Value("${tripmind.planning.executor.max-size:8}") int maxSize,
            @Value("${tripmind.planning.executor.queue-capacity:100}") int queueCapacity
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("trip-planner-");
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
