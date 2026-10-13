package com.njupt.kms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 知识管理系统后端入口。
 *
 * <p>Mapper 扫描依赖各接口上的 {@code @Mapper} 注解（MyBatis-Plus 自动配置会扫描
 * 主类所在包及其子包），不使用 {@code @MapperScan} 的 {@code **} 通配——
 * 该写法在 basePackages 中行为不稳定。
 */
@SpringBootApplication
@EnableAsync
public class KmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(KmsApplication.class, args);
    }
}
