package com.wms.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.Customizer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final Environment environment;

    public SecurityConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** 独立管理端口（默认 9089）专用链：actuator 只在该端口提供，而 Prometheus 抓取不带 token，实测会被下面的链拦成 401。
     *  因此只在 localPort 命中管理端口时放行；未配置独立端口（含测试用随机端口）时匹配器恒 false，行为与原来一致。
     *  该端口在 compose 里绑 127.0.0.1，公网暴露属 PROD-005/B1 的禁止项。 */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityFilterChain managementPortChain(HttpSecurity http) throws Exception {
        int managementPort = environment.getProperty("management.server.port", int.class, -1);
        int serverPort = environment.getProperty("server.port", int.class, 8080);
        boolean separatePort = managementPort > 0 && managementPort != serverPort;
        return http
                .securityMatcher(request -> separatePort && request.getLocalPort() == managementPort)
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authz -> authz.anyRequest().permitAll())
                .build();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, TokenAuthenticationFilter filter) throws Exception {
        boolean isDev = Arrays.asList(environment.getActiveProfiles()).contains("dev")
                || environment.getActiveProfiles().length == 0; // default (no profile) = dev

        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authz -> {
                    authz.requestMatchers("/auth/login", "/auth/wx-login", "/auth/wx-bind", "/auth/wx-register", "/health", "/health/**", "/market/pay/notify", "/market/pay/refund-notify").permitAll();
                    if (isDev) {
                        authz.requestMatchers("/h2-console/**").permitAll();
                    }
                    authz.anyRequest().authenticated();
                })
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, exception) -> response.sendError(HttpServletResponse.SC_FORBIDDEN)))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
