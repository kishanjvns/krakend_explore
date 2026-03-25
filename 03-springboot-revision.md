# Spring Boot Revision — Interview & Concept Guide
### Topics from Step 2 Code Only

---

## 1. @SpringBootApplication

### What it is
A composed annotation that combines three annotations in one:

| Annotation | What it does |
|---|---|
| `@SpringBootConfiguration` | Marks this as the primary configuration class (specialisation of `@Configuration`) |
| `@EnableAutoConfiguration` | Tells Spring Boot to auto-configure beans based on classpath contents |
| `@ComponentScan` | Scans this package and all sub-packages for Spring-managed components |

### Used in this project
`PatientServiceApplication.java`, `ReferralServiceApplication.java`

```java
@SpringBootApplication           // replaces all three annotations below
public class PatientServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PatientServiceApplication.class, args);
    }
}
```

### How auto-configuration works
When `spring-boot-starter-web` is on the classpath, Spring Boot auto-configures:
- Embedded Tomcat server
- `DispatcherServlet` (Spring MVC front controller)
- Jackson `ObjectMapper` for JSON serialisation
- Default error handling (`/error` endpoint)

You can see every auto-configuration that fired at startup with:
```bash
java -jar patient-service.jar --debug 2>&1 | grep "Positive matches"
```

### Interview note
**Auto-configuration back-off** is the key concept here.
Every Spring Boot auto-configuration class is annotated with
`@ConditionalOnMissingBean` — it only applies if you haven't defined your own.
Our `JacksonConfig` defines an `ObjectMapper` @Bean, so Spring Boot
backs off its own auto-configured `ObjectMapper` and uses ours.
This is how "convention over configuration" works in practice.

---

## 2. @RestController

### What it is
A composed annotation: `@Controller` + `@ResponseBody`

`@ResponseBody` tells Spring MVC to serialise every method return value
directly to the HTTP response body (as JSON by default when Jackson is present).
Without it, Spring MVC treats return values as view names.

### Used in this project
`PatientController.java`, `ReferralController.java`

### Evolution
```java
// Spring 3.x — verbose, @ResponseBody on every method
@Controller
@RequestMapping("/patients")
public class PatientController {

    @GetMapping
    @ResponseBody
    public List<PatientResponse> getAll() { ... }
}

// Spring 4.x+ — @RestController combines both
@RestController
@RequestMapping("/patients")
public class PatientController {

    @GetMapping
    public List<PatientResponse> getAll() { ... }  // @ResponseBody not needed
}
```

---

## 3. @RequestMapping and HTTP Method Annotations

### What they are
`@RequestMapping` maps HTTP requests to controller methods.
Shorthand annotations exist for each HTTP verb.

### Used in this project
`@GetMapping` throughout both controllers.

```java
@GetMapping                              // GET /patients
@GetMapping("/{id}")                     // GET /patients/{id}
@GetMapping("/status/{status}")          // GET /patients/status/{status}

// Equivalents (longer form)
@RequestMapping(method = RequestMethod.GET)
@RequestMapping(value = "/{id}", method = RequestMethod.GET)
```

### Spring MVC Path Specificity Rules

When multiple mappings share the same base, Spring resolves by specificity:
**More literal characters = more specific = higher priority**

```
/referrals/open            ← literal path — HIGHEST priority
/referrals/status/{status} ← one wildcard
/referrals/patient/{id}    ← one wildcard (but "patient" literal prefix)
/referrals/{referralId}    ← single wildcard — LOWEST priority
```

**Interview note:** Spring selects the most specific matching mapping.
Literal segments always beat wildcard segments. Declaration order does NOT matter.

---

## 4. @PathVariable

### What it is
Extracts a value from a URI template variable.

### Used in this project
`getPatientById()`, `getPatientsByStatus()`, `getReferralsByPatientId()`, etc.

