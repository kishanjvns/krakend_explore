# mediq — Helmfile Setup for kishan-lab kind Cluster

## Branch
```powershell
# PowerShell — Windows
git checkout feature/mediq-m3-kubernetes
git checkout -b feature/mediq-helmfile
```

## Environment
```
Build images:     WSL terminal (docker build + kind load)
Deploy:           PowerShell   (helmfile apply)
kubectl verify:   PowerShell   (kubectl get pods)
Cluster:          kishan-lab (existing, 3 nodes, no need to recreate)
Namespace:        mediq (created by helmfile)
```

## Why Helmfile Over Single helm install

```
Current helm/ folder problem:
  One chart with ALL templates together
  helm install mediq ./helm/mediq → deploys everything simultaneously
  postgres not ready → user-service crashes → restart loop
  kafka not ready → appointment-service crash → restart loop
  Race conditions on every deploy

Helmfile solution:
  Splits deployment into ordered RELEASES
  Each release has explicit needs: []
  postgres must be healthy before user-service deploys
  kafka must be healthy before appointment-service deploys
  Ordered, reliable, production-grade
```

---

## New Folder Structure

```
D:\codebase\krakend_explore\
  helmfile.yaml                  ← root orchestrator
  helmfile.d\
    00-namespace.yaml            ← namespace first
    01-infrastructure.yaml       ← postgres, redis, zookeeper, kafka, keycloak, jaeger
    02-platform.yaml             ← user-service, doctor-service
    03-core.yaml                 ← appointment-service, notification-service
    04-advanced.yaml             ← emr-service, analytics-service
    05-gateway.yaml              ← krakend
  helm\
    infrastructure\              ← NEW individual charts
      postgres\
      redis\
      zookeeper\
      kafka\
      keycloak\
      jaeger\
    services\                    ← NEW individual charts
      user-service\
      doctor-service\
      appointment-service\
      notification-service\
      emr-service\
      analytics-service\
    gateway\
      krakend\
  environments\
    dev.yaml                     ← kind cluster overrides (1 replica, small resources)
    prod.yaml                    ← future AWS EKS overrides
```

---

## Step 1: Create Root helmfile.yaml

Create `D:\codebase\krakend_explore\helmfile.yaml`:

```yaml
# mediq — Root Helmfile
# Orchestrates all releases in dependency order

helmDefaults:
  wait: true              # wait for all resources to be ready before next release
  waitForJobs: true
  timeout: 300            # 5 minute timeout per release
  recreatePods: false
  force: false

environments:
  dev:
    values:
      - environments/dev.yaml
  prod:
    values:
      - environments/prod.yaml

helmfiles:
  - path: helmfile.d/00-namespace.yaml
  - path: helmfile.d/01-infrastructure.yaml
    needs:
      - helmfile.d/00-namespace.yaml
  - path: helmfile.d/02-platform.yaml
    needs:
      - helmfile.d/01-infrastructure.yaml
  - path: helmfile.d/03-core.yaml
    needs:
      - helmfile.d/02-platform.yaml
  - path: helmfile.d/04-advanced.yaml
    needs:
      - helmfile.d/03-core.yaml
  - path: helmfile.d/05-gateway.yaml
    needs:
      - helmfile.d/02-platform.yaml   # krakend needs services to exist
```

---

## Step 2: Create helmfile.d/ files

### 00-namespace.yaml
Create `helmfile.d/00-namespace.yaml`:

```yaml
releases:
  - name: mediq-namespace
    chart: ./helm/infrastructure/namespace
    namespace: mediq
    createNamespace: true
    values:
      - namespace: mediq
```

### 01-infrastructure.yaml
Create `helmfile.d/01-infrastructure.yaml`:

