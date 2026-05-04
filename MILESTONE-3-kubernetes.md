# mediq — Milestone 3: Kubernetes + Cloud Native

## Branch
```powershell
git checkout feature/mediq-m2-core-services
git checkout -b feature/mediq-m3-kubernetes
```

## What This Milestone Covers
```
M3a: Kubernetes manifests for all services (Deployment, Service, ConfigMap, Secret, HPA)
M3b: Deploy mediq to local kind cluster — everything running in K8s
M3c: Helm chart — single command deploys entire mediq platform
M3d: KEDA — scale notification-service based on Kafka consumer lag
```

## Folder Structure Created
```
mediq/
  k8s/                          ← NEW — raw Kubernetes manifests
    namespace.yaml
    postgres/
      deployment.yaml
      service.yaml
      pvc.yaml
    redis/
      deployment.yaml
      service.yaml
    kafka/
      deployment.yaml
      service.yaml
    keycloak/
      deployment.yaml
      service.yaml
    jaeger/
      deployment.yaml
      service.yaml
    user-service/
      deployment.yaml
      service.yaml
      configmap.yaml
      secret.yaml
      hpa.yaml
    doctor-service/
      deployment.yaml
      service.yaml
      configmap.yaml
      hpa.yaml
    appointment-service/
      deployment.yaml
      service.yaml
      configmap.yaml
      hpa.yaml
    notification-service/
      deployment.yaml
      service.yaml
      configmap.yaml
      keda-scaledobject.yaml    ← KEDA instead of HPA
    krakend/
      deployment.yaml
      service.yaml
      configmap.yaml

  helm/                         ← NEW — Helm chart
    mediq/
      Chart.yaml
      values.yaml
      values-dev.yaml
      values-prod.yaml
      templates/
        _helpers.tpl
        namespace.yaml
        postgres.yaml
        redis.yaml
        kafka.yaml
        keycloak.yaml
        user-service.yaml
        doctor-service.yaml
        appointment-service.yaml
        notification-service.yaml
        krakend.yaml
        jaeger.yaml
```

---

## TASK-M3a: Kubernetes Manifests

### Step 1: Create namespace
`k8s/namespace.yaml`:
```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: mediq
  labels:
    app: mediq
    environment: dev
```

### Step 2: user-service ConfigMap
`k8s/user-service/configmap.yaml`:
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: user-service-config
  namespace: mediq
data:
  DB_URL: "jdbc:postgresql://postgres-service:5432/mediq_users"
  DB_USERNAME: "mediq"
  KAFKA_BOOTSTRAP_SERVERS: "kafka-service:9092"
  REDIS_HOST: "redis-service"
  REDIS_PORT: "6379"
  KEYCLOAK_ADMIN_URL: "http://keycloak-service:8090"
  JAEGER_ENDPOINT: "http://jaeger-service:4318/v1/traces"
  ENVIRONMENT: "kubernetes"
```

### Step 3: user-service Secret
`k8s/user-service/secret.yaml`:
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: user-service-secret
  namespace: mediq
type: Opaque
# IMPORTANT: In production use External Secrets Operator or AWS Secrets Manager
# These are base64 encoded values — NOT encrypted
# base64 of "mediq" = bWVkaXE=
# base64 of "admin-secret" = YWRtaW4tc2VjcmV0
data:
  DB_PASSWORD: bWVkaXE=
  KEYCLOAK_ADMIN_SECRET: YWRtaW4tc2VjcmV0
```

### Step 4: user-service Deployment
`k8s/user-service/deployment.yaml`:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: user-service
  namespace: mediq
  labels:
    app: user-service
    version: v1
