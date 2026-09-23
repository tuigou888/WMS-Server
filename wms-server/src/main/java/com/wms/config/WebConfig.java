package com.wms.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override public void addCorsMappings(CorsRegistry registry) {
        // Vite 会在默认端口被占用时自动递增（例如 5174），代理请求仍会转发浏览器 Origin。
        // 仅允许 localhost/127.0.0.1 的任意开发端口，生产同源部署不依赖该 CORS 规则。
        registry.addMapping("/**").allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*").allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS").allowedHeaders("*");
    }
}
