package com.mediq.config;

import com.mediq.interceptor.JwtClaimsInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtClaimsInterceptor jwtClaimsInterceptor;

    public WebMvcConfig(JwtClaimsInterceptor jwtClaimsInterceptor) {
        this.jwtClaimsInterceptor = jwtClaimsInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtClaimsInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                    "/actuator/**",
                    "/users/health",
                    "/users/patients/register",
                    "/users/doctors/register"
                );
    }
}
