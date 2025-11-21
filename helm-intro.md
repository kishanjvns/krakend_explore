# 📦 What is Helm?

Helm is essentially a package manager for Kubernetes, much like apt for Ubuntu or yum for CentOS. It simplifies the process of defining, installing, and upgrading applications on a Kubernetes cluster.

## Key Functions

**Installs Controllers and Third-Party Applications:** Helm allows you to easily deploy complex applications like Prometheus, Grafana, Argo CD, Nginx Ingress Controller, and other common tools on your Kubernetes cluster.

**Manages Application Lifecycle:** Beyond just installation, Helm enables you to:
- Install applications
- Update existing applications to newer versions
- Uninstall applications cleanly
- Package your own organizational applications as reusable Helm charts

## 🤔 Why Use Helm?

Without Helm, managing applications on Kubernetes can become a significant challenge, especially in complex environments.

**Avoids Manual YAML Management:** Instead of dealing with numerous individual YAML files for deployments, services, config maps, etc., Helm bundles them into a single, manageable unit called a "chart".

**Reduces Maintenance Overhead:** It eliminates the need for DevOps/SRE teams to write and maintain custom scripts for installing and upgrading various applications and their different versions across multiple environments.

**Simplifies Version Management:** Helm allows you to easily deploy specific versions of applications and manage upgrades, ensuring consistency across different stages of your development pipeline.

**Enables Customization:** Charts can be parameterized using a values.yaml file, allowing you to deploy the same application with different configurations (e.g., number of replicas, resource limits) for different environments.

## 🛠️ Installing Helm

Helm is a client-side tool—you install it on your local machine, not directly on the Kubernetes cluster. Helm interacts with your Kubernetes cluster using your kubeconfig file's current context, just like kubectl.

### Installation Commands (Examples)

| OS | Command |
|---|---|
| macOS (Homebrew) | `brew install helm` |
| Windows (Chocolatey) | `choco install kubernetes-helm` |
| Linux (Debian/Ubuntu) | (Follow official guide, or use a command sequence like below) |

**Example for Debian/Ubuntu:**

```bash
# Example for Debian/Ubuntu
curl https://baltocdn.com/helm/signing.asc | gpg --dearmor | sudo tee /usr/share/keyrings/helm.gpg > /dev/null
sudo apt-get install apt-transport-https --yes
echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/helm.gpg] https://baltocdn.com/helm/stable/debian/ all main" | sudo tee /etc/apt/sources.list.d/helm-stable-debian.list
sudo apt-get update
sudo apt-get install helm
```

### Verify Installation

```bash
helm version
```

### Check Kubernetes Context

```bash
kubectl config current-context
```

## 🔑 Helm Key Concepts

| Concept | Description | Analogy |
|---|---|---|
| **Helm Repository (Repo)** | A centralized location that hosts Helm charts. Example: Bitnami is a popular public repository. | An App Store or Package Index |
| **Helm Chart** | A package or bundle of an application, containing all the necessary Kubernetes resource definitions (YAML files). | The Application Package/Setup File |
| **Helm Release** | A deployed instance of a Helm chart on a Kubernetes cluster. When you install a chart, you give it a unique name. | The Installed Instance of the App |

## ⚙️ Managing Third-Party Applications with Helm

To install applications from the community or vendors, you use Helm Repositories.

### Add the Repository

```bash
helm repo add [repo-name] [repo-url]

# Example: Adding the Bitnami repository 
helm repo add bitnami https://charts.bitnami.com/bitnami
```

### Update Repositories

```bash
# Recommended to fetch the latest chart metadata
helm repo update
```

### Search for a Chart

```bash
helm search repo [chart-name]

# Example: Searching for Nginx in Bitnami 
helm search repo bitnami nginx
```

### Install a Chart (Create a Release)

```bash
helm install [release-name] [repo-name]/[chart-name]

# Example: Installing Nginx 
helm install nginx-v1 bitnami/nginx

# Example: Installing Prometheus 
helm install prometheus bitnami/prometheus
```

### Manage Releases

**List installed releases:**

```bash
helm list
```

**Uninstall a release (Removes all associated Kubernetes resources):**

```bash
helm uninstall [release-name]

# Example: Uninstalling Nginx 
helm uninstall nginx-v1
```

## 🏗️ Creating and Managing Your Own Helm Charts

For DevOps engineers, packaging your own microservices or organizational apps as Helm charts is critical.

**Scenario:** Packaging 'payments' and 'shipping' microservices.

### Create the Basic Chart Structure

```bash
mkdir -p best-commerce/{payments,shipping}
cd best-commerce

# Create standard chart scaffolding
helm create payments
helm create shipping
```

This command creates a directory structure containing:
- **Chart.yaml:** Metadata for the chart
- **values.yaml:** Default customizable values for the templates
- **templates/:** Directory for Kubernetes YAML manifest files

### Key File Customization (Example: payments chart)

**Chart.yaml (Metadata):**

```yaml
apiVersion: v2
name: payments
description: A Helm chart for the payments microservice
type: application
version: 0.1.0 # Chart version
appVersion: "1.0.0" # Application version
```

**templates/deployment.yaml (The Kubernetes manifest):**

Templates use Go template syntax. The `{{ .Values. }}` syntax is replaced by values from values.yaml.

```yaml
# ... (Snipped for brevity)
spec:
  containers:
    - name: {{ .Release.Name }}
      image: "{{ .Values.image.repository }}:{{ .Values.image.tag }}"
      imagePullPolicy: {{ .Values.image.pullPolicy }}
      command: ["sh", "-c", "echo '{{ .Values.appMessage }}' && sleep 3600"]
# ...
```

**values.yaml (Default configuration):**

```yaml
image:
  repository: busybox
  tag: latest
  pullPolicy: IfNotPresent

appMessage: "I am a payment service"
```

*(Repeat similar steps for the shipping chart, changing appMessage.)*

### Package Your Charts

Navigate back to the parent directory where payments and shipping folders reside:

```bash
cd ..

# Creates payments-0.1.0.tgz and shipping-0.1.0.tgz
helm package payments
helm package shipping
```

### Create a Helm Repository Index

This command generates an index.yaml file, which acts as a catalog for your local Helm repository:

```bash
helm repo index .
```

You would then publish this index.yaml along with the .tgz files to a central location (like Nexus, Artifactory, or GitHub Pages) for others to consume.

## ⚙️ Customizing Chart Installation with --set

You can override any default value defined in a chart's values.yaml file by using the `--set` flag during installation. This is key for environment-specific configurations.

### View Available Values for a Chart

```bash
helm show values [repo-name]/[chart-name]

# Example:
helm show values bitnami/nginx
```

### Install with Custom Values

```bash
helm install [release-name] [repo-name]/[chart-name] --set [key]=[value]

# Example: Setting the number of replicas to 3
helm install my-nginx bitnami/nginx --set replicaCount=3
```

---

**Notes:**
1. Like apt for Ubuntu or yum for CentOS
2. Simplifies the process of defining, installing, and upgrading applications
3. You install it on your local machine, not directly on the Kubernetes cluster
4. Default customizable values for the templates
5. Directory for Kubernetes YAML manifest files
6. This is key for environment-specific configurations
