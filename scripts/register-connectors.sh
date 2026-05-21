#!/bin/sh
set -e

POSTGRES_HOST="postgres"
POSTGRES_PORT="5432"
POSTGRES_USER="debezium"
POSTGRES_PASSWORD="debezium"

register() {
  local connect_url=$1
  local name=$2
  local json=$3

  local status
  status=$(curl -s -o /dev/null -w "%{http_code}" "$connect_url/connectors/$name")
  if [ "$status" = "200" ]; then
    echo "[$name] already registered — skipping."
  else
    echo "[$name] registering..."
    curl -sf -X POST "$connect_url/connectors" \
      -H 'Content-Type: application/json' \
      -d "$json" \
      && echo "[$name] OK." \
      || echo "[$name] WARNING: registration failed — will be retried on next restart."
  fi
}

# ── user-outbox-connector ────────────────────────────────────────────────────
register "http://mediq-user-connect:8083" "user-outbox-connector" '{
  "name": "user-outbox-connector",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "database.hostname": "'"$POSTGRES_HOST"'",
    "database.port": "'"$POSTGRES_PORT"'",
    "database.user": "'"$POSTGRES_USER"'",
    "database.password": "'"$POSTGRES_PASSWORD"'",
    "database.dbname": "mediq_users",
    "topic.prefix": "dbz.users",
    "plugin.name": "pgoutput",
    "slot.name": "debezium_users_slot",
    "publication.name": "debezium_users_pub",
    "table.include.list": "mediq_users.user_outbox",
    "snapshot.mode": "initial",
    "transforms": "outbox",
    "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
    "transforms.outbox.table.field.event.id": "id",
    "transforms.outbox.table.field.event.key": "aggregate_id",
    "transforms.outbox.table.field.event.type": "event_type",
    "transforms.outbox.table.field.event.payload": "payload",
    "transforms.outbox.route.by.field": "destination",
    "transforms.outbox.route.topic.replacement": "${routedByValue}",
    "key.converter": "org.apache.kafka.connect.storage.StringConverter",
    "value.converter": "org.apache.kafka.connect.json.JsonConverter",
    "value.converter.schemas.enable": "false"
  }
}'

# ── appointment-outbox-connector ─────────────────────────────────────────────
register "http://mediq-appointment-connect:8083" "appointment-outbox-connector" '{
  "name": "appointment-outbox-connector",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "database.hostname": "'"$POSTGRES_HOST"'",
    "database.port": "'"$POSTGRES_PORT"'",
    "database.user": "'"$POSTGRES_USER"'",
    "database.password": "'"$POSTGRES_PASSWORD"'",
    "database.dbname": "mediq_appointments",
    "topic.prefix": "dbz.appointments",
    "plugin.name": "pgoutput",
    "slot.name": "debezium_appointments_slot",
    "publication.name": "debezium_appointments_pub",
    "table.include.list": "mediq_appointments.appointment_outbox",
    "snapshot.mode": "initial",
    "transforms": "outbox",
    "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
    "transforms.outbox.table.field.event.id": "id",
    "transforms.outbox.table.field.event.key": "aggregate_id",
    "transforms.outbox.table.field.event.type": "event_type",
    "transforms.outbox.table.field.event.payload": "payload",
    "transforms.outbox.route.by.field": "destination",
    "transforms.outbox.route.topic.replacement": "${routedByValue}",
    "key.converter": "org.apache.kafka.connect.storage.StringConverter",
    "value.converter": "org.apache.kafka.connect.json.JsonConverter",
    "value.converter.schemas.enable": "false"
  }
}'

# ── doctor-outbox-connector ───────────────────────────────────────────────────
register "http://mediq-doctor-connect:8083" "doctor-outbox-connector" '{
  "name": "doctor-outbox-connector",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "database.hostname": "'"$POSTGRES_HOST"'",
    "database.port": "'"$POSTGRES_PORT"'",
    "database.user": "'"$POSTGRES_USER"'",
    "database.password": "'"$POSTGRES_PASSWORD"'",
    "database.dbname": "mediq_doctors",
    "topic.prefix": "dbz.doctors",
    "plugin.name": "pgoutput",
    "slot.name": "debezium_doctors_slot",
    "publication.name": "debezium_doctors_pub",
    "table.include.list": "mediq_doctors.service_outbox",
    "snapshot.mode": "initial",
    "transforms": "outbox",
    "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
    "transforms.outbox.table.field.event.id": "id",
    "transforms.outbox.table.field.event.key": "aggregate_id",
    "transforms.outbox.table.field.event.type": "event_type",
    "transforms.outbox.table.field.event.payload": "payload",
    "transforms.outbox.route.by.field": "destination",
    "transforms.outbox.route.topic.replacement": "${routedByValue}",
    "key.converter": "org.apache.kafka.connect.storage.StringConverter",
    "value.converter": "org.apache.kafka.connect.json.JsonConverter",
    "value.converter.schemas.enable": "false"
  }
}'

# ── payment-outbox-connector ──────────────────────────────────────────────────
register "http://mediq-payment-connect:8083" "payment-outbox-connector" '{
  "name": "payment-outbox-connector",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "database.hostname": "'"$POSTGRES_HOST"'",
    "database.port": "'"$POSTGRES_PORT"'",
    "database.user": "'"$POSTGRES_USER"'",
    "database.password": "'"$POSTGRES_PASSWORD"'",
    "database.dbname": "mediq_payments",
    "topic.prefix": "dbz.payments",
    "plugin.name": "pgoutput",
    "slot.name": "debezium_payments_slot",
    "publication.name": "debezium_payments_pub",
    "table.include.list": "mediq_payments.service_outbox",
    "snapshot.mode": "initial",
    "transforms": "outbox",
    "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
    "transforms.outbox.table.field.event.id": "id",
    "transforms.outbox.table.field.event.key": "aggregate_id",
    "transforms.outbox.table.field.event.type": "event_type",
    "transforms.outbox.table.field.event.payload": "payload",
    "transforms.outbox.route.by.field": "destination",
    "transforms.outbox.route.topic.replacement": "${routedByValue}",
    "key.converter": "org.apache.kafka.connect.storage.StringConverter",
    "value.converter": "org.apache.kafka.connect.json.JsonConverter",
    "value.converter.schemas.enable": "false"
  }
}'

echo "All connectors processed."
