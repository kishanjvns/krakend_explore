package com.trucare;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot CONCEPT — @SpringBootApplication
 *
 * This single annotation is a shortcut for three annotations:
 *
 *  @SpringBootConfiguration
 *    Specialisation of @Configuration. Marks this class as the
 *    primary configuration source for the Spring context.
 *
 *  @EnableAutoConfiguration
 *    Tells Spring Boot to auto-configure beans based on the classpath.
 *    e.g. spring-boot-starter-web on classpath →
 *         auto-configures embedded Tomcat + DispatcherServlet + Jackson.
 *    This is the "magic" of Spring Boot — it reads META-INF/spring/
 *    auto-configuration files from each starter JAR.
 *
 *  @ComponentScan
 *    Scans this package (com.trucare.patient) and all sub-packages
 *    for Spring-managed components:
 *      @Service, @RestController, @Repository, @Configuration, @Component
 *
 * Interview note:
 *   Auto-configuration is the most important Spring Boot concept.
 *   It follows "convention over configuration" — sensible defaults for free,
 *   override only what you need (e.g. our JacksonConfig bean).
 *   You can see all auto-configurations with:
 *     java -jar patient-service.jar --debug 2>&1 | grep "Positive matches"
 */
@SpringBootApplication
public class PatientServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PatientServiceApplication.class, args);
    }
}
