package com.example.llm.text.analysis.resilience;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 熔断器单元测试：状态机 CLOSED → OPEN → HALF_OPEN → CLOSED 全路径
 */
class CircuitBreakerTest {

    @Test
    @DisplayName("初始状态：关闭，放行请求")
    void 初始状态放行() {
        CircuitBreaker breaker = new CircuitBreaker(3, 1000);
        assertTrue(breaker.allowRequest());
        assertEquals(CircuitBreaker.State.CLOSED, breaker.getState());
    }

    @Test
    @DisplayName("连续失败达到阈值：熔断打开，拒绝请求")
    void 失败达到阈值打开熔断() {
        CircuitBreaker breaker = new CircuitBreaker(3, 1000);
        for (int i = 0; i < 3; i++) {
            breaker.recordFailure();
        }
        assertFalse(breaker.allowRequest());
        assertEquals(CircuitBreaker.State.OPEN, breaker.getState());
    }

    @Test
    @DisplayName("冷却期结束：进入半开，放行试探请求")
    void 冷却期结束进入半开() throws InterruptedException {
        CircuitBreaker breaker = new CircuitBreaker(2, 300);
        breaker.recordFailure();
        breaker.recordFailure();
        assertFalse(breaker.allowRequest());

        Thread.sleep(400); // 等待冷却期结束

        assertTrue(breaker.allowRequest());
        assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.getState());
    }

    @Test
    @DisplayName("半开试探成功：熔断关闭，服务恢复")
    void 半开试探成功恢复() throws InterruptedException {
        CircuitBreaker breaker = new CircuitBreaker(2, 300);
        breaker.recordFailure();
        breaker.recordFailure();
        Thread.sleep(400);
        breaker.allowRequest(); // 进入半开

        breaker.recordSuccess();

        assertEquals(CircuitBreaker.State.CLOSED, breaker.getState());
        assertEquals(0, breaker.getFailureCount());
        assertTrue(breaker.allowRequest());
    }

    @Test
    @DisplayName("半开试探失败：熔断重新打开")
    void 半开试探失败重新打开() throws InterruptedException {
        CircuitBreaker breaker = new CircuitBreaker(2, 300);
        breaker.recordFailure();
        breaker.recordFailure();
        Thread.sleep(400);
        breaker.allowRequest(); // 进入半开

        breaker.recordFailure();

        assertEquals(CircuitBreaker.State.OPEN, breaker.getState());
        assertFalse(breaker.allowRequest());
    }

    @Test
    @DisplayName("失败未达阈值：保持关闭")
    void 失败未达阈值() {
        CircuitBreaker breaker = new CircuitBreaker(3, 1000);
        breaker.recordFailure();
        breaker.recordFailure();
        assertTrue(breaker.allowRequest());
        assertEquals(CircuitBreaker.State.CLOSED, breaker.getState());
    }
}
