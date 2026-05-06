# mediq — Helmfile Bug Fixes

## Branch
```powershell
git checkout feature/mediq-helmfile
# Apply all fixes on same branch — no new branch needed
```

## Bugs Found

```
Bug 1 (CRITICAL): Keycloak chart has no ConfigMap template
  keycloak deployment references: configmap/keycloak-realm
  That ConfigMap is never created
  Keycloak pod will fail: "configmap keycloak-realm not found"
  mediq-realm.json needs to be mounted as a ConfigMap

Bug 2 (CRITICAL): KrakenD chart has no ConfigMap template
  krakend deployment references: configmap/krakend-config
  That ConfigMap is never created
  KrakenD pod will fail: "configmap krakend-config not found"
  All krakend/ directory files need to be mounted as ConfigMaps

Bug 3 (CRITICAL): Postgres only creates mediq_users database
  postgres chart only sets POSTGRES_DB=mediq_users
  6 services need 6 separate databases:
    mediq_users, mediq_doctors, mediq_appointments,
    mediq_notifications, mediq_emr, mediq_analytics
  All services except user-service will fail to connect to DB
```

---

## Fix 1 — Keycloak ConfigMap

### Understanding the fix

```
Keycloak needs mediq-realm.json to auto-import the realm on startup.
In docker-compose this was a volume mount from ./keycloak/realm/
In Kubernetes this must be a ConfigMap.

Flow:
  mediq-realm.json → ConfigMap (keycloak-realm) → mounted at /opt/keycloak/data/import/
  Keycloak starts → reads /opt/keycloak/data/import/mediq-realm.json → imports realm
```

### Add ConfigMap template to keycloak chart

Create `helm/infrastructure/keycloak/templates/configmap.yaml`:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: keycloak-realm
  namespace: mediq
data:
  mediq-realm.json: |
    {{- .Files.Get "realm/mediq-realm.json" | nindent 4 }}
```

### Copy realm file into the chart

```powershell
# PowerShell — copy mediq-realm.json into the keycloak chart folder
# Helm can only access files INSIDE the chart directory via .Files.Get

New-Item -ItemType Directory -Force -Path helm\infrastructure\keycloak\realm

Copy-Item keycloak\realm\mediq-realm.json `
          helm\infrastructure\keycloak\realm\mediq-realm.json
```

Verify:
```powershell
ls helm\infrastructure\keycloak\realm\
# Expected: mediq-realm.json
```

### Why .Files.Get instead of hardcoding

```
Option A — hardcode JSON in YAML (wrong):
  ConfigMap YAML with entire mediq-realm.json inlined = unmaintainable
  Every realm change requires editing the template

Option B — .Files.Get (correct):
  Helm reads the file at chart render time
  mediq-realm.json stays in its own file
  Easy to update — just change the source file and redeploy
```

---

## Fix 2 — KrakenD ConfigMap

### Understanding the fix

```
KrakenD needs its entire config directory mounted:
  krakend/krakend.tmpl
  krakend/krakend.json
  krakend/partials/*.tmpl
  krakend/settings/hosts.json

In docker-compose: volume mount of ./krakend/ directory
In Kubernetes: ConfigMap with all files, mounted at /etc/krakend/

Problem: ConfigMap cannot mount nested directories cleanly.
Solution: Separate ConfigMaps per subdirectory, each mounted at correct path.
```

### Add ConfigMap templates to krakend chart

Create `helm/gateway/krakend/templates/configmap.yaml`:

```yaml
# Main KrakenD config files
apiVersion: v1
kind: ConfigMap
metadata:
  name: krakend-config
  namespace: mediq
data:
  krakend.tmpl: |
    {{- .Files.Get "config/krakend.tmpl" | nindent 4 }}
  krakend.json: |
    {{- .Files.Get "config/krakend.json" | nindent 4 }}
---
# KrakenD partials
apiVersion: v1
kind: ConfigMap
metadata:
  name: krakend-partials
  namespace: mediq
data:
  auth_doctor_admin.tmpl: |
    {{- .Files.Get "config/partials/auth_doctor_admin.tmpl" | nindent 4 }}
  auth_doctor_nurse_admin.tmpl: |
    {{- .Files.Get "config/partials/auth_doctor_nurse_admin.tmpl" | nindent 4 }}
  circuit_breaker.tmpl: |
    {{- .Files.Get "config/partials/circuit_breaker.tmpl" | nindent 4 }}
  endpoint_patients.tmpl: |
    {{- .Files.Get "config/partials/endpoint_patients.tmpl" | nindent 4 }}
  endpoint_referrals.tmpl: |
    {{- .Files.Get "config/partials/endpoint_referrals.tmpl" | nindent 4 }}
  endpoint_doctors.tmpl: |
    {{- .Files.Get "config/partials/endpoint_doctors.tmpl" | nindent 4 }}
  endpoint_appointments.tmpl: |
    {{- .Files.Get "config/partials/endpoint_appointments.tmpl" | nindent 4 }}
  rate_limit_proxy.tmpl: |
    {{- .Files.Get "config/partials/rate_limit_proxy.tmpl" | nindent 4 }}
---
# KrakenD settings
apiVersion: v1
kind: ConfigMap
metadata:
  name: krakend-settings
  namespace: mediq
data:
  hosts.json: |
    {{- .Files.Get "config/settings/hosts.json" | nindent 4 }}
```

