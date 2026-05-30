# Dev Infrastructure Guide — mediq on Kind (Windows + Docker Desktop)

## Your Stack at a Glance

```
Windows Host
├── C:\bin\             kubectl, helm, helmfile, k9s, kind
├── C:\bin\load-images.ps1   ← loads all images into Kind
├── Docker Desktop      ← Docker engine + daemon (native Windows)
└── Kind                ← k8s cluster as Docker containers
                            cluster name: mediq
                            nodes: mediq-control-plane, mediq-worker, mediq-worker2
                            port mappings (on control-plane node):
                              30080 → 8080   (KrakenD)
                              30090 → 8090   (Keycloak)
                              30086 → 16686  (Jaeger)
                              30030 → 3000   (Grafana)
```

**Key mental model:** Everything runs natively on Windows. Docker Desktop provides the Docker engine. Kind creates the k8s cluster as Docker containers. kubectl/helm/helmfile talk directly to Kind — no WSL, no IP translation, no kubeconfig export steps.

---

## Full Startup Procedure (from scratch)

Follow this exact order every time you need to bring up the full stack.

### 1. Start Docker Desktop
Open Docker Desktop from the Start menu. Wait until the whale icon in the taskbar shows **"Docker Desktop is running"**. All other steps depend on this.

### 2. Check if Kind cluster exists
```powershell
kind get clusters
```
- If `mediq` is listed → skip to step 4
- If not listed → continue to step 3

### 3. Create the cluster
```powershell
cd D:\codebase\krakend_explore
kind create cluster --config kind-config.yaml
```
Kind automatically sets the kubectl context to `kind-mediq` — no export step needed.

### 4. Verify nodes
```powershell
kubectl get nodes
```
Must show 3 nodes `Ready` before continuing.

### 5. Load images into Kind
```powershell
load-images.ps1
```
This loads all mediq service images + infrastructure images into all 3 Kind nodes. Only needed after creating a fresh cluster or rebuilding images.

### 6. Deploy with helmfile
```powershell
cd D:\codebase\krakend_explore
helmfile -e dev sync
```

Watch progress in a second terminal:
```powershell
kubectl get pods -n mediq-dev -w
```

### 7. Verify access
| Service | URL |
|---------|-----|
| KrakenD | http://localhost:8080 |
| Keycloak Admin | http://localhost:8090 (admin / admin) |
| Jaeger | http://localhost:16686 |
| Prometheus | http://localhost:9090 (ClusterIP or forwarded) |
| Grafana | http://localhost:3000 (admin / mediq) |

---

## After a Windows Reboot

Docker Desktop starts automatically (if configured) or start it manually. The Kind cluster **persists** across reboots — Kind stores state in Docker volumes. You only need to:

```powershell
kubectl get nodes          # confirm cluster is still reachable
kubectl get pods -n mediq-dev   # confirm pods are running
helmfile -e dev sync       # if pods are not running, redeploy
```

---

## Rebuild Image After Code Change and Redeploy

### Full cycle (e.g. user-service changed)
```powershell
# 1. Build JAR (Java services only — skip for frontend)
cd user-service; mvn package -DskipTests; cd ..

# 2. Build Docker image
docker build -t mediq/user-service:latest ./user-service

# 3. Load into Kind
kind load docker-image mediq/user-service:latest --name mediq

# 4. Restart pod (forces Kubernetes to use the new image)
kubectl rollout restart deployment/user-service -n mediq-dev
kubectl rollout status deployment/user-service -n mediq-dev
```

