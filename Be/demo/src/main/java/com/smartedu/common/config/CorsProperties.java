package com.smartedu.common.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "smartedu.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
