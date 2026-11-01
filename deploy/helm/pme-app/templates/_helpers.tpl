{{- define "pme.labels" -}}
app.kubernetes.io/name: {{ .name }}
app.kubernetes.io/part-of: pme
app.kubernetes.io/managed-by: Helm
{{- end -}}
