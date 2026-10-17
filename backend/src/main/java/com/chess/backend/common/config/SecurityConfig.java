package com.chess.backend.common.config;

import com.chess.backend.common.api.AppResponse;
import com.chess.backend.features.auth.service.OAuthSuccessHandler;
import com.chess.backend.common.security.RefreshCookieOriginFilter;
import com.chess.backend.common.security.AuthRateLimitFilter;
import com.chess.backend.common.domain.enums.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chess.backend.features.auth.security.JwtAuthenticationFilter;
import java.util.Arrays;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    AuthRateLimitFilter authRateLimitFilter(StringRedisTemplate redis, ObjectMapper objectMapper) {
        return new AuthRateLimitFilter(redis, objectMapper);
    }

    @Bean
    FilterRegistrationBean<AuthRateLimitFilter> authRateLimitFilterRegistration(AuthRateLimitFilter filter) {
        FilterRegistrationBean<AuthRateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter,
                                            AuthRateLimitFilter authRateLimitFilter, OAuthSuccessHandler oAuthSuccessHandler,
                                            ObjectMapper objectMapper, AppProperties properties) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/oauth/onboarding").hasAuthority(UserRole.ROLE_ONBOARDING.name())
                        .requestMatchers("/api/auth/**", "/oauth2/**", "/login/oauth2/**", "/ws/**", "/actuator/health/**",
                                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        .requestMatchers("/api/internal/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/users/*/avatar").permitAll()
                        .requestMatchers("/api/users/**", "/api/friends/**", "/api/games/**").hasAuthority(UserRole.ROLE_USER.name())
                        .anyRequest().hasAuthority(UserRole.ROLE_USER.name()))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(response.getOutputStream(), AppResponse.error(401, "AUTHENTICATION_FAILED", "Authentication is required or has expired"));
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(403);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(response.getOutputStream(), AppResponse.error(403, "FORBIDDEN", "You do not have permission to perform this action"));
                        }))
                .oauth2Login(oauth -> oauth.successHandler(oAuthSuccessHandler).failureHandler(oAuthSuccessHandler))
                .addFilterBefore(new RefreshCookieOriginFilter(properties, objectMapper), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(authRateLimitFilter, AuthorizationFilter.class)
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(properties.corsOrigins().split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-ID", "X-Internal-Token", "X-CSRF-TOKEN"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
