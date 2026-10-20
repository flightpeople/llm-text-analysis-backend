package com.example.llm.text.analysis.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 文本分析请求（摘要 / 情感）
 */
@Data
public class AnalysisRequest {

    @NotBlank(message = "文本内容不能为空")
    @Size(max = 5000, message = "文本长度不能超过5000字符")
    private String text;
}
