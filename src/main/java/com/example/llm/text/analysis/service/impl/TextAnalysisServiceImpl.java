package com.example.llm.text.analysis.service.impl;

import com.example.llm.text.analysis.common.BusinessException;
import com.example.llm.text.analysis.common.ErrorCode;
import com.example.llm.text.analysis.llm.LlmClient;
import com.example.llm.text.analysis.resilience.CircuitBreaker;
import com.example.llm.text.analysis.service.AnalysisType;
import com.example.llm.text.analysis.service.TextAnalysisService;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

/**
 * 文本分析服务实现
 * 能力：摘要 / 情感分析 / 翻译 / 批量分析
 * 机制：文本预处理（清洗去重分片）→ 本地缓存（Caffeine）→ 熔断保护 → LLM 调用
 */
@Slf4j
@Service
public class TextAnalysisServiceImpl implements TextAnalysisService {

    private static final int MAX_INPUT_LENGTH = 2000;

    private final LlmClient llmClient;
    private final CircuitBreaker circuitBreaker;
    private final Cache<String, String> analysisCache;
    private final Executor textPreprocessExecutor;

    public TextAnalysisServiceImpl(LlmClient llmClient,
                                   CircuitBreaker circuitBreaker,
                                   @Qualifier("analysisCache") Cache<String, String> analysisCache,
                                   @Qualifier("textPreprocessExecutor") Executor textPreprocessExecutor) {
        this.llmClient = llmClient;
        this.circuitBreaker = circuitBreaker;
        this.analysisCache = analysisCache;
        this.textPreprocessExecutor = textPreprocessExecutor;
    }

    @Override
    public String summarize(String text) {
        String cleaned = preprocess(text);
        String cacheKey = "summary::" + cleaned;
        return analysisCache.get(cacheKey, key -> callLlm(buildSummaryPrompt(cleaned)));
    }

    @Override
    public String analyzeEmotion(String text) {
        String cleaned = preprocess(text);
        String cacheKey = "emotion::" + cleaned;
        return analysisCache.get(cacheKey, key -> callLlm(buildEmotionPrompt(cleaned)));
    }

    @Override
    public String translate(String text, String targetLang) {
        String cleaned = preprocess(text);
        String cacheKey = "translate::" + targetLang + "::" + cleaned;
        return analysisCache.get(cacheKey, key -> callLlm(buildTranslatePrompt(cleaned, targetLang)));
    }

    @Override
    public Map<String, String> analyzeBatch(List<String> texts, AnalysisType type) {
        List<CompletableFuture<Map.Entry<String, String>>> futures = texts.stream()
                .map(text -> CompletableFuture.supplyAsync(() -> {
                    String result = switch (type) {
                        case SUMMARY -> summarize(text);
                        case EMOTION -> analyzeEmotion(text);
                        case TRANSLATE -> translate(text, "en");
                    };
                    return Map.entry(text, result);
                }, textPreprocessExecutor))
                .toList();

        Map<String, String> resultMap = new LinkedHashMap<>();
        for (CompletableFuture<Map.Entry<String, String>> future : futures) {
            try {
                Map.Entry<String, String> entry = future.get();
                resultMap.put(entry.getKey(), entry.getValue());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "批量分析被中断");
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof BusinessException businessException) {
                    throw businessException;
                }
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "批量分析失败: " + cause.getMessage());
            }
        }
        return resultMap;
    }

    /**
     * 带熔断保护的 LLM 调用
     */
    private String callLlm(String prompt) {
        if (!circuitBreaker.allowRequest()) {
            throw new BusinessException(ErrorCode.CIRCUIT_OPEN, "服务熔断中，请稍后重试");
        }
        try {
            String result = llmClient.chat(prompt);
            circuitBreaker.recordSuccess();
            return result;
        } catch (BusinessException e) {
            circuitBreaker.recordFailure();
            throw e;
        } catch (Exception e) {
            circuitBreaker.recordFailure();
            throw new BusinessException(ErrorCode.LLM_UNAVAILABLE, "大模型调用异常: " + e.getMessage(), e);
        }
    }

    /**
     * 文本预处理：清洗（压缩空白）→ 去重（重复句子）→ 分片（超长截断）
     */
    private String preprocess(String text) {
        if (text == null || text.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文本内容不能为空");
        }
        // 清洗：压缩连续空白字符
        String cleaned = text.trim().replaceAll("\\s+", " ");
        // 去重：去除重复句子
        String deduped = String.join("", Arrays.stream(cleaned.split("(?<=[。！？.!?])"))
                .map(String::trim)
                .filter(sentence -> !sentence.isEmpty())
                .distinct()
                .toList());
        // 分片：超长文本截断，防止超出模型输入上限
        return deduped.length() > MAX_INPUT_LENGTH ? deduped.substring(0, MAX_INPUT_LENGTH) : deduped;
    }

    private String buildSummaryPrompt(String text) {
        return "请用不超过100字概括以下文本的核心内容，直接输出概括结果：\n" + text;
    }

    private String buildEmotionPrompt(String text) {
        return "请判断以下文本的情感倾向，只回复三个词之一：正面、负面、中性。\n" + text;
    }

    private String buildTranslatePrompt(String text, String targetLang) {
        return "请将以下文本翻译为" + targetLang + "，只输出翻译结果：\n" + text;
    }
}
