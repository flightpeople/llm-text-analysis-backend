package com.example.llm.text.analysis.resilience;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 简易熔断器：CLOSED → OPEN → HALF_OPEN 状态机
 * 连续失败达到阈值后打开熔断快速失败；冷却期结束后进入半开状态放行试探请求，
 * 试探成功则关闭恢复服务，试探失败则重新打开。
 */
@Slf4j
public class CircuitBreaker {

    public enum State {
        CLOSED, OPEN, HALF_OPEN
    }

    private final int failureThreshold;
    private final long openDurationMs;

    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private volatile long openedAt = 0L;

    public CircuitBreaker(int failureThreshold, long openDurationMs) {
        this.failureThreshold = failureThreshold;
        this.openDurationMs = openDurationMs;
    }

    /**
     * 判断当前是否放行请求
     */
    public boolean allowRequest() {
        State current = state.get();
        if (current == State.OPEN) {
            if (System.currentTimeMillis() - openedAt >= openDurationMs) {
                // 冷却期结束，进入半开状态放行试探请求
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    log.info("熔断器进入半开状态，放行试探请求");
                }
                return true;
            }
            return false;
        }
        return true;
    }

    /**
     * 记录一次成功：半开状态下恢复关闭
     */
    public void recordSuccess() {
        failureCount.set(0);
        State previous = state.getAndSet(State.CLOSED);
        if (previous == State.HALF_OPEN) {
            log.info("熔断器关闭，服务恢复正常");
        }
    }

    /**
     * 记录一次失败：达到阈值打开熔断；半开状态下失败则重新打开
     */
    public void recordFailure() {
        int failures = failureCount.incrementAndGet();
        State current = state.get();
        if (current == State.HALF_OPEN) {
            reopen();
        } else if (current == State.CLOSED && failures >= failureThreshold) {
            if (state.compareAndSet(State.CLOSED, State.OPEN)) {
                openedAt = System.currentTimeMillis();
                log.warn("熔断器打开，连续失败次数: {}", failures);
            }
        }
    }

    private void reopen() {
        state.set(State.OPEN);
        openedAt = System.currentTimeMillis();
        log.warn("半开状态试探失败，熔断器重新打开");
    }

    public State getState() {
        return state.get();
    }

    public int getFailureCount() {
        return failureCount.get();
    }
}