### Multiple services at once
```powershell
$changed = @("user-service", "doctor-service")
foreach ($svc in $changed) {
    mvn package -DskipTests -f "$svc/pom.xml"
    docker build -t "mediq/$svc`:latest" "./$svc"
    kind load docker-image "mediq/$svc`:latest" --name mediq
    kubectl rollout restart "deployment/$svc" -n mediq-dev
}
```

### Why `imagePullPolicy: IfNotPresent` matters
All `values.yaml` files use `IfNotPresent`. Kubernetes only pulls an image if it is not already on the node. Since you loaded it with `kind load`, the node has it and uses it immediately without hitting any registry. Never set `Always` — your images are local only and not pushed to any registry.

---

## Deploying to a New Namespace / Environment

Each environment gets its own namespace with fully isolated infrastructure (postgres, kafka, keycloak all separate).

### How it works
`helmfile.yaml.gotmpl` uses `{{ .Environment.Values.namespace }}` throughout. The value comes from the environment file:

| Command | Reads from | Deploys to namespace |
|---------|------------|----------------------|
| `helmfile -e dev sync` | `environments/dev.yaml` | `mediq-dev` |
| `helmfile -e staging sync` | `environments/staging.yaml` | `mediq-staging` |
| `helmfile -e prod sync` | `environments/prod.yaml` | `mediq-prod` |

### Adding a new environment
1. Create `environments/myenv.yaml` (copy from `dev.yaml`, set `namespace: mediq-myenv`)
2. Add it to the `environments:` block in `helmfile.yaml.gotmpl`
3. Deploy: `helmfile -e myenv sync`

---

## Helmfile Issues Reference

### Issue: `helmfile.yaml` vs `helmfile.d/` conflict
Helmfile auto-discovers both and refuses to run. The fix was to keep only `helmfile.yaml.gotmpl` at the root — never create a `helmfile.d/` directory here.

### Issue: Go templates need `.gotmpl` extension
`helmfile.yaml` containing `{{ }}` expressions will fail with a YAML parse error. The file must be named `helmfile.yaml.gotmpl` so Helmfile processes templates before parsing YAML.

### Issue: `environments` and `releases` in same YAML document
Helmfile v1 requires a `---` separator between the two blocks. The current file already has this — do not remove it.

### Issue: `needs:` on sub-helmfile entries (Helmfile v1)
`needs:` between `helmfiles:` path entries was removed in Helmfile v1. All releases live in a single `helmfile.yaml.gotmpl` with `needs:` on individual releases only.

---

## External Image Issues with Kind

### Problem: multi-platform manifest list
Docker Hub images are often multi-platform (amd64, arm64, etc.). When you `docker pull` without specifying a platform, Docker stores a manifest list referencing all platforms. Kind's containerd tries to import all platforms and fails with:
```
ctr: content digest sha256:...: not found
```

### Fix: always pull external images with `--platform linux/amd64`
```powershell
docker pull --platform linux/amd64 confluentinc/cp-zookeeper:7.6.0
docker pull --platform linux/amd64 confluentinc/cp-kafka:7.6.0
docker pull --platform linux/amd64 quay.io/keycloak/keycloak:24.0.3
docker pull --platform linux/amd64 temporalio/auto-setup:1.24.2
docker pull --platform linux/amd64 temporalio/ui:2.26.2
docker pull --platform linux/amd64 grafana/loki:2.9.6
docker pull --platform linux/amd64 grafana/promtail:2.9.6
docker pull --platform linux/amd64 prom/prometheus:v2.51.0
docker pull --platform linux/amd64 grafana/grafana:10.4.2
```

If you already pulled without `--platform`, `docker rmi` first and re-pull. If the error persists (manifest list cached), use buildx to create a clean single-platform image:
```powershell
docker buildx build --platform linux/amd64 --provenance=false --load -t <image>:<tag> - <<< "FROM <image>:<tag>"
```
`--provenance=false` prevents BuildKit from adding an attestation manifest which also confuses Kind.

### Problem: image timeout during `helmfile sync`
Infrastructure images (Confluent, Temporal, Keycloak) are large (500MB–1GB). If Kind tries to pull them during deploy, the Helm timeout expires. Always pre-load them with `kind load docker-image` before running helmfile.

---

## Helm Chart: namespace must use `.Release.Namespace`

Every Kubernetes resource in a Helm chart must use `{{ .Release.Namespace }}` — never hardcode `namespace: mediq`:

```yaml
# WRONG — breaks environment isolation
metadata:
  namespace: mediq

# CORRECT — Helm injects the right namespace at deploy time
metadata:
  namespace: {{ .Release.Namespace }}
```

If you add a new Helm chart or template, always use `{{ .Release.Namespace }}`.

---

## Docker Desktop: `DOCKER_HOST` env var issue

If `docker` commands fail with:
```
error during connect: Get "http://localhost:2375/_ping": dial tcp: connectex: No connection could be made
```
It means `DOCKER_HOST` is set to an old TCP address. Fix:
```powershell
$env:DOCKER_HOST = ""                                                          # current session
[System.Environment]::SetEnvironmentVariable("DOCKER_HOST", "", "User")       # permanent
```
Docker Desktop uses a named pipe by default — `DOCKER_HOST` must be unset.

---

## Quick Diagnostics Cheatsheet

```powershell
# Cluster reachable?
kubectl get nodes

# Pods running?
kubectl get pods -n mediq-dev

# Why is a pod failing?
kubectl describe pod <pod-name> -n mediq-dev
kubectl logs <pod-name> -n mediq-dev
kubectl logs <pod-name> -n mediq-dev --previous   # crashed container

# Redeploy one release
helmfile -e dev -l name=mediq-user-service sync

# Tear down k8s resources (keeps cluster)
helmfile -e dev destroy

# Delete cluster entirely
kind delete cluster --name mediq
```

---

## k9s Tips

```powershell
k9s --namespace mediq-dev
```

| Key | Action |
|-----|--------|
| `:pods` | List pods |
| `:deployments` | List deployments |
| `l` on a pod | Live logs |
| `d` on a pod | Describe |
| `ctrl+k` on a pod | Delete pod |
| `/keycloak` | Filter by name |
| `esc` | Go back |
