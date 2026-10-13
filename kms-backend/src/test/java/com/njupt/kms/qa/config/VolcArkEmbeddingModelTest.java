package com.njupt.kms.qa.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingOptionsBuilder;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class VolcArkEmbeddingModelTest {

    private static final String BASE = "https://ark.example.com/api/v3";

    private MockRestServiceServer server;
    private VolcArkEmbeddingModel model;

    private void build() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        model = new VolcArkEmbeddingModel(builder, BASE, "ark-test-key", "doubao-embedding-vision-251215");
    }

    private EmbeddingRequest request(String... inputs) {
        return new EmbeddingRequest(List.of(inputs), EmbeddingOptionsBuilder.builder().build());
    }

    @Test
    void calls_multimodal_endpoint_not_standard_embeddings() {
        // 走标准 /embeddings 会 400（实测），必须走 /embeddings/multimodal
        build();
        server.expect(requestTo(BASE + "/embeddings/multimodal"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer ark-test-key"))
                .andExpect(jsonPath("$.model").value("doubao-embedding-vision-251215"))
                .andExpect(jsonPath("$.input[0].type").value("text"))
                .andExpect(jsonPath("$.input[0].text").value("测试文本"))
                .andRespond(withSuccess("{\"data\":{\"embedding\":[0.1,0.2,0.3]}}",
                        MediaType.APPLICATION_JSON));

        float[] vector = model.embed("测试文本");

        assertThat(vector).containsExactly(0.1f, 0.2f, 0.3f);
        server.verify();
    }

    @Test
    void one_request_yields_one_vector_even_for_multiple_inputs() {
        // 端点不支持批量：EmbeddingModel.call 必须逐条发请求。
        // 若这里只发出一次请求，说明走了批量假设，真实环境会得到错的维度对应关系。
        build();
        server.expect(requestTo(BASE + "/embeddings/multimodal"))
                .andRespond(withSuccess("{\"data\":{\"embedding\":[1.0]}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/embeddings/multimodal"))
                .andRespond(withSuccess("{\"data\":{\"embedding\":[2.0]}}", MediaType.APPLICATION_JSON));

        var response = model.call(request("第一段", "第二段"));

        assertThat(response.getResults()).hasSize(2);
        assertThat(response.getResults().get(0).getOutput()).containsExactly(1.0f);
        assertThat(response.getResults().get(1).getOutput()).containsExactly(2.0f);
        server.verify();
    }

    @Test
    void api_error_message_includes_status_and_body() {
        // 排查时要能看到服务端到底说了什么，只报状态码等于没说
        build();
        server.expect(requestTo(BASE + "/embeddings/multimodal"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("{\"error\":{\"message\":\"InvalidParameter\"}}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> model.embed("x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("400")
                .hasMessageContaining("InvalidParameter");
    }

    @Test
    void blank_text_is_normalized_to_empty_string() {
        // 空白文本不能让整条索引失败
        build();
        server.expect(requestTo(BASE + "/embeddings/multimodal"))
                .andExpect(jsonPath("$.input[0].text").value(""))
                .andRespond(withSuccess("{\"data\":{\"embedding\":[0.0]}}", MediaType.APPLICATION_JSON));

        assertThat(model.embed("   ")).hasSize(1);
    }

    @Test
    void null_text_does_not_throw() {
        build();
        server.expect(requestTo(BASE + "/embeddings/multimodal"))
                .andRespond(withSuccess("{\"data\":{\"embedding\":[0.0]}}", MediaType.APPLICATION_JSON));

        assertThat(model.embed((String) null)).hasSize(1);
    }

    @Test
    void malformed_response_is_reported_as_format_error() {
        build();
        server.expect(requestTo(BASE + "/embeddings/multimodal"))
                .andRespond(withSuccess("{\"unexpected\":true}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> model.embed("x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("响应格式异常");
    }
}