spec:
  replicas: 2
  selector:
    matchLabels:
      app: user-service
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1          # allow 1 extra pod during rollout
      maxUnavailable: 0    # never take a pod down before new one is ready
  template:
    metadata:
      labels:
        app: user-service
        version: v1
    spec:
      containers:
        - name: user-service
          image: mediq/user-service:latest
          imagePullPolicy: IfNotPresent  # for kind cluster (local image)
          ports:
            - containerPort: 8081
              name: http
          envFrom:
            - configMapRef:
                name: user-service-config
            - secretRef:
                name: user-service-secret

          # Resource requests/limits — prevents OOMKilled
          resources:
            requests:
              memory: "512Mi"    # guaranteed minimum
              cpu: "250m"        # 0.25 CPU guaranteed
            limits:
              memory: "1Gi"      # killed if exceeds this (OOMKilled)
              cpu: "1000m"       # throttled if exceeds this

          # Liveness probe — kills container if fails
          # Uses the custom /actuator/health/liveness endpoint from M1.3
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8081
            initialDelaySeconds: 60   # wait 60s for app to start
            periodSeconds: 15         # check every 15s
            timeoutSeconds: 5         # fail if no response in 5s
            failureThreshold: 3       # restart after 3 consecutive failures

          # Readiness probe — removes from Service if fails
          # Uses the custom /actuator/health/readiness endpoint from M1.3
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8081
            initialDelaySeconds: 30   # start checking after 30s
            periodSeconds: 10         # check every 10s
            timeoutSeconds: 5
            failureThreshold: 3       # remove from LB after 3 failures

          # Startup probe — gives app time to start before liveness kicks in
          startupProbe:
            httpGet:
              path: /actuator/health
              port: 8081
            initialDelaySeconds: 10
            periodSeconds: 5
            failureThreshold: 24      # 24 × 5s = 2 minutes max startup time

          env:
            - name: JAVA_OPTS
              value: "-Xms256m -Xmx768m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

### Step 5: user-service Service
`k8s/user-service/service.yaml`:
```yaml
apiVersion: v1
kind: Service
metadata:
  name: user-service
  namespace: mediq
  labels:
    app: user-service
spec:
  type: ClusterIP        # internal only — accessed via KrakenD
  selector:
    app: user-service
  ports:
    - name: http
      port: 8081
      targetPort: 8081
      protocol: TCP
```

### Step 6: user-service HPA
`k8s/user-service/hpa.yaml`:
```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: user-service-hpa
  namespace: mediq
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: user-service
  minReplicas: 2
  maxReplicas: 8        # must be <= number of Kafka partitions if consuming
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70   # scale up when avg CPU > 70%
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80   # scale up when avg memory > 80%
```

### Step 7: notification-service KEDA ScaledObject
`k8s/notification-service/keda-scaledobject.yaml`:
```yaml
# KEDA scales notification-service based on Kafka consumer lag
# NOT CPU — because Kafka consumer is IO-bound, not CPU-bound
apiVersion: keda.sh/v1alpha1
kind: ScaledObject
metadata:
  name: notification-service-scaler
  namespace: mediq
spec:
  scaleTargetRef:
    name: notification-service
  minReplicaCount: 1
  maxReplicaCount: 8     # MUST equal Kafka topic partition count
  cooldownPeriod: 30
  triggers:
    - type: kafka
      metadata:
        bootstrapServers: kafka-service:9092
        consumerGroup: mediq-notification-appointment-group
        topic: mediq.appointment.events
        lagThreshold: "100"    # scale up if lag > 100 messages per partition
        offsetResetPolicy: latest
```

### Step 8: Create manifests for all other services

Follow the EXACT same pattern as user-service for:
- `k8s/doctor-service/` (port 8083, ConfigMap, Secret, Deployment, Service, HPA)
- `k8s/appointment-service/` (port 8084)
- `k8s/notification-service/` (port 8085, use KEDA instead of HPA)
- `k8s/krakend/` (port 8080, type: LoadBalancer for KrakenD Service)
- `k8s/postgres/` (type: ClusterIP, PVC for storage)
- `k8s/redis/` (type: ClusterIP)
- `k8s/kafka/` (type: ClusterIP, PVC for storage)
- `k8s/keycloak/` (type: ClusterIP)
- `k8s/jaeger/` (type: ClusterIP, port 16686 as NodePort for UI access)

