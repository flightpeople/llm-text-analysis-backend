package com.example.llm.text.analysis.service;

import java.util.List;
import java.util.Map;

/**
 * 文本分析服务
 */
public interface TextAnalysisService {

    /**
     * 文本摘要
     */
    String summarize(String text);

    /**
     * 情感分析
     */
    String analyzeEmotion(String text);

    /**
     * 文本翻译
     */
    String translate(String text, String targetLang);

    /**
     * 批量分析（线程池并发执行）
     */
    Map<String, String> analyzeBatch(List<String> texts, AnalysisType type);
}
