package com.njupt.kms.qa.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG 配置。
 *
 * <p>默认 {@code enabled=false}：未配置密钥时问答功能整体关闭，
 * 其余功能完全不受影响。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.rag")
public class RagProperties {

    private boolean enabled = false;

    private Retrieve retrieve = new Retrieve();
    private Chunk chunk = new Chunk();
    private VectorStoreConfig vectorStore = new VectorStoreConfig();
    private Embedding embedding = new Embedding();

    @Data
    public static class Retrieve {
        /** 召回的块数上限 */
        private int topK = 5;
        /** 相似度下限，低于此值不喂给模型 */
        private double minScore = 0.3;
    }

    @Data
    public static class Chunk {
        /** 单块最大字符数 */
        private int maxLength = 500;
        /** 相邻块的重叠字符数，避免语义在切点处断裂 */
        private int overlap = 80;
    }

    @Data
    public static class VectorStoreConfig {
        /** 索引文件路径，相对工作目录 */
        private String path = "data/vector-index.json";
    }

    @Data
    public static class Embedding {
        private String baseUrl;
        private String apiKey;
        private String model = "doubao-embedding-vision-251215";
        /**
         * 并发度。实测火山方舟单条向量化约 350ms 且端点不支持批量，
         * 串行索引 500 块需 3 分钟，只能靠并发提速。
         */
        private int concurrency = 8;
    }
}