KrakenD Service should be type LoadBalancer:
```yaml
spec:
  type: LoadBalancer    # external access point for all clients
  ports:
    - name: http
      port: 8080
      targetPort: 8080
```

---

## TASK-M3b: kind Cluster Setup and Deploy

### Step 1: Create kind cluster config
Create `kind-config.yaml` in project root:
```yaml
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
name: mediq
nodes:
  - role: control-plane
    extraPortMappings:
      - containerPort: 30080   # KrakenD NodePort
        hostPort: 8080
        protocol: TCP
      - containerPort: 30090   # Keycloak NodePort
        hostPort: 8090
        protocol: TCP
      - containerPort: 30086   # Jaeger UI NodePort
        hostPort: 16686
        protocol: TCP
  - role: worker
  - role: worker
```

### Step 2: Create and configure cluster
```powershell
# Create cluster (from WSL or PowerShell)
kind create cluster --config kind-config.yaml

# Verify
kubectl cluster-info --context kind-mediq
kubectl get nodes
```

### Step 3: Build and load images into kind
```powershell
# Build all service images
docker build -t mediq/user-service:latest ./user-service
docker build -t mediq/doctor-service:latest ./doctor-service
docker build -t mediq/appointment-service:latest ./appointment-service
docker build -t mediq/notification-service:latest ./notification-service

# Load into kind cluster (kind doesn't pull from Docker Hub by default)
kind load docker-image mediq/user-service:latest --name mediq
kind load docker-image mediq/doctor-service:latest --name mediq
kind load docker-image mediq/appointment-service:latest --name mediq
kind load docker-image mediq/notification-service:latest --name mediq
```

### Step 4: Deploy to kind
```powershell
# Apply namespace first
kubectl apply -f k8s/namespace.yaml

# Apply infrastructure (order matters — services before apps)
kubectl apply -f k8s/postgres/
kubectl apply -f k8s/redis/
kubectl apply -f k8s/kafka/
kubectl apply -f k8s/keycloak/
kubectl apply -f k8s/jaeger/

# Wait for infrastructure to be ready
kubectl wait --for=condition=ready pod -l app=postgres -n mediq --timeout=120s
kubectl wait --for=condition=ready pod -l app=kafka -n mediq --timeout=120s

# Apply application services
kubectl apply -f k8s/user-service/
kubectl apply -f k8s/doctor-service/
kubectl apply -f k8s/appointment-service/
kubectl apply -f k8s/notification-service/
kubectl apply -f k8s/krakend/

# Verify all pods running
kubectl get pods -n mediq
```

### Step 5: Verify deployment
```powershell
# All pods should be Running and Ready
kubectl get pods -n mediq
# Expected:
# NAME                                    READY   STATUS    RESTARTS
# postgres-xxxxx                          1/1     Running   0
# redis-xxxxx                             1/1     Running   0
# kafka-xxxxx                             1/1     Running   0
# keycloak-xxxxx                          1/1     Running   0
# jaeger-xxxxx                            1/1     Running   0
# user-service-xxxxx                      1/1     Running   0
# user-service-yyyyy                      1/1     Running   0  (2 replicas)
# doctor-service-xxxxx                    1/1     Running   0
# appointment-service-xxxxx               1/1     Running   0
# notification-service-xxxxx              1/1     Running   0
# krakend-xxxxx                           1/1     Running   0

# Test liveness probe directly
kubectl exec -n mediq deploy/user-service -- wget -qO- http://localhost:8081/actuator/health/liveness

# Test via KrakenD (exposed on host port 8080 via kind extraPortMappings)
curl http://localhost:8080/api/v1/users/patients/register ...
```

