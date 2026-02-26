package com.vivire.locapet.app.global.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .authorizeHttpRequests { auth ->
                auth
                    // Auth API - 소셜 로그인, 토큰 재발급 공개
                    .requestMatchers("/api/v1/auth/social/**").permitAll()
                    .requestMatchers("/api/v1/auth/reissue").permitAll()
                    // Onboarding API - 공개
                    .requestMatchers("/api/v1/onboarding/**").permitAll()
                    // Meta API - 공개
                    .requestMatchers("/api/v1/meta/**").permitAll()
                    // Swagger UI
                    .requestMatchers(
                        "/api-app.html",
                        "/api-docs/**",
                        "/swagger-ui/**",
                        "/v3/api-docs/**"
                    ).permitAll()
                    // Actuator
                    .requestMatchers("/actuator/**").permitAll()
                    // 그 외 인증 필요 (session, logout, withdraw 등)
                    .anyRequest().authenticated()
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }
}
