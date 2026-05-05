package com.trucare.config;

import com.trucare.interceptor.JwtClaimsInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring Boot CONCEPT — WebMvcConfigurer
 *
 * Implements WebMvcConfigurer to ADD to Spring MVC's auto-configured setup
 * without replacing it entirely (which @EnableWebMvc would do).
 *
 * Registers JwtClaimsInterceptor on all paths except:
 *   /actuator/** → health probes must bypass — no X-User-Id expected
 *   /referrals/health → custom health endpoint bypass
 *
 * Note the excluded path is /referrals/health here (not /patients/health)
 * because this is the referral-service.
 */
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
                    "/referrals/health"    // referral-service health endpoint
                );
    }
}
