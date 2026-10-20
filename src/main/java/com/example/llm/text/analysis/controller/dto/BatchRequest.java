package com.example.llm.text.analysis.controller.dto;

import com.example.llm.text.analysis.service.AnalysisType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量分析请求
 */
@Data
public class BatchRequest {

    @NotEmpty(message = "文本列表不能为空")
    @Size(max = 20, message = "单次批量最多20条")
    private List<String> texts;

    @NotNull(message = "分析类型不能为空")
    private AnalysisType type;
}
