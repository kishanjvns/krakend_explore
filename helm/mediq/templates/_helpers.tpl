{{/*
Common labels for all mediq resources.
Usage: {{ include "mediq.labels" (merge (dict "name" "user-service") .) | nindent 4 }}
*/}}
{{- define "mediq.labels" -}}
app: {{ .name }}
chart: {{ .Chart.Name }}-{{ .Chart.Version }}
release: {{ .Release.Name }}
environment: {{ .Values.global.environment | default "dev" }}
{{- end }}

{{/*
Build a fully-qualified image reference.
Usage: {{ include "mediq.image" (merge (dict "name" "user-service") .) }}
*/}}
{{- define "mediq.image" -}}
{{ .Values.global.imageRegistry }}/{{ .name }}:{{ .Values.global.imageTag }}
{{- end }}