```yaml
releases:
  - name: mediq-postgres
    chart: ./helm/infrastructure/postgres
    namespace: mediq
    values:
      - ./helm/infrastructure/postgres/values.yaml
    set:
      - name: image
        value: postgres:16-alpine
      - name: username
        value: mediq
      - name: password
        value: mediq
      - name: storage
        value: "{{ .Environment.Values.postgres.storage | default \"2Gi\" }}"

  - name: mediq-redis
    chart: ./helm/infrastructure/redis
    namespace: mediq
    values:
      - ./helm/infrastructure/redis/values.yaml

  - name: mediq-zookeeper
    chart: ./helm/infrastructure/zookeeper
    namespace: mediq
    values:
      - ./helm/infrastructure/zookeeper/values.yaml

  - name: mediq-kafka
    chart: ./helm/infrastructure/kafka
    namespace: mediq
    needs:
      - mediq/mediq-zookeeper          # kafka needs zookeeper first
    values:
      - ./helm/infrastructure/kafka/values.yaml

  - name: mediq-keycloak
    chart: ./helm/infrastructure/keycloak
    namespace: mediq
    needs:
      - mediq/mediq-postgres
    values:
      - ./helm/infrastructure/keycloak/values.yaml

  - name: mediq-jaeger
    chart: ./helm/infrastructure/jaeger
    namespace: mediq
    values:
      - ./helm/infrastructure/jaeger/values.yaml
```

### 02-platform.yaml
Create `helmfile.d/02-platform.yaml`:

```yaml
releases:
  - name: mediq-user-service
    chart: ./helm/services/user-service
    namespace: mediq
    needs:
      - mediq/mediq-postgres
      - mediq/mediq-redis
      - mediq/mediq-kafka
      - mediq/mediq-keycloak
    values:
      - ./helm/services/user-service/values.yaml
    set:
      - name: replicaCount
        value: "{{ .Environment.Values.userService.replicas | default 1 }}"

  - name: mediq-doctor-service
    chart: ./helm/services/doctor-service
    namespace: mediq
    needs:
      - mediq/mediq-postgres
      - mediq/mediq-kafka
      - mediq/mediq-user-service       # doctor-service consumes user events
    values:
      - ./helm/services/doctor-service/values.yaml
    set:
      - name: replicaCount
        value: "{{ .Environment.Values.doctorService.replicas | default 1 }}"
```

### 03-core.yaml
Create `helmfile.d/03-core.yaml`:

```yaml
releases:
  - name: mediq-appointment-service
    chart: ./helm/services/appointment-service
    namespace: mediq
    needs:
      - mediq/mediq-postgres
      - mediq/mediq-kafka
      - mediq/mediq-user-service
      - mediq/mediq-doctor-service
    values:
      - ./helm/services/appointment-service/values.yaml
    set:
      - name: replicaCount
        value: "{{ .Environment.Values.appointmentService.replicas | default 1 }}"

  - name: mediq-notification-service
    chart: ./helm/services/notification-service
    namespace: mediq
    needs:
      - mediq/mediq-postgres
      - mediq/mediq-kafka
      - mediq/mediq-appointment-service
    values:
      - ./helm/services/notification-service/values.yaml
    set:
      - name: replicaCount
        value: "{{ .Environment.Values.notificationService.replicas | default 1 }}"
```

### 04-advanced.yaml
Create `helmfile.d/04-advanced.yaml`:

```yaml
releases:
  - name: mediq-emr-service
    chart: ./helm/services/emr-service
    namespace: mediq
    needs:
      - mediq/mediq-postgres
      - mediq/mediq-kafka
      - mediq/mediq-appointment-service
    values:
      - ./helm/services/emr-service/values.yaml
    set:
      - name: replicaCount
        value: "{{ .Environment.Values.emrService.replicas | default 1 }}"

  - name: mediq-analytics-service
    chart: ./helm/services/analytics-service
    namespace: mediq
    needs:
      - mediq/mediq-postgres
      - mediq/mediq-kafka
      - mediq/mediq-user-service
      - mediq/mediq-appointment-service
    values:
      - ./helm/services/analytics-service/values.yaml
    set:
      - name: replicaCount
        value: "{{ .Environment.Values.analyticsService.replicas | default 1 }}"
```

### 05-gateway.yaml
Create `helmfile.d/05-gateway.yaml`:

```yaml
releases:
  - name: mediq-krakend
    chart: ./helm/gateway/krakend
    namespace: mediq
    needs:
      - mediq/mediq-user-service
      - mediq/mediq-doctor-service
      - mediq/mediq-appointment-service
      - mediq/mediq-keycloak
    values:
      - ./helm/gateway/krakend/values.yaml
    set:
      - name: replicaCount
        value: "{{ .Environment.Values.krakend.replicas | default 1 }}"
```

---

## Step 3: Create Environment Values Files

