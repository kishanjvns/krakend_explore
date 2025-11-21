1. What is Helm?
Helm is essentially a package manager for Kubernetes, much like apt for Ubuntu or yum for CentOS. It simplifies the process of defining, installing, and upgrading Kubernetes applications.

Installs Kubernetes Controllers and Third-Party Applications: Helm allows you to easily deploy complex applications like Prometheus, Grafana, Argo CD, Nginx Ingress Controller, and other common tools on your Kubernetes cluster.
Manages Application Lifecycle: Beyond just installation, Helm enables you to:
Install applications
Update existing applications to newer versions
Uninstall applications cleanly
Package your own organizational applications as reusable Helm chart
2. Why Use Helm?
Without Helm, managing applications on Kubernetes can become a significant challenge, especially in complex environments.

Avoids Manual YAML Management: Instead of dealing with numerous individual YAML files for deployments, services, config maps, etc., Helm bundles them into a single, manageable unit called a "chart".
Reduces Maintenance Overhead: It eliminates the need for DevOps/SRE teams to write and maintain custom scripts for installing and upgrading various applications and their different versions across multiple environments.
Simplifies Version Management: Helm allows you to easily deploy specific versions of applications and manage upgrades, ensuring consistency across different stages of your development pipeline.
Enables Customization: Charts can be parameterized, allowing you to deploy the same application with different configurations (e.g., number of replicas, resource limits) for different environments.
3. Installing Helm
Helm is a client-side tool, meaning you install it on your local machine, not directly on the Kubernetes cluster (14:20-14:44). Helm interacts with your Kubernetes cluster using your kubeconfig file's current context, just like kubectl.

Installation Commands (examples):

macOS (Homebrew):
bash
brew install helm

Windows (Chocolatey):
bash
choco install kubernetes-helm

Linux (APT/DNF - refer to official docs for specific commands):
bash

Example for Debian/Ubuntu
curl https://baltocdn.com/helm/signing.asc | gpg --dearmor | sudo tee /usr/share/keyrings/helm.gpg > /dev/null
sudo apt-get install apt-transport-https --yes
echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/helm.gpg] https://baltocdn.com/helm/stable/debian/ all main" | sudo tee /etc/apt/sources.list.d/helm-stable-debian.list
sudo apt-get update
sudo apt-get install helm

Verify Installation:
bash
helm version

Check Kubernetes Context (Helm uses this to interact with your cluster):
bash
kubectl config current-context

4. Helm Key Concepts
Helm Repository (Repo): A centralized location that hosts Helm charts.
Example: Bitnami is a popular public repository for many common applications.
Helm Chart: A package or bundle of an application, containing all the necessary Kubernetes resource definitions (YAML files).
Helm Release: A deployed instance of a Helm chart on a Kubernetes cluster. When you install a chart, you give it a release name.
5. Managing Third-Party Applications with Helm
To install third-party applications, you typically follow these steps:

Add the repository:
bash
helm repo add

Example: Adding the Bitnami repository 
bash
helm repo add bitnami https://charts.bitnami.com/bitnami

Update Helm repositories (recommended):
bash
helm repo update

Search for a chart in a repository:
bash
helm search repo  [chart-name]

Example: Searching for Nginx in Bitnami 
bash
helm search repo bitnami nginx

Install a chart:
bash
helm install  /

Example: Installing Nginx 
bash
helm install nginx-v1 bitnami/nginx

Example: Installing Prometheus 
bash
helm install prometheus bitnami/prometheus

List installed releases:
bash
helm list

Uninstall a release:
bash
helm uninstall

Example: Uninstalling Nginx 
bash
helm uninstall nginx-v1

Example: Uninstalling Prometheus 
bash
helm uninstall prometheus

6. Creating and Managing Your Own Helm Charts
This section demonstrates how to package your own applications as Helm charts, which is crucial for DevOps engineers.

Scenario: Create Helm charts for two microservices: payments and shipping.

Create project directories:
bash
mkdir -p best-commerce/{payments,shipping}
cd best-commerce

Create the basic Helm chart structure:
bash
helm create payments
helm create shipping

This command creates a standard Helm chart directory structure, including:

Chart.yaml: Metadata for the chart .
values.yaml: Default customizable values for the templates .
templates/: Directory for Kubernetes YAML manifest files .
Customize Chart Files:

Chart.yaml (payments example):
This file contains metadata about your chart.
yaml
apiVersion: v2
name: payments
description: A Helm chart for the payments microservice
type: application
version: 0.1.0 # Chart version
appVersion: "1.0.0" # Application version

templates/deployment.yaml (payments example) (42:20):
This file defines your Kubernetes Deployment. Variables enclosed in {{ .Values. }} are placeholders that will be replaced by values from values.yaml.

yaml
apiVersion: apps/v1
kind: Deployment
metadata:
name: {{ .Release.Name }}-deployment
labels:
app: {{ .Release.Name }}
spec:
replicas: 1 # Hardcoded for simplicity, can be a variable
selector:
matchLabels:
app: {{ .Release.Name }}
template:
metadata:
labels:
app: {{ .Release.Name }}
spec:
containers:
- name: {{ .Release.Name }}
image: "{{ .Values.image.repository }}:{{ .Values.image.tag }}"
imagePullPolicy: {{ .Values.image.pullPolicy }}
command: ["sh", "-c", "echo '{{ .Values.appMessage }}' && sleep 3600"]

values.yaml (payments example) :
This file provides the default values for the variables used in your templates.

yaml
image:
repository: busybox
tag: latest
pullPolicy: IfNotPresent

appMessage: "I am a payment service"

Repeat similar steps for the shipping chart, changing appMessage in values.yaml to "I am a shipment service".

Package Your Charts:
Navigate back to the best-commerce directory where payments and shipping folders reside.
bash
cd ..
helm package payments
helm package shipping

This command creates a .tgz archive for each chart (e.g., payments-0.1.0.tgz, shipping-0.1.0.tgz), which is the deployable Helm chart .

Create a Helm Repository Index:
This command generates an index.yaml file, which acts as a catalog for your local Helm repository, listing all available charts.
bash
helm repo index .

You can then publish this index.yaml and the .tgz files to a centralized location like Nexus, Artifactory, or GitHub Pages for others to consume.

7. Customizing Chart Installation with --set
When installing a chart, you can override values defined in its values.yaml file using the --set flag. This is useful for environment-specific configurations without modifying the chart directly.

View Available Values for a Chart:
bash
helm show values /

Example:
bash
helm show values bitnami/nginx

Install with Custom Values:
bash
helm install  / --set =

Example: If an Nginx chart had a replicaCount value:
bash
helm install my-nginx bitnami/nginx --set replicaCount=3
