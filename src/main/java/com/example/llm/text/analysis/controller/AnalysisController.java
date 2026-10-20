package com.example.llm.text.analysis.controller;

import com.example.llm.text.analysis.common.Result;
import com.example.llm.text.analysis.controller.dto.AnalysisRequest;
import com.example.llm.text.analysis.controller.dto.BatchRequest;
import com.example.llm.text.analysis.controller.dto.TranslateRequest;
import com.example.llm.text.analysis.service.TextAnalysisService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 文本分析接口
 */
@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private final TextAnalysisService textAnalysisService;

    public AnalysisController(TextAnalysisService textAnalysisService) {
        this.textAnalysisService = textAnalysisService;
    }

    @PostMapping("/summarize")
    public Result<String> summarize(@RequestBody @Valid AnalysisRequest request) {
        return Result.ok(textAnalysisService.summarize(request.getText()));
    }

    @PostMapping("/emotion")
    public Result<String> analyzeEmotion(@RequestBody @Valid AnalysisRequest request) {
        return Result.ok(textAnalysisService.analyzeEmotion(request.getText()));
    }

    @PostMapping("/translate")
    public Result<String> translate(@RequestBody @Valid TranslateRequest request) {
        return Result.ok(textAnalysisService.translate(request.getText(), request.getTargetLang()));
    }

    @PostMapping("/batch")
    public Result<Map<String, String>> batch(@RequestBody @Valid BatchRequest request) {
        return Result.ok(textAnalysisService.analyzeBatch(request.getTexts(), request.getType()));
    }
}
