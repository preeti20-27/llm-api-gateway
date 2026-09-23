package com.llmgateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Enables @Async (used by UsageLogService, so writing a usage log never adds
 * latency to the request the client is actually waiting on) and backs it with a
 * virtual-thread-per-task executor — consistent with spring.threads.virtual.enabled
 * for the web server itself. Writing a usage log is one small I/O-bound JDBC insert
 * per request: exactly the workload virtual threads exist for (cheap to create,
 * cheap to block on I/O, no thread-pool sizing to tune).
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }
}
