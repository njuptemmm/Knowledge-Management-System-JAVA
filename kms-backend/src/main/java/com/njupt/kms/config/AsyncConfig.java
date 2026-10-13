package com.njupt.kms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class AsyncConfig {

    /**
     * 解析专用线程池。有界队列 + CallerRunsPolicy：
     * 队列满时由调用线程执行，形成天然背压，避免任务被静默丢弃。
     * 线程数刻意压得很低——解析是 CPU 密集且不紧急的后台任务，
     * 不应与 Web 请求争抢资源。
     */
    @Bean("attachmentParseExecutor")
    public Executor attachmentParseExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("attach-parse-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
