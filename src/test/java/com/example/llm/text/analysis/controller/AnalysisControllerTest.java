package com.example.llm.text.analysis.controller;

import com.example.llm.text.analysis.service.AnalysisType;
import com.example.llm.text.analysis.service.TextAnalysisService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 控制器层单元测试：MockMvc 验证接口行为与统一响应格式
 */
@WebMvcTest(AnalysisController.class)
class AnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TextAnalysisService textAnalysisService;

    @Test
    @DisplayName("摘要接口：正常请求返回统一格式")
    void summarize_正常() throws Exception {
        when(textAnalysisService.summarize(anyString())).thenReturn("摘要内容");

        mockMvc.perform(post("/api/analysis/summarize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Spring Boot 是快速开发框架\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data").value("摘要内容"));
    }

    @Test
    @DisplayName("摘要接口：空文本触发参数校验，返回400错误码")
    void summarize_参数为空() throws Exception {
        mockMvc.perform(post("/api/analysis/summarize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("翻译接口：正常请求")
    void translate_正常() throws Exception {
        when(textAnalysisService.translate(anyString(), anyString())).thenReturn("Hello World");

        mockMvc.perform(post("/api/analysis/translate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"你好世界\",\"targetLang\":\"英语\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("Hello World"));
    }

    @Test
    @DisplayName("批量接口：返回原文到结果的映射")
    void batch_正常() throws Exception {
        when(textAnalysisService.analyzeBatch(anyList(), any()))
                .thenReturn(Map.of("文本一", "结果一", "文本二", "结果二"));

        mockMvc.perform(post("/api/analysis/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texts\":[\"文本一\",\"文本二\"],\"type\":\"SUMMARY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data['文本一']").value("结果一"));
    }
}
