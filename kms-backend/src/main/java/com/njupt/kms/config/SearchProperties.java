package com.njupt.kms.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 检索相关度权重。标题是内容的最强语义浓缩，摘要次之，正文兜底。
 *
 * <p>配置化而非硬编码，便于在报告中说明参数调优过程。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.search")
public class SearchProperties {

    private Weight weight = new Weight();

    @Data
    public static class Weight {
        private double title = 3.0;
        private double summary = 2.0;
        private double content = 1.0;
    }
}
