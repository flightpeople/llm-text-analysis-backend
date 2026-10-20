package com.example.llm.text.analysis.service;

import com.example.llm.text.analysis.common.BusinessException;
import com.example.llm.text.analysis.common.ErrorCode;
import com.example.llm.text.analysis.llm.LlmClient;
import com.example.llm.text.analysis.resilience.CircuitBreaker;
import com.example.llm.text.analysis.service.impl.TextAnalysisServiceImpl;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 文本分析服务单元测试
 * 覆盖：正常流程 / 缓存命中 / 异常场景 / 熔断打开 / 并发防击穿 / 批量分析 / 参数校验
 */
class TextAnalysisServiceTest {

    private LlmClient llmClient;
    private CircuitBreaker circuitBreaker;
    private TextAnalysisServiceImpl service;

    @BeforeEach
    void setUp() {
        llmClient = Mockito.mock(LlmClient.class);
        circuitBreaker = new CircuitBreaker(3, 1000);
        Cache<String, String> cache = Caffeine.newBuilder().maximumSize(100).build();
        Executor executor = task -> task.run();
        service = new TextAnalysisServiceImpl(llmClient, circuitBreaker, cache, executor);
    }

    @Test
    @DisplayName("正常流程：摘要返回大模型结果")
    void summarize_正常流程() throws IOException {
        when(llmClient.chat(anyString())).thenReturn("本文介绍了Spring Boot的核心特性。");

        String result = service.summarize("Spring Boot 是一个快速开发框架。Spring Boot 简化了配置。");

        assertEquals("本文介绍了Spring Boot的核心特性。", result);
        verify(llmClient, times(1)).chat(anyString());
    }

    @Test
    @DisplayName("缓存命中：相同文本第二次不调用大模型")
    void summarize_缓存命中() throws IOException {
        when(llmClient.chat(anyString())).thenReturn("摘要结果");

        service.summarize("同一段文本。");
        service.summarize("同一段文本。");

        verify(llmClient, times(1)).chat(anyString());
    }

    @Test
    @DisplayName("异常场景：大模型返回业务异常时向上抛出并记录失败")
    void summarize_大模型异常() throws IOException {
        when(llmClient.chat(anyString()))
                .thenThrow(new BusinessException(ErrorCode.LLM_RATE_LIMITED, "限流"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.summarize("测试文本"));
        assertEquals(ErrorCode.LLM_RATE_LIMITED, exception.getCode());
        assertEquals(1, circuitBreaker.getFailureCount());
    }

    @Test
    @DisplayName("熔断场景：连续失败达到阈值后快速失败，不再调用大模型")
    void summarize_熔断打开() throws IOException {
        when(llmClient.chat(anyString())).thenThrow(new IOException("连接超时"));

        // 前3次调用会真正走到大模型（阈值3）
        for (int i = 0; i < 3; i++) {
            final int index = i;
            assertThrows(BusinessException.class, () -> service.summarize("文本" + index));
        }
        // 第4次：熔断已打开，直接快速失败
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.summarize("文本4"));
        assertEquals(ErrorCode.CIRCUIT_OPEN, exception.getCode());
        assertTrue(exception.getMessage().contains("熔断"));

        // 大模型只被调用了3次
        verify(llmClient, times(3)).chat(anyString());
    }

    @Test
    @DisplayName("并发场景：50线程相同文本，大模型只被调用1次（缓存防击穿）")
    void summarize_并发缓存() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);
        when(llmClient.chat(anyString())).thenAnswer(invocation -> {
            callCount.incrementAndGet();
            Thread.sleep(50); // 模拟网络耗时，放大并发窗口
            return "并发摘要";
        });

        int threadCount = 50;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        Executor pool = Executors.newFixedThreadPool(16);

        for (int i = 0; i < threadCount; i++) {
            pool.execute(() -> {
                try {
                    startLatch.await();
                    service.summarize("并发测试文本");
                } catch (Exception e) {
                    // 忽略，断言在下方
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));

        assertEquals(1, callCount.get(), "相同文本并发请求，大模型应只被调用1次");
    }

    @Test
    @DisplayName("批量分析：线程池并发执行，返回原文到结果的映射")
    void analyzeBatch_正常() throws IOException {
        when(llmClient.chat(anyString())).thenReturn("批量结果");

        Map<String, String> result = service.analyzeBatch(
                List.of("文本一", "文本二", "文本三"), AnalysisType.SUMMARY);

        assertEquals(3, result.size());
        verify(llmClient, times(3)).chat(anyString());
    }

    @Test
    @DisplayName("参数校验：空文本直接抛参数异常")
    void summarize_空文本() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.summarize("   "));
        assertEquals(ErrorCode.PARAM_ERROR, exception.getCode());
    }
}
