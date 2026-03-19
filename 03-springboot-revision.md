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
