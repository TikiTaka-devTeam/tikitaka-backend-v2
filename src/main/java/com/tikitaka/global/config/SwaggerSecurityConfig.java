package com.tikitaka.global.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties(SwaggerAuthProperties.class)
public class SwaggerSecurityConfig {
    private static final Logger log = LoggerFactory.getLogger(SwaggerSecurityConfig.class);

    @Bean
    @Order(1)
    @Profile("prod")
    SecurityFilterChain productionSwaggerSecurityFilterChain(
            HttpSecurity http, SwaggerAuthProperties properties, PasswordEncoder passwordEncoder) throws Exception {
        InMemoryUserDetailsManager users = new InMemoryUserDetailsManager();
        if (StringUtils.hasText(properties.username()) && StringUtils.hasText(properties.password())) {
            users.createUser(User.withUsername(properties.username())
                    .password(passwordEncoder.encode(properties.password()))
                    .roles("SWAGGER").build());
        } else {
            log.warn("Swagger credentials are not configured; production Swagger access remains locked.");
        }
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(passwordEncoder);
        BasicAuthenticationEntryPoint entryPoint = new BasicAuthenticationEntryPoint();
        entryPoint.setRealmName("TikiTaka Swagger");
        entryPoint.afterPropertiesSet();

        return swaggerHttp(http)
                .authenticationManager(new ProviderManager(provider))
                .httpBasic(basic -> basic.authenticationEntryPoint(entryPoint))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(entryPoint))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().hasRole("SWAGGER"))
                .build();
    }

    @Bean
    @Order(1)
    @Profile("!prod")
    SecurityFilterChain developmentSwaggerSecurityFilterChain(HttpSecurity http) throws Exception {
        return swaggerHttp(http)
                .httpBasic(basic -> basic.disable())
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .build();
    }

    private HttpSecurity swaggerHttp(HttpSecurity http) throws Exception {
        return http.securityMatcher("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/v3/api-docs.yaml")
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .requestCache(cache -> cache.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    }
}