### Step 6: Test rolling deploy
```powershell
# Simulate a new image version
docker build -t mediq/user-service:v2 ./user-service
kind load docker-image mediq/user-service:v2 --name mediq

# Update deployment image
kubectl set image deployment/user-service user-service=mediq/user-service:v2 -n mediq

# Watch rolling update
kubectl rollout status deployment/user-service -n mediq
# Expected: "deployment successfully rolled out"
# Zero downtime — readiness probe ensures traffic only routes to ready pods
```

---

## TASK-M3c: Helm Chart

### Why Helm
```
Problem with raw k8s manifests:
  12 services × multiple yaml files = 60+ files
  Deploying to dev: different image tags, smaller replicas
  Deploying to prod: different secrets, larger replicas, different domains
  Manual editing of 60 files per environment = error-prone

Helm solution:
  Templates with variables
  values.yaml = defaults
  values-dev.yaml = dev overrides
  values-prod.yaml = prod overrides
  helm install mediq ./helm/mediq -f values-dev.yaml
  One command. Any environment.
```

### Chart.yaml
`helm/mediq/Chart.yaml`:
```yaml
apiVersion: v2
name: mediq
description: mediq Healthcare Platform — Practo-like microservices
type: application
version: 0.1.0
appVersion: "1.0.0"
keywords:
  - healthcare
  - microservices
  - mediq
maintainers:
  - name: Kishan Jaiswal
```

### values.yaml — defaults
`helm/mediq/values.yaml`:
```yaml
global:
  namespace: mediq
  imageRegistry: mediq
  imageTag: latest
  imagePullPolicy: IfNotPresent

postgres:
  image: postgres:16-alpine
  database: mediq_users
  username: mediq
  password: mediq       # override in values-prod.yaml with secret ref
  storage: 10Gi

redis:
  image: redis:7-alpine
  storage: 2Gi

kafka:
  image: confluentinc/cp-kafka:7.6.0
  zookeeperImage: confluentinc/cp-zookeeper:7.6.0
  storage: 10Gi

keycloak:
  image: quay.io/keycloak/keycloak:24.0.3
  adminPassword: admin  # override in values-prod.yaml

userService:
  replicas: 2
  port: 8081
  resources:
    requests:
      memory: 512Mi
      cpu: 250m
    limits:
      memory: 1Gi
      cpu: 1000m
  hpa:
    minReplicas: 2
    maxReplicas: 8
    cpuThreshold: 70

doctorService:
  replicas: 2
  port: 8083
  resources:
    requests:
      memory: 512Mi
      cpu: 250m
    limits:
      memory: 1Gi
      cpu: 1000m

appointmentService:
  replicas: 2
  port: 8084
  resources:
    requests:
      memory: 512Mi
      cpu: 250m
    limits:
      memory: 1Gi
      cpu: 1000m

notificationService:
  replicas: 1
  port: 8085
  keda:
    minReplicas: 1
    maxReplicas: 8
    lagThreshold: 100

krakend:
  image: devopsfaith/krakend:2.7
  replicas: 2
  port: 8080

jaeger:
  image: jaegertracing/all-in-one:1.57
  enabled: true
```

### values-dev.yaml
`helm/mediq/values-dev.yaml`:
```yaml
# Development overrides
global:
  imageTag: latest

userService:
  replicas: 1            # smaller in dev — save resources
  hpa:
    minReplicas: 1
    maxReplicas: 3

doctorService:
  replicas: 1

appointmentService:
  replicas: 1

notificationService:
  replicas: 1

jaeger:
  enabled: true          # tracing on in dev

krakend:
  replicas: 1
```

### _helpers.tpl — template helpers
`helm/mediq/templates/_helpers.tpl`:
```
{{- define "mediq.labels" -}}
app: {{ .name }}
chart: {{ .Chart.Name }}-{{ .Chart.Version }}
release: {{ .Release.Name }}
environment: {{ .Values.global.environment | default "dev" }}
{{- end }}

{{- define "mediq.image" -}}
{{ .Values.global.imageRegistry }}/{{ .name }}:{{ .Values.global.imageTag }}
{{- end }}
```