### environments/dev.yaml
Create `environments/dev.yaml`:

```yaml
# kind cluster — conservative resources
# kishan-lab: 1 control-plane + 2 workers

postgres:
  storage: 2Gi          # smaller than prod

userService:
  replicas: 1           # 1 replica in dev (save resources)
  resources:
    requests:
      memory: 256Mi     # smaller than prod
      cpu: 100m
    limits:
      memory: 512Mi
      cpu: 500m

doctorService:
  replicas: 1
  resources:
    requests:
      memory: 256Mi
      cpu: 100m
    limits:
      memory: 512Mi
      cpu: 500m

appointmentService:
  replicas: 1
  resources:
    requests:
      memory: 256Mi
      cpu: 100m
    limits:
      memory: 512Mi
      cpu: 500m

notificationService:
  replicas: 1
  resources:
    requests:
      memory: 256Mi
      cpu: 100m
    limits:
      memory: 512Mi
      cpu: 500m

emrService:
  replicas: 1
  resources:
    requests:
      memory: 256Mi
      cpu: 100m
    limits:
      memory: 512Mi
      cpu: 500m

analyticsService:
  replicas: 1
  resources:
    requests:
      memory: 256Mi
      cpu: 100m
    limits:
      memory: 512Mi
      cpu: 500m

krakend:
  replicas: 1

jaeger:
  enabled: true

imageTag: latest
imagePullPolicy: IfNotPresent    # use locally loaded images
```

### environments/prod.yaml
Create `environments/prod.yaml`:

```yaml
# Future AWS EKS — larger resources, multiple replicas

postgres:
  storage: 50Gi

userService:
  replicas: 3
  resources:
    requests:
      memory: 512Mi
      cpu: 250m
    limits:
      memory: 1Gi
      cpu: 1000m

# ... (mirror all services with prod-scale values)

krakend:
  replicas: 3

jaeger:
  enabled: false        # use AWS X-Ray in prod instead

imagePullPolicy: Always # always pull in prod (no local images)
```

---

## Step 4: Refactor Helm Charts — Split Monolith into Individual Charts

The current `helm/mediq/` is one big chart. Split it into individual charts per service. This enables Helmfile to deploy them independently with dependencies.

### Create namespace chart
Create `helm/infrastructure/namespace/Chart.yaml`:
```yaml
apiVersion: v2
name: mediq-namespace
description: mediq Kubernetes namespace
version: 0.1.0
```

Create `helm/infrastructure/namespace/templates/namespace.yaml`:
```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: {{ .Values.namespace }}
  labels:
    app.kubernetes.io/managed-by: helmfile
    environment: dev
```

Create `helm/infrastructure/namespace/values.yaml`:
```yaml
namespace: mediq
```

### Create postgres chart
Create `helm/infrastructure/postgres/Chart.yaml`:
```yaml
apiVersion: v2
name: mediq-postgres
description: PostgreSQL for mediq platform
version: 0.1.0
```

Create `helm/infrastructure/postgres/values.yaml`:
```yaml
image: postgres:16-alpine
username: mediq
password: mediq
database: mediq_users
storage: 2Gi
port: 5432
```

Create `helm/infrastructure/postgres/templates/postgres.yaml`:
```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: postgres-pvc
  namespace: mediq
spec:
  accessModes: [ReadWriteOnce]
  resources:
    requests:
      storage: {{ .Values.storage }}
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: postgres
  namespace: mediq
  labels:
    app: postgres
spec:
  replicas: 1
  selector:
    matchLabels:
      app: postgres
  template:
    metadata:
      labels:
        app: postgres
    spec:
      containers:
        - name: postgres
          image: {{ .Values.image }}
          ports:
            - containerPort: {{ .Values.port }}
          env:
            - name: POSTGRES_USER
              value: {{ .Values.username }}
            - name: POSTGRES_PASSWORD
              value: {{ .Values.password }}
            - name: POSTGRES_DB
              value: {{ .Values.database }}
          volumeMounts:
            - name: data
              mountPath: /var/lib/postgresql/data
          readinessProbe:
            exec:
              command: ["pg_isready", "-U", "{{ .Values.username }}"]
            initialDelaySeconds: 10
            periodSeconds: 5
            failureThreshold: 5
          livenessProbe:
            exec:
              command: ["pg_isready", "-U", "{{ .Values.username }}"]
            initialDelaySeconds: 30
            periodSeconds: 10
      volumes:
        - name: data
          persistentVolumeClaim:
            claimName: postgres-pvc
---
apiVersion: v1
kind: Service
metadata:
  name: postgres-service
  namespace: mediq
spec:
  type: ClusterIP
  selector:
    app: postgres
  ports:
    - port: {{ .Values.port }}
      targetPort: {{ .Values.port }}
```

