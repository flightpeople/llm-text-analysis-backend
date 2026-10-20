package com.example.llm.text.analysis.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 翻译请求
 */
@Data
public class TranslateRequest {

    @NotBlank(message = "文本内容不能为空")
    @Size(max = 5000, message = "文本长度不能超过5000字符")
    private String text;

    @NotBlank(message = "目标语言不能为空")
    @Size(max = 20, message = "目标语言长度不合法")
    private String targetLang;
}