### user-service template
`helm/mediq/templates/user-service.yaml`:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: user-service
  namespace: {{ .Values.global.namespace }}
  labels:
    {{- include "mediq.labels" (merge (dict "name" "user-service") .) | nindent 4 }}
spec:
  replicas: {{ .Values.userService.replicas }}
  selector:
    matchLabels:
      app: user-service
  template:
    metadata:
      labels:
        app: user-service
    spec:
      containers:
        - name: user-service
          image: {{ .Values.global.imageRegistry }}/user-service:{{ .Values.global.imageTag }}
          imagePullPolicy: {{ .Values.global.imagePullPolicy }}
          ports:
            - containerPort: {{ .Values.userService.port }}
          resources:
            {{- toYaml .Values.userService.resources | nindent 12 }}
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: {{ .Values.userService.port }}
            initialDelaySeconds: 60
            periodSeconds: 15
            failureThreshold: 3
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: {{ .Values.userService.port }}
            initialDelaySeconds: 30
            periodSeconds: 10
            failureThreshold: 3
# ... rest of spec
```

### Deploy with Helm
```powershell
# Install to kind cluster
helm install mediq ./helm/mediq -f ./helm/mediq/values-dev.yaml -n mediq --create-namespace

# Upgrade after changes
helm upgrade mediq ./helm/mediq -f ./helm/mediq/values-dev.yaml -n mediq

# Check status
helm status mediq -n mediq

# List all releases
helm list -n mediq

# Uninstall
helm uninstall mediq -n mediq
```

---

## TASK-M3d: KEDA Setup

### Install KEDA on kind cluster
```powershell
# Add KEDA helm repo
helm repo add kedacore https://kedacore.github.io/charts
helm repo update

# Install KEDA
helm install keda kedacore/keda --namespace keda --create-namespace

# Verify KEDA is running
kubectl get pods -n keda
```

### Apply KEDA ScaledObject for notification-service
```powershell
kubectl apply -f k8s/notification-service/keda-scaledobject.yaml

# Watch scaling in action
kubectl get hpa -n mediq   # KEDA creates an HPA under the hood
```

### Test KEDA scaling
```powershell
# Produce a burst of appointment events
docker exec -it mediq-kafka kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic mediq.appointment.events
# Type 500 JSON messages and press Ctrl+C

# Watch notification-service scale up
kubectl get pods -n mediq -l app=notification-service -w
# Should scale from 1 → multiple pods as lag grows
```

---

## Verification

### Full cluster health check
```powershell
kubectl get all -n mediq

# Check HPA
kubectl get hpa -n mediq

# Check events (look for scaling events)
kubectl get events -n mediq --sort-by='.lastTimestamp' | tail -20

# Check probes working
kubectl describe pod $(kubectl get pod -l app=user-service -n mediq -o jsonpath='{.items[0].metadata.name}') -n mediq | grep -A 15 "Liveness\|Readiness"

# Rolling deploy test
kubectl set image deployment/user-service user-service=mediq/user-service:v2 -n mediq
kubectl rollout status deployment/user-service -n mediq
kubectl rollout history deployment/user-service -n mediq

# Rollback if needed
kubectl rollout undo deployment/user-service -n mediq
```

---

## Commit
```powershell
git add .
git commit -m "feat(m3): kubernetes manifests, kind cluster, helm chart, keda

- k8s/ manifests for all 9 services
- Liveness + readiness probes on all deployments
- Resource requests/limits configured (prevents OOMKilled)
- HPA for user/doctor/appointment services (CPU-based)
- KEDA for notification-service (Kafka lag-based)
- kind cluster config with port mappings
- Helm chart with dev/prod values separation
- Rolling deploy strategy configured (maxUnavailable=0)
- kind cluster running mediq end-to-end"
```