### Copy krakend config into the chart

```powershell
# PowerShell — copy krakend config into chart folder
# Helm .Files.Get only reads files inside the chart directory

New-Item -ItemType Directory -Force -Path helm\gateway\krakend\config\partials
New-Item -ItemType Directory -Force -Path helm\gateway\krakend\config\settings

# Copy main files
Copy-Item krakend\krakend.tmpl   helm\gateway\krakend\config\krakend.tmpl
Copy-Item krakend\krakend.json   helm\gateway\krakend\config\krakend.json

# Copy partials
Copy-Item krakend\partials\*     helm\gateway\krakend\config\partials\

# Copy settings
Copy-Item krakend\settings\*     helm\gateway\krakend\config\settings\
```

Verify:
```powershell
ls helm\gateway\krakend\config\
# Expected: krakend.tmpl, krakend.json, partials/, settings/

ls helm\gateway\krakend\config\partials\
# Expected: all .tmpl files

ls helm\gateway\krakend\config\settings\
# Expected: hosts.json
```

### Update krakend deployment to mount all 3 ConfigMaps

Replace `helm/gateway/krakend/templates/deployment.yaml` volumeMounts and volumes section:

Find:
```yaml
          volumeMounts:
            - name: krakend-config
              mountPath: /etc/krakend
              readOnly: true
      volumes:
        - name: krakend-config
          configMap:
            name: {{ .Values.configMapName }}
```

Replace with:
```yaml
          volumeMounts:
            - name: krakend-config
              mountPath: /etc/krakend
              readOnly: true
            - name: krakend-partials
              mountPath: /etc/krakend/partials
              readOnly: true
            - name: krakend-settings
              mountPath: /etc/krakend/settings
              readOnly: true
      volumes:
        - name: krakend-config
          configMap:
            name: krakend-config
        - name: krakend-partials
          configMap:
            name: krakend-partials
        - name: krakend-settings
          configMap:
            name: krakend-settings
```

---

## Fix 3 — PostgreSQL Multiple Databases

### Understanding the fix

```
Each service needs its own database:
  user-service        → mediq_users
  doctor-service      → mediq_doctors
  appointment-service → mediq_appointments
  notification-service → mediq_notifications
  emr-service         → mediq_emr
  analytics-service   → mediq_analytics

PostgreSQL only auto-creates the ONE database set in POSTGRES_DB.
Additional databases need an init script.

PostgreSQL Docker image convention:
  Any .sql file in /docker-entrypoint-initdb.d/ runs on first startup
  We mount an init script via ConfigMap to create all 6 databases
```

### Add init script ConfigMap to postgres chart

Create `helm/infrastructure/postgres/templates/configmap.yaml`:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: postgres-init
  namespace: mediq
data:
  init.sql: |
    -- Create all mediq databases
    -- mediq_users is already created by POSTGRES_DB env var
    -- Create the rest here

    CREATE DATABASE mediq_doctors;
    GRANT ALL PRIVILEGES ON DATABASE mediq_doctors TO mediq;

    CREATE DATABASE mediq_appointments;
    GRANT ALL PRIVILEGES ON DATABASE mediq_appointments TO mediq;

    CREATE DATABASE mediq_notifications;
    GRANT ALL PRIVILEGES ON DATABASE mediq_notifications TO mediq;

    CREATE DATABASE mediq_emr;
    GRANT ALL PRIVILEGES ON DATABASE mediq_emr TO mediq;

    CREATE DATABASE mediq_analytics;
    GRANT ALL PRIVILEGES ON DATABASE mediq_analytics TO mediq;
```

### Update postgres deployment to mount init script

In `helm/infrastructure/postgres/templates/postgres.yaml`:

Find the `volumeMounts` section in the postgres container:
```yaml
          volumeMounts:
            - name: data
              mountPath: /var/lib/postgresql/data
```

Replace with:
```yaml
          volumeMounts:
            - name: data
              mountPath: /var/lib/postgresql/data
            - name: init-script
              mountPath: /docker-entrypoint-initdb.d
              readOnly: true
```

Find the `volumes` section:
```yaml
      volumes:
        - name: data
          persistentVolumeClaim:
            claimName: postgres-pvc
```

Replace with:
```yaml
      volumes:
        - name: data
          persistentVolumeClaim:
            claimName: postgres-pvc
        - name: init-script
          configMap:
            name: postgres-init
```

### Important note on postgres init script

```
The init script in /docker-entrypoint-initdb.d/ ONLY runs on
FIRST STARTUP when the data directory is empty.

If postgres is already running and has data:
  → init script will NOT run again
  → databases already exist or need manual creation

For a fresh kind cluster with new PVC:
  → init script runs automatically on first pod start ✅

