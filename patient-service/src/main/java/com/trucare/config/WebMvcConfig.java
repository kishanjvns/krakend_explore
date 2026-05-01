package com.trucare.config;

import com.trucare.interceptor.JwtClaimsInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring Boot CONCEPT — WebMvcConfigurer
 *
 * WebMvcConfigurer is an interface that lets you customise Spring MVC behaviour
 * without replacing the entire auto-configured setup.
 *
 * Spring Boot auto-configures Spring MVC when it sees spring-boot-starter-web.
 * Implementing WebMvcConfigurer lets you ADD to that configuration selectively
 * rather than @EnableWebMvc which REPLACES it entirely.
 *
 * WebMvcConfigurer provides many override points:
 *   addInterceptors()      → register HandlerInterceptors (used here)
 *   addCorsMappings()      → configure CORS (Step 5+)
 *   addResourceHandlers()  → serve static files
 *   configureMessageConverters() → customise JSON/XML converters
 *   addArgumentResolvers() → custom @Controller method argument types
 *
 * Spring Boot EVOLUTION:
 *   Spring 3.x : extend WebMvcConfigurerAdapter (abstract class)
 *   Spring 5.x : WebMvcConfigurerAdapter deprecated — implement WebMvcConfigurer directly
 *                (Java 8+ default methods make the adapter unnecessary)
 *   Spring Boot 3.x : same — directly implement WebMvcConfigurer
 *
 * Interview note:
 *   The shift from extending WebMvcConfigurerAdapter to implementing WebMvcConfigurer
 *   directly is a good Java 8 default-methods story. The adapter existed to avoid
 *   implementing all methods — default methods made it obsolete.
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
                .addPathPatterns("/**")          // intercept ALL paths
                .excludePathPatterns(
                    "/actuator/**",              // health probes bypass
                    "/patients/health"           // custom health endpoint bypass
                );
    }
}