### Create redis chart
Create `helm/infrastructure/redis/Chart.yaml`:
```yaml
apiVersion: v2
name: mediq-redis
description: Redis for mediq platform
version: 0.1.0
```

Create `helm/infrastructure/redis/values.yaml`:
```yaml
image: redis:7-alpine
port: 6379
```

Create `helm/infrastructure/redis/templates/redis.yaml`:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: redis
  namespace: mediq
  labels:
    app: redis
spec:
  replicas: 1
  selector:
    matchLabels:
      app: redis
  template:
    metadata:
      labels:
        app: redis
    spec:
      containers:
        - name: redis
          image: {{ .Values.image }}
          ports:
            - containerPort: {{ .Values.port }}
          readinessProbe:
            exec:
              command: ["redis-cli", "ping"]
            initialDelaySeconds: 5
            periodSeconds: 5
          livenessProbe:
            exec:
              command: ["redis-cli", "ping"]
            initialDelaySeconds: 10
            periodSeconds: 10
---
apiVersion: v1
kind: Service
metadata:
  name: redis-service
  namespace: mediq
spec:
  type: ClusterIP
  selector:
    app: redis
  ports:
    - port: {{ .Values.port }}
      targetPort: {{ .Values.port }}
```

### Create user-service chart
Create `helm/services/user-service/Chart.yaml`:
```yaml
apiVersion: v2
name: mediq-user-service
description: mediq User Service
version: 0.1.0
```

Create `helm/services/user-service/values.yaml`:
```yaml
replicaCount: 1
image:
  repository: mediq/user-service
  tag: latest
  pullPolicy: IfNotPresent
port: 8081
env:
  DB_URL: jdbc:postgresql://postgres-service:5432/mediq_users
  DB_USERNAME: mediq
  DB_PASSWORD: mediq
  KAFKA_BOOTSTRAP_SERVERS: kafka-service:9092
  REDIS_HOST: redis-service
  REDIS_PORT: "6379"
  KEYCLOAK_ADMIN_URL: http://keycloak-service:8090
  JAEGER_ENDPOINT: http://jaeger-service:4318/v1/traces
  ENVIRONMENT: kubernetes
resources:
  requests:
    memory: 256Mi
    cpu: 100m
  limits:
    memory: 512Mi
    cpu: 500m
hpa:
  enabled: true
  minReplicas: 1
  maxReplicas: 4
  cpuThreshold: 70
```

Create `helm/services/user-service/templates/deployment.yaml`:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: user-service
  namespace: mediq
  labels:
    app: user-service
spec:
  replicas: {{ .Values.replicaCount }}
  selector:
    matchLabels:
      app: user-service
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  template:
    metadata:
      labels:
        app: user-service
    spec:
      containers:
        - name: user-service
          image: "{{ .Values.image.repository }}:{{ .Values.image.tag }}"
          imagePullPolicy: {{ .Values.image.pullPolicy }}
          ports:
            - containerPort: {{ .Values.port }}
          env:
            {{- range $key, $val := .Values.env }}
            - name: {{ $key }}
              value: {{ $val | quote }}
            {{- end }}
          resources:
            {{- toYaml .Values.resources | nindent 12 }}
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: {{ .Values.port }}
            initialDelaySeconds: 60
            periodSeconds: 15
            failureThreshold: 3
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: {{ .Values.port }}
            initialDelaySeconds: 30
            periodSeconds: 10
            failureThreshold: 3
          startupProbe:
            httpGet:
              path: /actuator/health
              port: {{ .Values.port }}
            initialDelaySeconds: 10
            periodSeconds: 5
            failureThreshold: 24
          env:
            - name: JAVA_OPTS
              value: "-Xms128m -Xmx384m -XX:+UseG1GC"
```