If you already have postgres running in the cluster:
  → Delete the PVC to force fresh init:
  kubectl delete pvc postgres-pvc -n mediq
  → Then redeploy postgres
```

---

## Verification Steps After Fixes

### 1. Render templates locally — check for errors
```powershell
# Render keycloak chart and check ConfigMap appears
helm template mediq-keycloak ./helm/infrastructure/keycloak | grep -A5 "kind: ConfigMap"

# Render krakend chart and check all 3 ConfigMaps appear
helm template mediq-krakend ./helm/gateway/krakend | grep "kind: ConfigMap"

# Render postgres chart and check init ConfigMap appears
helm template mediq-postgres ./helm/infrastructure/postgres | grep -A5 "kind: ConfigMap"
```

Expected output for each:
```
kind: ConfigMap
```

### 2. Run helmfile diff — preview before applying
```powershell
helmfile -e dev diff
# Should show all resources without errors
# If you see "file not found" errors → check .Files.Get paths
```

### 3. Build images
```powershell
# From D:\codebase\krakend_explore
docker build -t mediq/user-service:latest ./user-service
docker build -t mediq/doctor-service:latest ./doctor-service
docker build -t mediq/appointment-service:latest ./appointment-service
docker build -t mediq/notification-service:latest ./notification-service
docker build -t mediq/emr-service:latest ./emr-service
docker build -t mediq/analytics-service:latest ./analytics-service
```

### 4. Load images to kind cluster
```bash
# WSL terminal — kind CLI is only in WSL
kind load docker-image mediq/user-service:latest --name kishan-lab
kind load docker-image mediq/doctor-service:latest --name kishan-lab
kind load docker-image mediq/appointment-service:latest --name kishan-lab
kind load docker-image mediq/notification-service:latest --name kishan-lab
kind load docker-image mediq/emr-service:latest --name kishan-lab
kind load docker-image mediq/analytics-service:latest --name kishan-lab
```

Verify images loaded:
```bash
# WSL
docker exec kishan-lab-control-plane crictl images | grep mediq
# Expected: all 6 mediq/* images listed
```

### 5. Deploy with helmfile
```powershell
# PowerShell
helmfile -e dev apply

# Watch pods come up in order
kubectl get pods -n mediq -w
```

### 6. Verify databases were created
```powershell
# Connect to postgres pod
kubectl exec -it deploy/postgres -n mediq -- psql -U mediq -c "\l"

# Expected databases listed:
# mediq_users
# mediq_doctors
# mediq_appointments
# mediq_notifications
# mediq_emr
# mediq_analytics
```

### 7. Verify Keycloak imported realm
```powershell
# Check keycloak logs for realm import
kubectl logs deploy/keycloak -n mediq | grep -i "import\|realm\|mediq"
# Expected: "Realm mediq imported"
```

### 8. Verify KrakenD config loaded
```powershell
# Check krakend logs
kubectl logs deploy/krakend -n mediq | grep -i "config\|error\|start"
# Expected: no "file not found" errors, "starting KrakenD" message
```

### 9. End-to-end test
```powershell
# KrakenD is NodePort 30080 mapped to host 8080 via kind config
curl -X POST http://localhost:8080/api/v1/users/patients/register `
  -H "Content-Type: application/json" `
  -d '{
    "firstName": "Helm",
    "lastName": "Test",
    "dateOfBirth": "1990-01-01",
    "password": "Test@1234",
    "contacts": [
      {"contactType": "EMAIL", "contactValue": "helm@mediq.com", "isPrimary": true}
    ]
  }'
# Expected: HTTP 201 with UserResponse JSON
```

---

## Summary

```
Bug 1 — Keycloak ConfigMap:
  Created: helm/infrastructure/keycloak/templates/configmap.yaml
  Copied:  keycloak/realm/mediq-realm.json → helm/infrastructure/keycloak/realm/
  Result:  Keycloak can import mediq realm on startup ✅

Bug 2 — KrakenD ConfigMap:
  Created: helm/gateway/krakend/templates/configmap.yaml (3 ConfigMaps)
  Copied:  krakend/ → helm/gateway/krakend/config/
  Updated: deployment.yaml to mount all 3 ConfigMaps
  Result:  KrakenD has full config at /etc/krakend/ ✅

Bug 3 — Postgres multiple databases:
  Created: helm/infrastructure/postgres/templates/configmap.yaml
  Updated: postgres deployment to mount init script
  Result:  All 6 databases created on first startup ✅
```

---

## Commit
```powershell
git add .
git commit -m "fix(helmfile): keycloak ConfigMap, krakend ConfigMap, postgres multi-DB init

- Bug 1: Add keycloak-realm ConfigMap from mediq-realm.json
  Keycloak can now import mediq realm on startup

- Bug 2: Add krakend-config/partials/settings ConfigMaps
  KrakenD now has full config mounted at /etc/krakend/
  3 separate ConfigMaps for main config, partials, settings

- Bug 3: Add postgres init script ConfigMap
  Creates all 6 databases on first startup:
  mediq_users, mediq_doctors, mediq_appointments,
  mediq_notifications, mediq_emr, mediq_analytics"
```
