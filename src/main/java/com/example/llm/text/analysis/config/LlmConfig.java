package com.example.llm.text.analysis.config;

import com.example.llm.text.analysis.resilience.CircuitBreaker;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * LLM 相关 Bean 配置：熔断器 + 本地缓存
 */
@Configuration
public class LlmConfig {

    @Bean
    public CircuitBreaker llmCircuitBreaker(
            @Value("${llm.circuit-breaker.failure-threshold:5}") int failureThreshold,
            @Value("${llm.circuit-breaker.open-duration-seconds:30}") long openDurationSeconds) {
        return new CircuitBreaker(failureThreshold, TimeUnit.SECONDS.toMillis(openDurationSeconds));
    }

    @Bean("analysisCache")
    public Cache<String, String> analysisCache(
            @Value("${llm.cache.maximum-size:10000}") long maximumSize,
            @Value("${llm.cache.expire-after-write-hours:1}") long expireAfterWriteHours) {
        return Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(Duration.ofHours(expireAfterWriteHours))
                .build();
    }
}