Create `helm/services/user-service/templates/service.yaml`:
```yaml
apiVersion: v1
kind: Service
metadata:
  name: user-service
  namespace: mediq
spec:
  type: ClusterIP
  selector:
    app: user-service
  ports:
    - port: {{ .Values.port }}
      targetPort: {{ .Values.port }}
```

Create `helm/services/user-service/templates/hpa.yaml`:
```yaml
{{- if .Values.hpa.enabled }}
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
  minReplicas: {{ .Values.hpa.minReplicas }}
  maxReplicas: {{ .Values.hpa.maxReplicas }}
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: {{ .Values.hpa.cpuThreshold }}
{{- end }}
```

### Repeat same pattern for all other services

Follow the EXACT same structure (Chart.yaml + values.yaml + templates/) for:

```
helm/infrastructure/zookeeper/    (image: confluentinc/cp-zookeeper:7.6.0, port: 2181)
helm/infrastructure/kafka/        (image: confluentinc/cp-kafka:7.6.0, port: 9092)
helm/infrastructure/keycloak/     (image: quay.io/keycloak/keycloak:24.0.3, port: 8090)
helm/infrastructure/jaeger/       (image: jaegertracing/all-in-one:1.57, ports: 16686/4317/4318)
helm/services/doctor-service/     (port: 8083)
helm/services/appointment-service/ (port: 8084)
helm/services/notification-service/ (port: 8085)
helm/services/emr-service/        (port: 8086)
helm/services/analytics-service/  (port: 8087)
helm/gateway/krakend/             (image: devopsfaith/krakend:2.7, port: 8080, type: NodePort)
```

Key differences per service — adjust in values.yaml:
```
zookeeper: no readiness probe change needed, port 2181
kafka:
  env:
    KAFKA_ZOOKEEPER_CONNECT: zookeeper-service:2181
    KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka-service:9092
    KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: "1"
    KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"

keycloak:
  command: ["start-dev", "--import-realm"]
  env:
    KEYCLOAK_ADMIN: admin
    KEYCLOAK_ADMIN_PASSWORD: admin
    KC_HTTP_PORT: "8090"
    KC_HOSTNAME_STRICT: "false"
  volumeMount: keycloak/realm/mediq-realm.json → /opt/keycloak/data/import/

krakend:
  service type: NodePort (external access)
  nodePort: 30080
  volumeMount: krakend/ → /etc/krakend/
```

---

## Step 5: Build and Load Images

### WSL Terminal — do this FIRST before helmfile apply

```bash
# Open WSL terminal
# Navigate to project
cd /mnt/d/codebase/krakend_explore

# Build all service images
docker build -t mediq/user-service:latest ./user-service
docker build -t mediq/doctor-service:latest ./doctor-service
docker build -t mediq/appointment-service:latest ./appointment-service
docker build -t mediq/notification-service:latest ./notification-service
docker build -t mediq/emr-service:latest ./emr-service
docker build -t mediq/analytics-service:latest ./analytics-service

# Load all images into kishan-lab kind cluster
kind load docker-image mediq/user-service:latest --name kishan-lab
kind load docker-image mediq/doctor-service:latest --name kishan-lab
kind load docker-image mediq/appointment-service:latest --name kishan-lab
kind load docker-image mediq/notification-service:latest --name kishan-lab
kind load docker-image mediq/emr-service:latest --name kishan-lab
kind load docker-image mediq/analytics-service:latest --name kishan-lab

# Verify images are loaded
docker exec kishan-lab-control-plane crictl images | grep mediq
```

---

## Step 6: Deploy with Helmfile

### PowerShell — Windows

```powershell
cd D:\codebase\krakend_explore

# Dry run first — see what will be deployed without applying
helmfile -e dev diff

# Deploy everything in order
helmfile -e dev apply

# What this does:
# 1. Deploys namespace
# 2. Deploys infrastructure (postgres, redis, zookeeper, kafka, keycloak, jaeger)
#    → waits for each to be ready before continuing
# 3. Deploys user-service + doctor-service
#    → waits for postgres + redis + kafka + keycloak to be healthy
# 4. Deploys appointment + notification
#    → waits for user-service + doctor-service
# 5. Deploys emr + analytics
# 6. Deploys krakend last
#    → waits for all services + keycloak

# Watch progress in another terminal
kubectl get pods -n mediq -w
```