```java
// URL: GET /patients/P001
@GetMapping("/{id}")
public ResponseEntity<PatientResponse> getPatientById(@PathVariable String id) {
    // id = "P001"
}

// URL: GET /patients/status/admitted
@GetMapping("/status/{status}")
public ResponseEntity<List<PatientResponse>> getPatientsByStatus(
        @PathVariable String status) {
    // status = "admitted"
}
```

### When variable name differs
```java
@GetMapping("/{patient-id}")
public ResponseEntity<PatientResponse> getPatient(
        @PathVariable("patient-id") String patientId) {  // explicit name binding
}
```

---

## 5. ResponseEntity<T>

### What it is
A wrapper for the full HTTP response: status code + headers + body.
Gives you explicit control over the HTTP response.

### Used in this project
Every controller method returns `ResponseEntity<T>`.

```java
// HTTP 200 OK with body
return ResponseEntity.ok(patientService.getAllPatients());

// HTTP 200 OK — explicit form
return ResponseEntity.status(HttpStatus.OK).body(result);

// HTTP 201 Created (for POST — used in Step 3+)
return ResponseEntity.status(HttpStatus.CREATED).body(created);

// HTTP 404 Not Found with no body
return ResponseEntity.notFound().build();

// HTTP 204 No Content (for DELETE)
return ResponseEntity.noContent().build();
```

### Why use ResponseEntity vs just returning the object?
Returning the object directly sends HTTP 200 always.
`ResponseEntity` lets you control status codes explicitly — critical for:
- REST semantics (201 for create, 204 for delete)
- KrakenD circuit breaker logic (reads status codes from backends)

---

## 6. @RestControllerAdvice — Global Exception Handling

### What it is
A global interceptor that handles exceptions thrown by ANY `@RestController`.
`@RestControllerAdvice` = `@ControllerAdvice` + `@ResponseBody`

### Used in this project
`GlobalExceptionHandler.java` in both services.

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Handles PatientNotFoundException from any controller method
    @ExceptionHandler(PatientNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handlePatientNotFound(
            PatientNotFoundException ex,
            HttpServletRequest request) {
        return ErrorResponse.of(404, "NOT_FOUND",
                ex.getMessage(), request.getRequestURI());
    }
}
```

### How Spring AOP makes this work
`@ControllerAdvice` works through Spring AOP.
Spring wraps every controller in a proxy. When an exception escapes a
controller method, the proxy intercepts it and routes it to the matching
`@ExceptionHandler` method in the `@RestControllerAdvice` class.

### Evolution
```
Spring 3.x : @ExceptionHandler worked per-controller only
Spring 4.x : @ControllerAdvice introduced — cross-controller handling
Spring 4.x : @RestControllerAdvice added for REST APIs
```

### Interview note
The separation of exception handling from controller logic is a
**cross-cutting concern** — exactly the use case Spring AOP was designed for.
Without `@RestControllerAdvice`, every controller method needs a try-catch,
duplicating error-handling logic across dozens of methods.

---

## 7. @Configuration and @Bean

### What they are
`@Configuration` marks a class as a source of bean definitions.
`@Bean` on a method tells Spring: register the return value as a managed bean.

### Used in this project
`JacksonConfig.java` in both services.

```java
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {        // return value → registered as a bean
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
```

### @Bean vs @Component
```
@Component  → Spring finds and registers the CLASS itself via component scan
@Bean       → Spring registers the RETURN VALUE of a method in a @Configuration class
```

Use `@Bean` when you need to create and configure a third-party class
(like `ObjectMapper`) that you cannot annotate with `@Component` yourself.

---

## 8. @Service — Stereotype Annotations

### The stereotype hierarchy
```
@Component              ← base stereotype
├── @Service            ← business/domain layer
├── @Repository         ← data access layer (also enables exception translation)
└── @Controller         ← presentation layer
    └── @RestController ← REST presentation layer
```

### Used in this project
`PatientService.java`, `ReferralService.java`

### Interview note
All stereotypes are functionally equivalent to `@Component` for component scanning.
The difference is **semantic intent and tooling support**:
- `@Repository` enables persistence-layer exception translation
- `@Service` signals business logic — used by AOP for transactional advice
- Using the right stereotype makes the code self-documenting

---

## 9. Constructor Injection vs Field Injection

### Used in this project
Both controllers use constructor injection.

```java
// ❌ Field injection — avoid this in production code
@RestController
public class PatientController {

    @Autowired
    private PatientService patientService;   // cannot be final, hard to test
}

// ✅ Constructor injection — preferred
@RestController
public class PatientController {

    private final PatientService patientService;   // can be final

    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }
}
```

### Why constructor injection wins
| Concern | Field Injection | Constructor Injection |
|---|---|---|
| Immutability | ❌ field cannot be final | ✅ field can be final |
| Testability | ❌ needs Spring or reflection | ✅ just pass a mock |
| Fail-fast | ❌ fails at first call | ✅ fails at startup |
| Explicit deps | ❌ hidden inside class | ✅ visible in signature |

In Spring Boot 3.x, if a class has exactly ONE constructor,
`@Autowired` is not required — Spring auto-injects.

---

## 10. Spring Boot Actuator

### What it is
Provides production-ready operational endpoints out of the box.

### Used in this project
`pom.xml` dependency + `application.properties` config.

```properties
management.endpoints.web.exposure.include=health,info,metrics
management.endpoint.health.show-details=always
```

### Key endpoints
| Endpoint | URL | Used by |
|---|---|---|
| Health | `/actuator/health` | Docker healthcheck, ALB target group |
| Info | `/actuator/info` | CI/CD pipelines, dashboards |
| Metrics | `/actuator/metrics` | Prometheus, CloudWatch |

### In your TrueCare EKS setup
ALB target group health checks call `/actuator/health`.
If the response is not HTTP 200, ALB stops routing traffic to that pod.
This is the automatic health-based traffic shifting in your Route 53 → ALB → EKS flow.

---

## 11. application.properties — Key Properties Explained

```properties
# Tells Spring which port to start on
server.port=8081

