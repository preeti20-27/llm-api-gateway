package com.llmgateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Enables @Async (used by UsageLogService, so writing a usage log never adds
 * latency to the request the client is actually waiting on) and backs it with a
 * virtual-thread-per-task executor — consistent with spring.threads.virtual.enabled
 * for the web server itself. Writing a usage log is one small I/O-bound JDBC insert
 * per request: exactly the workload virtual threads exist for (cheap to create,
 * cheap to block on I/O, no thread-pool sizing to tune).
 * <p>
 * The executor is a real @Bean (not just returned from getAsyncExecutor()) so it can
 * also be injected directly — from Phase 5, FailoverLlmProvider uses it to run the
 * Resilience4j-decorated Gemini call on, since @TimeLimiter requires an asynchronous
 * (CompletableFuture-returning) method to actually enforce a timeout on.
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService virtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    public Executor getAsyncExecutor() {
        return new TaskExecutorAdapter(virtualThreadExecutor());
    }
}
