# krakend_explore
## KrakenD: API Contract and Data Manipulation

-----

### API Contract and Backends

The **endpoints** section in the KrakenD configuration defines the **API contract**. KrakenD is not just a proxy; it can connect to **multiple upstream services (backends)** simultaneously.

  * **Parallel Connections:** When a user calls a specific endpoint (e.g., `GET /user`), KrakenD connects **in parallel and simultaneously** to different backends.
  * **Example Backends:**
      * An external source: `api.github.com`
      * Another URL, e.g., `/user/repos`

-----

### Data Manipulation Options

KrakenD allows for basic manipulation of the data received from the backends.

  * **`allow` List:** The Gateway can **hide any field not present** in the `allow` list of items from a backend's response (e.g., limiting the fields returned from the `api.github.com` response). This is configured under the backend's `extra_config`.
      * **JSON Example (Allow under Backend):**
        ```json
        "backend": [
            {
                "url_pattern": "/users/krakend",
                "host": [
                    "https://api.github.com"
                ],
                "allow": [
                    "login",
                    "id",
                    "avatar_url",
                    "html_url"
                ]
                
            }
        ]
        ```
  * **`deny` List:** This works similarly to `allow` but explicitly **hides** the specified attributes.
  * **Renaming/Mapping:** Attributes can be renamed (e.g., changing the field **"blog"** from GitHub's response to **"website"**) to unify fields from different backends. This is also configured under the `backend` attribute .
      * **JSON Example (Renaming under Backend):**
        ```json
        "backend": [
            {
                "url_pattern": "/users/krakend",
                "host": [
                    "https://api.github.com"
                ],
                "allow": [
                    "login",
                    "id",
                    "avatar_url",
                    "html_url"
                ],
                "mapping": {
                    "blog": "website"
                }
                
            }
        ]
        ```
  * **Encapsulation/Grouping:** Responses from multiple backends can be grouped inside a single field (e.g., encapsulating the results from the two backend calls within a **`user`** group in the final API response). This is configured directly on the backend object within the endpoint.
      * **JSON Example (Grouping on Backend):**
        ```json
        // Endpoint Configuration
        {
            "endpoint": "/user",
            "output_encoding": "json",
            "backend": [
                {
                    "url_pattern": "/users/krakend",
                    "group": "user", // Groups the data into a field named 'user'
                    // ... other backend configuration
                },
                {
                    "url_pattern": "/users/krakend/repos",
                    "group": "repos", // Groups the data into a field named 'repos'
                    // ... other backend configuration
                }
            ]
        }
        ```

-----

### Response Example

Calling the KrakenD endpoint instead of the original APIs shows the aggregated and manipulated response:

  * The **`repos`** field contains the response from the second backend call.
  * The **`user`** field contains the response from the first API call.
  * The response **does not contain all the fields** from the original `api.github.com` due to the `allow` list.
  * The field **`blog`** is renamed to **`website`**.
  * The **`repos`** result is an array/collection.

-----
## Caching and Performance with Jaeger Tracing

This section focuses on using the **HTTP Cache middleware** in KrakenD to improve performance

-----

### 1\. HTTP Caching

  * **Function:** The Market Cache endpoint connects to an external URL (e.g., `coinggaco.com`). The **HTTP Cache middleware** is enabled to store the backend's response in KrakenD's memory for subsequent calls.
  * **Mechanism:** The backend API's response includes a **`Cache-Control`** header that specifies the **Time-To-Live (TTL)**, such as **`300 seconds`** (5 minutes). This is the duration for which KrakenD saves the content.
  * **Configuration:** The caching is enabled by adding the middleware to the backend configuration.
      * **JSON Example (Enabling Cache on Backend):**
        ```json
        "extra_config": {
            "io/sf/krakend/http-cache": {}
        }
        ```
  * **Result:** When the endpoint is called a second time, the content is served instantly **from the cache**, resulting in significant performance gains.

-----

### 2\. Caching Considerations

  * **Backend Control:** You must be aware that the **`Cache-Control`** header and thus the TTL are typically **set by the backend**.
  * **Memory Management:** If you store large amounts of backend calls for a long time, the **memory consumption** of KrakenD will increase significantly, which is an important operational consideration.


-----
## Rate limit feture of Krakend

This section focuses on using the **Rate Limit** in KrakenD to improve the security aspect both at `krakend` api level and `backend endpoint` level

# Rate Limiting

This section demonstrates how KrakenD can apply rate limits at both the backend (upstream) level and the endpoint (user) level to protect resources and manage traffic.

## 1. Backend Rate Limiting (Upstream Protection)

This limit is applied to a specific upstream service to prevent KrakenD from overwhelming it.

**Target:** The `products.json` URL is limited.

**Limit:** KrakenD can only make one request per second to this backend.

**Configuration:**
```json
"backend": [
    {
        "url_pattern": "/products.json",
        "extra_config": {
            "qos/ratelimit/proxy": {
                "max_rate": 1,
                "capacity": 1
            }
        }
    }
]
```

---

## 2. Endpoint Rate Limiting (User Protection)

This limit is applied to the final API endpoint the user is calling to protect the Gateway itself and ensure fair usage.

**Target:** The `/shop` endpoint the end-user calls.

**Limit:** The user can only make two requests per second to the KrakenD endpoint.

**Configuration:**
```json
"endpoint": {
    "extra_config": {
        "qos/ratelimit/endpoint": {
            "max_rate": 2,
            "strategy": "ip"
        }
    }
}
```

---

## 3. Rate Limit Enforcement and Partial Responses

The combination of the limits creates a bottleneck for the backend product URL (1 RPS) within the overall endpoint limit (2 RPS).

### Scenario

The `/shop` endpoint aggregates data from two backends:
- `shop-campaigns` (No limit)
- `products.json` (Limited to 1 RPS)

### Response Behaviors

#### Initial Response
A request returns `200 OK` and the header `X-Krakend-Complete: true`, meaning both backends returned data.

#### Exceeding Backend Limit
If the user requests faster than 1 RPS:
- The `products.json` URL fails (rate limited).
- KrakenD returns a **partial response** containing only the `shop-campaigns` data.
- The header is `X-Krakend-Complete: false`.
- **KrakenD's Policy:** The policy is "return whatever you can" (fail-fast), not retrying the failed call.

#### Exceeding Endpoint Limit
If the user exceeds the 2 RPS limit (i.e., too many requests overall):
- KrakenD returns a `503 Service Unavailable` to the end user, preventing any request from reaching the backends.
- This is the last rate limit to take effect.

---

## Summary

| Rate Limit Type | Target | Limit | Effect on Exceeding |
|-----------------|--------|-------|---------------------|
| **Backend Rate Limit** | `products.json` backend | 1 RPS | Partial response with `X-Krakend-Complete: false` |
| **Endpoint Rate Limit** | `/shop` endpoint | 2 RPS | `503 Service Unavailable` |

##  Endpoint Enhancements 

This section focuses on two advanced endpoint configurations in KrakenD: **Concurrent Calls** for performance and **Sequential Endpoints** for request dependency.

-----

### 1\. Concurrent Calls 

**Concurrent calls** is an efficiency technique where KrakenD requests the **same data** from the upstream service **multiple times simultaneously** to maximize the chance of a fast response.

  * **Goal:** Improve **latency** and user experience by leveraging distributed systems.
  * **Mechanism:**
    1.  The user calls a KrakenD endpoint.
    2.  KrakenD makes **multiple parallel calls** (e.g., three calls) to the same configured backend URL.     
    3.  It then returns the **fastest successful response** and immediately cancels the slower, outstanding calls.
  * **Trade-off:** Applies **more pressure** to the backend, which must be able to handle the increased traffic, but can result in up to **70% faster responses** for the end-user.
  * **Use Case:** Highly effective when the backend is a cluster of servers (where one machine might be faster than others) or when network latency is variable.
  * **Configuration:**
    ```json
    "backend": [
        {
            "url_pattern": "/coins/markets.json",
            "concurrent_calls": 3,
            "host": [
                "http://api.coingecko.com"
            ]
        }
    ]
    ```
      * The `"concurrent_calls": 3` field specifies how many times KrakenD should hit the backend concurrently.

-----

### 2\. Sequential Endpoints 

**Sequential endpoints** enforce a **dependency chain** where the output of one backend call is used as the input for a subsequent call.

  * **Goal:** Create a **data cascade** within the API Gateway when a backend requires an ID or parameter from a preceding response.
  * **Mechanism:**
    1.  KrakenD calls the **first backend** (`hotels/1.json`).
    2.  It **waits** for the full response.
    3.  It then extracts a required value (e.g., `destination_id`) from that response.
    4.  It **injects** this extracted value into the URL or payload of the **second backend** call (`destinations/{destination_id}`).     5.  The final aggregated response is returned to the user.
  * **Use Case:** Ideal for scenarios where fetching one resource is a prerequisite for fetching a second, dependent resource (e.g., fetching a user's ID, then using that ID to fetch the user's details).
  * **Configuration:**
    ```json
    {
       "@comment": "",
       "endpoint": "/sequential",
       "backend": [
         {
            "url_pattern": "hotels/1.json",
            "allow": ["destination_id"]
         },
         {
            "url_pattern": "destination/{resp0_destination_id}",
         }
       ],
       "extra_config": {
        "proxy": {
            "sequential": true
        }
       }
    }
    ```
      * The `router` section within the endpoint's `extra_config` is set to `"sequential": true` to enable this behavior.
      * The backends themselves must be configured to pass/inject data, but the `sequential` flag is the key enabler.

#
# KrakenD Async Agent - Analysis & Conclusion

## Initial Understanding
KrakenD Async Agent is marketed as a feature to enable fire-and-forget operations by:
- Accepting client requests
- Immediately returning 202 Accepted
- Asynchronously forwarding to Kafka/message broker
- Decoupling client from backend processing

## Use Cases Explored

### Healthcare Domain
1. **ER Patient Triage** - Nurse submits vitals, multiple systems notified
2. **E-Prescribing** - Doctor prescribes, pharmacy + insurance + notifications happen async
3. **Lab Results** - Blood test results trigger multiple downstream actions
4. **Prior Authorization** - Insurance approval requests with auto-adjudication

### General Use Cases
1. **User Registration with Side Effects** - Create user + send email + CRM sync
2. **Webhook Relay** - External webhooks → Gateway → Internal systems
3. **IoT Telemetry** - Device events → Gateway → Analytics pipeline
4. **API Rate Limiting + Buffering** - High-frequency events from mobile apps

## Critical Insight Identified

**Key Question Raised:**
> "Why use KrakenD Async Agent when the backend service can directly produce to Kafka?"

**Comparison:**
```
Option A: Client → KrakenD Async Agent → Kafka → Consumer Services
Option B: Client → KrakenD → Backend Service (produces to Kafka directly) → Consumer Services
```

Both achieve:
- ✅ Fire-and-forget pattern
- ✅ 202 Accepted response
- ✅ Decoupled architecture
- ✅ Event-driven processing

**Option B is simpler:**
- Backend owns its domain events
- One less infrastructure component
- Better domain logic encapsulation
- Same performance characteristics

## Conclusion: When to Use KrakenD Async Agent

### ❌ NOT Useful (95% of cases)
**Don't use when:**
- You control the backend service
- Backend can produce to Kafka directly
- You need business logic before event production
- You want services to own their events

**Better approach:** Have backend service produce to Kafka directly

### ✅ Actually Useful (5% of cases)

**1. Legacy Service Integration**
- Cannot modify vendor/legacy service code
- Need to add event-driven capabilities around it
- Gateway mirrors requests to Kafka for new consumers

**2. Pure Protocol Translation (No Business Logic)**
- IoT devices sending raw telemetry
- Just need HTTP → Kafka transformation
- No validation or enrichment needed
- Simpler than deploying a dedicated service

**3. Webhook Aggregation Gateway**
- Receiving webhooks from many external sources (Stripe, Twilio, GitHub, etc.)
- Need fast acknowledgment (< 50ms) for external systems
- Centralized auth, rate limiting, and routing
- Alternative to deploying multiple webhook-relay services

## Key Takeaway

**KrakenD Async Agent is a niche feature.** The marketing emphasizes fire-and-forget and decoupling, but these benefits are equally achievable by having backend services produce to Kafka directly.

**Primary value:** Quick protocol translation (HTTP → Kafka) when you cannot or don't want to modify backend services.

**Architectural Principle:** Services should own their events. If you control the backend, let it produce to Kafka directly rather than relying on gateway-level event production.

## Better KrakenD Use Cases to Explore

Instead of async agent, focus on KrakenD's real strengths:
1. **API Aggregation** - Merge multiple backend calls into single response
2. **Circuit Breaking** - Prevent cascading failures
3. **Rate Limiting** - Protect backends from overload
4. **Authentication/Authorization** - Centralized security (JWT, OAuth, AWS Cognito)
5. **Response Caching** - Reduce backend load
6. **Load Balancing** - Distribute traffic across instances

## GitHub Webhook Reality Check

**Question:** Does GitHub use async agent pattern for webhooks?

**Answer:** GitHub receives webhooks from external integrations and needs to:
- Acknowledge quickly (< 500ms)
- Fan out to multiple internal systems
- Handle millions of webhook deliveries

**Likely Architecture:**
```
External App → GitHub Webhook Endpoint → Message Queue → Internal Services
```

They probably use:
- Custom webhook receiver service (not KrakenD)
- Internal message queue (Kafka, RabbitMQ, or proprietary)
- Multiple consumer services

**Why not async agent:** GitHub has sophisticated internal infrastructure and controls their entire stack, so they build custom solutions optimized for their scale.

## Recommended Learning Path

1. ✅ Understand async agent concept (completed)
2. ✅ Identify limitations (completed)
3. ⏭️ Explore **Circuit Breaking** with KrakenD
4. ⏭️ Explore **Authentication/Authorization** with AWS Cognito + KrakenD
5. ⏭️ Build event-driven microservices with Kafka (no gateway)

---

**Date:** November 19, 2024
**Conclusion:** KrakenD Async Agent is architecturally interesting but practically limited. Better to focus on KrakenD's core API Gateway strengths and implement event-driven architecture at the service level.
