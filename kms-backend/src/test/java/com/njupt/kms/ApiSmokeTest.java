package com.njupt.kms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 端到端冒烟测试，针对 kms_test 库（见 application-test.yaml）。
 *
 * <p>前置：kms_test 库已执行 schema.sql 与 seed.sql。
 * 依赖 MySQL 与 Redis 均在运行。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String adminToken;

    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(body);
        return node.path("data").path("token").asText();
    }

    @Test
    @Order(1)
    void public_endpoints_work_without_login() throws Exception {
        mockMvc.perform(get("/api/categories/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].name").value("编程语言"));

        mockMvc.perform(get("/api/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(get("/api/knowledge").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    @Order(2)
    void write_endpoints_reject_anonymous() throws Exception {
        mockMvc.perform(post("/api/knowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"匿名创建\"}"))
                .andExpect(jsonPath("$.code").value(1002))
                .andExpect(jsonPath("$.message").value("请先登录"));

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"test\"}"))
                .andExpect(jsonPath("$.code").value(1002));
    }

    @Test
    @Order(3)
    void login_succeeds_and_me_returns_profile() throws Exception {
        adminToken = login("admin", "admin123");
        assertThat(adminToken).isNotBlank();

        mockMvc.perform(get("/api/auth/me").header("Authorization", adminToken))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    @Order(4)
    void login_rejects_wrong_password() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(jsonPath("$.code").value(2000))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    @Order(5)
    void validation_error_names_the_field() throws Exception {
        mockMvc.perform(post("/api/knowledge")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\"}"))
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.message").value("标题不能为空"));
    }

    @Test
    @Order(6)
    void malformed_body_is_reported_as_a_param_error() throws Exception {
        mockMvc.perform(post("/api/knowledge")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.message").value(
                        Matchers.containsString("请求体格式错误")));
    }

    @Test
    @Order(7)
    void fulltext_search_finds_chinese_content() throws Exception {
        mockMvc.perform(get("/api/search").param("keyword", "索引"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                // seed 中「MySQL 索引原理」必须被搜到，证明 ngram 索引生效
                .andExpect(jsonPath("$.data.records[0].title").value(
                        Matchers.containsString("索引")));
    }

    @Test
    @Order(8)
    void search_finds_latin_terms_via_like_fallback() throws Exception {
        // ngram 解析器不索引拉丁字符，此路径由 LIKE 兜底。
        // 若此测试失败，说明英文技术名词搜不到——对本系统是致命缺陷。
        mockMvc.perform(get("/api/search").param("keyword", "Java"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records").isNotEmpty())
                .andExpect(jsonPath("$.data.records[0].title").value(
                        Matchers.containsString("Java")));
    }

    @Test
    @Order(9)
    void category_filter_includes_descendants() throws Exception {
        // 分类 1「编程语言」下面有 Java / Python / Spring / JVM，
        // 按其筛选必须能拿到子孙分类的条目
        mockMvc.perform(get("/api/knowledge").param("categoryId", "1").param("size", "100"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(Matchers.greaterThan(0)));

        mockMvc.perform(get("/api/knowledge").param("categoryId", "5").param("size", "100"))
                // 分类 5「JVM」是叶子，只有 1 条已发布
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    @Order(10)
    void anonymous_listing_hides_unpublished_items() throws Exception {
        mockMvc.perform(get("/api/knowledge").param("size", "100"))
                .andExpect(jsonPath("$.data.records[*].status",
                        Matchers.everyItem(Matchers.is("PUBLISHED"))));

        // 作者本人可以看到自己的草稿
        mockMvc.perform(get("/api/knowledge")
                        .header("Authorization", adminToken)
                        .param("mine", "true").param("size", "100"))
                .andExpect(jsonPath("$.data.total").value(Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.data.records[*].status",
                        Matchers.hasItem("DRAFT")));
    }

    @Test
    @Order(11)
    void unknown_item_returns_business_error_not_stacktrace() throws Exception {
        mockMvc.perform(get("/api/knowledge/999999"))
                .andExpect(jsonPath("$.code").value(2000))
                .andExpect(jsonPath("$.message").value("知识条目不存在"));
    }

    @Test
    @Order(12)
    void forbidden_operation_returns_clear_message() throws Exception {
        String other = login("user", "user123");
        // seed 中 id=1 的条目作者是 admin，user 无权归档
        mockMvc.perform(post("/api/knowledge/2/archive").header("Authorization", other))
                .andExpect(jsonPath("$.code").value(2000))
                .andExpect(jsonPath("$.message").value("无权操作他人的知识条目"));
    }

    @Test
    @Order(13)
    void category_delete_guard_reports_the_blocker() throws Exception {
        // 分类 1 有子分类，删除必须被拒并说明原因
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/categories/1").header("Authorization", adminToken))
                .andExpect(jsonPath("$.code").value(2000))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("子分类")));
    }
}
