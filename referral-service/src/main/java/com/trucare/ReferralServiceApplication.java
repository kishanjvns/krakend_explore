package com.trucare;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot CONCEPT — @SpringBootApplication
 *   @SpringBootConfiguration  : primary config class
 *   @EnableAutoConfiguration  : auto-configures Tomcat, Jackson, MVC etc.
 *   @ComponentScan            : scans com.trucare.referral and sub-packages
 *
 * Each microservice is a completely independent Spring Boot application
 * with its own embedded Tomcat, its own application context, and its own
 * port. This is the "independently deployable" principle of microservices.
 */
@SpringBootApplication
public class ReferralServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReferralServiceApplication.class, args);
    }
}
