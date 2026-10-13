package com.njupt.kms.qa.config;

import com.njupt.kms.common.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Configuration
public class RagConfig {

    /**
     * 问答生成用的 ChatClient。
     *
     * <p>温度必须为 1：Moonshot 的 kimi-k2.6 只接受 1，传其他值直接 HTTP 400
     * （实测：{@code invalid temperature: only 1 is allowed for this model}）。
     * 该值固定写在 application.yaml 的 {@code spring.ai.openai.chat.options.temperature}。
     */
    @Bean
    public ChatClient qaChatClient(OpenAiChatModel model) {
        return ChatClient.builder(model)
                .defaultSystem(QaPrompts.SYSTEM)
                .build();
    }

    /**
     * 向量模型。
     *
     * <p>未配置 base-url / api-key 时返回一个「一调用就报错」的占位实现，
     * 而不是返回 null：这样 Bean 装配不会失败，未启用 RAG 时其余功能照常工作。
     */
    @Bean
    public EmbeddingModel volcArkEmbeddingModel(RagProperties properties) {
        RagProperties.Embedding config = properties.getEmbedding();
        if (config.getBaseUrl() == null || config.getBaseUrl().isBlank()
                || config.getApiKey() == null || config.getApiKey().isBlank()) {
            log.warn("[RAG] 未配置向量服务，问答功能不可用");
            return new UnavailableEmbeddingModel();
        }
        return new VolcArkEmbeddingModel(
                RestClient.builder(), config.getBaseUrl(), config.getApiKey(), config.getModel());
    }

    /**
     * 向量库。
     *
     * <p>SimpleVectorStore 默认纯内存、重启即丢，构造时显式从磁盘加载，
     * 就不再是「重启后知识库答不了」的状态。
     */
    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel, RagProperties properties) {
        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
        File file = new File(properties.getVectorStore().getPath());
        if (file.exists() && file.length() > 0) {
            try {
                store.load(file);
                log.info("[RAG] 已从 {} 加载向量索引", file.getAbsolutePath());
            } catch (Exception e) {
                log.warn("[RAG] 向量索引加载失败，将以空索引启动：{}", e.getMessage());
            }
        } else {
            log.info("[RAG] 向量索引文件不存在，以空索引启动：{}", file.getAbsolutePath());
        }
        return store;
    }

    /** 向量化线程池。单条约 350ms 且不支持批量，并发是唯一的提速手段。 */
    @Bean("embeddingExecutor")
    public Executor embeddingExecutor(RagProperties properties) {
        int concurrency = Math.max(1, properties.getEmbedding().getConcurrency());
        AtomicInteger counter = new AtomicInteger();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                concurrency, concurrency,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                runnable -> {
                    Thread thread = new Thread(runnable, "rag-embed-" + counter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                });
        log.info("[RAG] embedding 线程池并发度 = {}", concurrency);
        return executor;
    }

    /** 索引文件的父目录需要在保存前存在。供索引服务调用。 */
    public static void ensureParentDirectory(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) {
            Files.createDirectories(parent.toPath());
        }
    }

    /** 未配置密钥时的占位实现。被调用即给出可读提示，而不是 NPE。 */
    static class UnavailableEmbeddingModel implements EmbeddingModel {

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            throw unavailable();
        }

        @Override
        public float[] embed(Document document) {
            throw unavailable();
        }

        @Override
        public float[] embed(String text) {
            throw unavailable();
        }

        private BusinessException unavailable() {
            return new BusinessException(
                    "问答功能未启用：请在 application-local.yaml 中配置 app.rag.embedding.*");
        }
    }
}
