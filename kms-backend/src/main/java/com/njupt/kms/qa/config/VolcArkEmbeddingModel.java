package com.njupt.kms.qa.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 火山方舟 doubao-embedding-vision 的 Spring AI 适配。
 *
 * <p>该模型只支持多模态端点 {@code /embeddings/multimodal}（实测确认）：
 * 走标准 {@code /embeddings} 会得到 400 {@code does not support this api}。
 * 因此不能启用 Spring AI 的 OpenAI 向量自动配置，必须自研。
 *
 * <p>端点也不支持批量：一次传多条文本只会返回一条向量（被当作一条多模态消息
 * 合并处理）。故 {@link #call} 逐条发请求。提速由调用方在索引层做并发——
 * 本类无状态，可安全并发调用。
 */
@Slf4j
public class VolcArkEmbeddingModel extends AbstractEmbeddingModel {

    private static final String MULTIMODAL_PATH = "/embeddings/multimodal";

    private final RestClient restClient;
    private final String model;

    public VolcArkEmbeddingModel(RestClient.Builder builder, String baseUrl, String apiKey, String model) {
        this.model = model;
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        log.info("[RAG] VolcArkEmbeddingModel baseUrl={} model={}", baseUrl, model);
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> inputs = request.getInstructions();
        List<Embedding> embeddings = new ArrayList<>(inputs.size());
        for (int i = 0; i < inputs.size(); i++) {
            embeddings.add(new Embedding(embedOne(inputs.get(i)), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return embedOne(document.getText());
    }

    @Override
    public float[] embed(String text) {
        return embedOne(text);
    }

    private float[] embedOne(String text) {
        Map<String, Object> body = Map.of(
                "model", model,
                // 空白文本不能让整条索引失败，归一化为空串
                "input", List.of(Map.of("type", "text", "text", text == null ? "" : text.strip())));
        try {
            ArkResponse response = restClient.post()
                    .uri(MULTIMODAL_PATH)
                    .body(body)
                    .retrieve()
                    .body(ArkResponse.class);
            if (response == null || response.data() == null || response.data().embedding() == null) {
                throw new IllegalStateException("火山方舟 embedding 响应格式异常");
            }
            List<Float> vector = response.data().embedding();
            float[] result = new float[vector.size()];
            for (int i = 0; i < vector.size(); i++) {
                result[i] = vector.get(i);
            }
            return result;
        } catch (RestClientResponseException e) {
            // 带上响应体，否则排查时只能看到一行状态码
            throw new IllegalStateException(
                    "火山方舟 embedding 调用失败: HTTP %d %s".formatted(
                            e.getStatusCode().value(), e.getResponseBodyAsString()), e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ArkResponse(ArkData data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ArkData(List<Float> embedding) {
    }
}
