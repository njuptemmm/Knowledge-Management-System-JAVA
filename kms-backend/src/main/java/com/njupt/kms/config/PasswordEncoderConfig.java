package com.njupt.kms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 只提供密码编码器。
 *
 * <p>刻意不引入 spring-boot-starter-security：那会启用整条 Web 安全过滤器链，
 * 把所有接口默认拦死，与项目自建的 token 认证冲突。
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
