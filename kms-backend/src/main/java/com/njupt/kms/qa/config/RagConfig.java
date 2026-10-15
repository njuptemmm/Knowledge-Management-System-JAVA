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
     * <p>模型与参数写在 application.yaml 的 {@code spring.ai.openai.chat.options.*}：
     * 用 OpenAI 兼容协议指向 DeepSeek，与向量用的火山方舟完全独立，两者互不影响。
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
    public SimpleVectorStore vectorStore(EmbeddingModel embeddingModel, RagProperties properties) {
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
        return newPool("rag-embed-", concurrency);
    }

    /**
     * 索引任务的线程池，与向量化池**必须分开**。
     *
     * <p>索引任务会把分块提交给 embeddingExecutor 然后阻塞等待结果。
     * 若两者共用一个池，8 个索引线程同时阻塞在 join() 上、而它们等待的分块任务
     * 又排在这 8 个被占满的线程之后，就会互相等待致死。
     *
     * <p>池小是因为索引是后台任务，不该和 Web 请求抢资源；
     * 真正的并发度由 embeddingExecutor 提供。
     */
    @Bean("ragIndexExecutor")
    public Executor ragIndexExecutor() {
        return newPool("rag-index-", 2);
    }

    private ThreadPoolExecutor newPool(String namePrefix, int size) {
        AtomicInteger counter = new AtomicInteger();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                size, size,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                runnable -> {
                    Thread thread = new Thread(runnable, namePrefix + counter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                });
        log.info("[RAG] 线程池 {} 大小 = {}", namePrefix, size);
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
