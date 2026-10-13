package com.njupt.kms.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 注意：不使用 @WebMvcTest。本项目的 WebConfig 实现了 WebMvcConfigurer，
 * 而 @WebMvcTest 会加载 WebMvcConfigurer 却排除 @Component，
 * 导致 WebConfig 依赖的 AuthInterceptor 缺失而启动失败。
 * standalone 模式完全绕开 Spring 容器。
 */
class GlobalExceptionHandlerTest {

    @RestController
    static class ProbeController {

        @GetMapping("/probe/business")
        Result<Void> business() {
            throw new BusinessException("分类不存在");
        }

        @GetMapping("/probe/system")
        Result<Void> system() {
            throw new IllegalStateException(
                    "Cannot get connection: jdbc:mysql://localhost:3306/kms, root/secret123");
        }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void business_exception_returns_code_and_message() throws Exception {
        mockMvc.perform(get("/probe/business"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.BUSINESS_ERROR))
                .andExpect(jsonPath("$.message").value("分类不存在"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void system_exception_hides_internal_detail() throws Exception {
        mockMvc.perform(get("/probe/system"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(ResultCode.SYSTEM_ERROR))
                .andExpect(jsonPath("$.message").value("系统繁忙，请稍后重试"));
    }
}