---

## Step 7: Useful Helmfile Commands

```powershell
# See status of all releases
helmfile -e dev status

# Deploy only infrastructure layer
helmfile -e dev apply -f helmfile.d/01-infrastructure.yaml

# Deploy only one specific service
helmfile -e dev apply -l name=mediq-user-service

# Upgrade a specific service after code change
helmfile -e dev apply -l name=mediq-user-service

# Diff — see what changed before applying
helmfile -e dev diff

# Destroy everything (careful!)
helmfile -e dev destroy

# Render templates locally (debug)
helmfile -e dev template

# Sync only changed releases
helmfile -e dev sync
```

---

## Step 8: Verify Deployment

```powershell
# All pods should be Running
kubectl get pods -n mediq

# Expected output:
# NAME                                    READY   STATUS    RESTARTS
# postgres-xxxxx                          1/1     Running   0
# redis-xxxxx                             1/1     Running   0
# zookeeper-xxxxx                         1/1     Running   0
# kafka-xxxxx                             1/1     Running   0
# keycloak-xxxxx                          1/1     Running   0
# jaeger-xxxxx                            1/1     Running   0
# user-service-xxxxx                      1/1     Running   0
# doctor-service-xxxxx                    1/1     Running   0
# appointment-service-xxxxx               1/1     Running   0
# notification-service-xxxxx              1/1     Running   0
# emr-service-xxxxx                       1/1     Running   0
# analytics-service-xxxxx                 1/1     Running   0
# krakend-xxxxx                           1/1     Running   0

# Check Helmfile release status
helmfile -e dev status

# Test via KrakenD NodePort
# kind extraPortMappings maps nodePort 30080 → host port 8080
curl -X POST http://localhost:8080/api/v1/users/patients/register `
  -H "Content-Type: application/json" `
  -d '{
    "firstName": "Helm",
    "lastName": "Test",
    "dateOfBirth": "1990-01-01",
    "password": "Test@1234",
    "contacts": [
      {"contactType":"EMAIL","contactValue":"helm@test.com","isPrimary":true}
    ]
  }'

# View Jaeger traces
# Open browser: http://localhost:16686
# (kind NodePort for jaeger maps to host 16686)
```

---

## Step 9: Rebuild and Redeploy One Service

When you change code in user-service:

```bash
# WSL — rebuild and reload image
cd /mnt/d/codebase/krakend_explore
docker build -t mediq/user-service:latest ./user-service
kind load docker-image mediq/user-service:latest --name kishan-lab
```

```powershell
# PowerShell — redeploy only user-service
helmfile -e dev apply -l name=mediq-user-service

# Or force pod restart to pick up new image
kubectl rollout restart deployment/user-service -n mediq
kubectl rollout status deployment/user-service -n mediq
```

---

## Commit

```powershell
git add .
git commit -m "feat(helmfile): replace monolith helm chart with helmfile orchestration

- helmfile.yaml root with ordered helmfile.d/ layers
- helmfile.d/00-namespace: namespace first
- helmfile.d/01-infrastructure: postgres->redis->zookeeper->kafka->keycloak->jaeger
- helmfile.d/02-platform: user-service->doctor-service (needs infra)
- helmfile.d/03-core: appointment->notification (needs platform)
- helmfile.d/04-advanced: emr->analytics (needs core)
- helmfile.d/05-gateway: krakend last (needs all services)
- Individual helm charts per service (not monolith)
- environments/dev.yaml: kind-optimised resource sizes (256Mi, 1 replica)
- environments/prod.yaml: production scale placeholder
- Deployment commands: WSL for docker build + kind load, PowerShell for helmfile"
```

---

## What Helmfile Gives You Over plain Helm

```
Plain helm install:
  All 13 releases deployed simultaneously
  Race conditions on startup
  No dependency ordering
  Manual coordination required

Helmfile apply:
  Ordered deployment (infrastructure → platform → core → gateway)
  Each layer waits for previous to be fully healthy
  needs: [] enforced — user-service NEVER starts before kafka is ready
  helmfile -l name=X to target one release
  diff before apply (like terraform plan)
  Environment-specific values (dev vs prod) cleanly separated
  Single command: helmfile -e dev apply → entire platform deployed
```