# Used by Actuator /actuator/info and service discovery
spring.application.name=patient-service

# Without this, Instant serialises as [1711968600, 0] instead of "2024-04-01T10:30:00Z"
spring.jackson.serialization.write-dates-as-timestamps=false

# Defensive — don't fail if JSON from another service has extra fields
spring.jackson.deserialization.fail-on-unknown-properties=false

# Excludes Spring Security auto-config — auth is KrakenD's job
spring.autoconfigure.exclude=\
  org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
```

---

## Common Interview Questions

**Q: What is the difference between @Component, @Service, @Repository, @Controller?**
A: All are specialisations of @Component and trigger component scanning. They differ semantically: @Repository enables exception translation, @Service marks business logic, @Controller/@RestController marks the presentation layer.

**Q: What is auto-configuration in Spring Boot?**
A: Spring Boot reads META-INF/spring auto-configuration files from starter JARs and conditionally creates beans based on classpath contents and user-defined beans. The `@ConditionalOnMissingBean` annotation means user-defined beans always override defaults.

**Q: Why is constructor injection preferred over field injection?**
A: Constructor injection supports final fields (immutability), makes dependencies explicit, fails at startup if a dependency is missing, and makes unit testing straightforward without a Spring context.

**Q: What is @RestControllerAdvice?**
A: A global exception handler for all @RestController classes. It combines @ControllerAdvice (global interceptor) and @ResponseBody (JSON serialisation). It works via Spring AOP proxies that wrap controller beans.

**Q: What does ResponseEntity give you that a plain return type doesn't?**
A: Explicit control over HTTP status codes, response headers, and the body. Critical for REST semantics (201 for create, 204 for delete) and for KrakenD to correctly interpret backend responses for circuit-breaking and aggregation fallback.



# Spring Boot & Security Revision — Interview & Concept Guide
### Step 4: JWT Auth (Spring Boot security concepts)

---

## 1. WebMvcConfigurer — extending without replacing

### What it is
An interface that lets you add to Spring MVC's auto-configured setup selectively.

### Used in this project
`WebMvcConfig.java` registers `JwtClaimsInterceptor`.

```java
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtClaimsInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/actuator/**", "/patients/health");
    }
}
```

🔎 **What it means word by word:**

`implements WebMvcConfigurer` — adds to Spring MVC, does NOT replace it.
  Using `@EnableWebMvc` would replace auto-configuration entirely — wrong choice here.

`addPathPatterns("/**")` — intercept every path under the application root.

`excludePathPatterns("/actuator/**")` — Actuator health checks must not go through
  the interceptor. If they do and `X-User-Id` is missing, `UserContext.anonymous()`
  is set — harmless but unnecessary overhead on every health check cycle.

### Evolution — why no WebMvcConfigurerAdapter
```
Spring 3.x : WebMvcConfigurerAdapter (abstract class with empty implementations)
              Needed because Java 7 interfaces require ALL methods to be implemented

Spring 5.x : WebMvcConfigurerAdapter deprecated
              Java 8 interface default methods make adapters obsolete

Spring Boot 3.x : implement WebMvcConfigurer directly
                  All unused methods have default empty implementations
```

---

## 2. Spring Security vs KrakenD auth — why we don't use Spring Security here

### What Spring Security would look like
```java
// If we used Spring Security (NOT what we do)
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/patients").permitAll()
                .requestMatchers("/patients/{id}").hasRole("DOCTOR")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
            .build();
    }
}
```

### Why we explicitly disable Spring Security
```properties
# application.properties
spring.autoconfigure.exclude=\
  org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
```

🔎 **The reasoning:**

Auth is KrakenD's responsibility. If our Spring Boot service also validates JWT:
- Double validation → double latency on every request
- Two configs to maintain and keep in sync
- Spring Security adds a 9-filter chain to every request — unnecessary overhead
- Services are not internet-facing — only KrakenD can reach them

🔎 **The trust model:**
```
Internet → KrakenD (validates JWT, strips token, adds claim headers)
                  ↓
        Kubernetes internal network
                  ↓
        patient-service (trusts KrakenD-forwarded headers)
```

Services trust KrakenD-forwarded headers because:
1. They are only reachable via `ClusterIP` — not from the internet
2. KrakenD is the only thing calling them
3. No external party can forge `X-User-Id` because they can't reach the service directly

### When you WOULD use Spring Security in microservices
- Service-to-service mTLS (Istio alternative — which you removed)
- If services need to be directly accessible without a gateway
- If different services have different auth requirements that can't be expressed in KrakenD

---

## 3. @Component on HandlerInterceptor

### What it means
`@Component` on `JwtClaimsInterceptor` makes it a Spring-managed bean.
`WebMvcConfig` receives it via constructor injection and registers it.

```java
@Component              // Spring manages lifecycle
public class JwtClaimsInterceptor implements HandlerInterceptor { ... }

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtClaimsInterceptor jwtClaimsInterceptor; // injected

    public WebMvcConfig(JwtClaimsInterceptor jwtClaimsInterceptor) {
        this.jwtClaimsInterceptor = jwtClaimsInterceptor;  // constructor injection
    }
}
```

### Why @Component and not @Bean inside WebMvcConfig?
`@Component` on the interceptor class means it can be injected anywhere else
in the application (future use — e.g. a service that needs UserContext).
If it were a `@Bean` only inside `WebMvcConfig`, it would be more isolated.
Either works — `@Component` is more reusable.

---

## 4. Interceptor vs Filter vs AOP — know the difference

| | Filter (javax/jakarta) | HandlerInterceptor | Spring AOP |
|---|---|---|---|
| Level | Servlet container | Spring MVC | Spring bean |
| Sees | Raw HTTP request | Mapped handler | Method invocation |
| Runs for | All requests including static | Only @Controller methods | Only Spring beans |
| Access to handler | No | Yes | Yes |
| Use for | Security filter chains, encoding | User context, logging | Transactions, caching |

### In TrueCare Step 4
`HandlerInterceptor` is the right choice because:
- We only need context for `@Controller` methods
- We need access to headers from `HttpServletRequest`
- We don't need Spring AOP method-level granularity
- Spring Security's filter chain is overkill since KrakenD handles auth

---

## 5. Logger in Spring Boot — SLF4J + Logback

### What it is
SLF4J (Simple Logging Facade for Java) is the API.
Logback is the default implementation in Spring Boot.

```java
// Declaration
private static final Logger log = LoggerFactory.getLogger(PatientController.class);

// Usage
log.info("GET /patients/{} by userId={}", id, userId);
log.debug("Full detail returned to role={}", role);
log.warn("Unexpected anonymous access to protected endpoint");
log.error("Failed to process request", exception);
```

### Spring Boot auto-configuration of logging
Spring Boot auto-configures Logback via `spring-boot-starter-logging`
(included transitively in `spring-boot-starter-web`).
Default output: coloured console with timestamp, level, thread, logger, message.

### Configure in application.properties
```properties
# Set log level for all com.trucare classes
logging.level.com.trucare=DEBUG

# Set log level for Spring MVC specifically
logging.level.org.springframework.web=INFO

# Log to file as well as console
logging.file.name=logs/patient-service.log

# Pattern (rarely needed — defaults are good)
logging.pattern.console=%d{HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n
```

### Interview note
> "Spring Boot's logging auto-configuration uses SLF4J as the API with Logback as
> the default backend. This is a classic facade pattern — code depends on SLF4J,
> and the underlying implementation (Logback, Log4j2, JUL) can be swapped by
> changing dependencies without touching application code.
> The `private static final Logger` field is the standard idiom — static to avoid
> creating a new Logger per instance, final to prevent reassignment."

---

## 6. Audit logging pattern — who did what, when

### Why audit logging matters in healthcare
HIPAA and clinical governance require knowing exactly who accessed patient records.
`UserContext` in every controller enables this:

```java
log.info("GET /patients/{} accessed by userId={} role={} at={}",
    id, ctx.userId(), ctx.role(), Instant.now());
```

### Production audit log pattern (Structured JSON logging)
In production, replace plain text logs with structured JSON for easy querying
in CloudWatch Logs or Elasticsearch:

```json
{
  "timestamp": "2024-04-01T10:30:00Z",
  "level": "INFO",
  "service": "patient-service",
  "event": "PATIENT_ACCESSED",
  "patientId": "P001",
  "userId": "U001",
  "userRole": "DOCTOR",
  "userEmail": "dr.mehta@trucare.com",
  "endpoint": "GET /patients/P001",
  "responseStatus": 200,
  "durationMs": 45
}
```

This is achievable by adding `logstash-logback-encoder` to `pom.xml` —
a Step 7 (EKS deployment) topic.

---

## Common Interview Questions from Step 4

**Q: What is the difference between Spring Security and what you implemented?**

A: Spring Security provides a full authentication and authorisation framework —
filter chains, user details services, JWT parsing, role-based access. In our
architecture, KrakenD handles all of this at the gateway level. Spring Boot services
only read the pre-validated identity that KrakenD forwards as plain HTTP headers.
This eliminates redundant JWT validation in every service and removes the overhead
of Spring Security's 9-filter chain from each request.

**Q: How do you pass user identity between microservices without re-validating JWT?**

A: We use claim propagation via KrakenD headers. The gateway validates the JWT once
at the edge, extracts claims (`sub`, `email`, custom role), and injects them as
`X-User-*` HTTP headers before forwarding. Backend services read these trusted headers
via a `HandlerInterceptor` that populates a `UserContext` record in a `ThreadLocal`.
Services trust these headers because they are only reachable from inside the
Kubernetes cluster — external callers cannot forge them.

**Q: How do you ensure ThreadLocal doesn't cause cross-request contamination?**

A: The `JwtClaimsInterceptor` calls `UserContextHolder.clear()` in `afterCompletion()`,
which is guaranteed to run even if an exception occurs. Using `.remove()` rather than
`.set(null)` fully deallocates the thread-local slot, preventing both data contamination
and memory leaks in Tomcat's thread pool.
