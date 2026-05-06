#!/bin/bash
CONNECTORS_DIR="$(dirname "$0")/connectors"

declare -A PORTS=(
    ["user-service-connector.json"]="8091"
    ["appointment-service-connector.json"]="8092"
    ["payment-service-connector.json"]="8093"
    ["doctor-service-connector.json"]="8094"
    ["emr-service-connector.json"]="8095"
)

echo "=== Registering Debezium connectors ==="

for file in "${!PORTS[@]}"; do
    PORT="${PORTS[$file]}"
    URL="http://localhost:${PORT}"
    FILEPATH="$CONNECTORS_DIR/$file"
    echo ""
    echo "--- $file (port $PORT) ---"

    until curl -sf "$URL/connectors" > /dev/null 2>&1; do
        echo "  Waiting for Kafka Connect..."
        sleep 5
    done

    NAME=$(python3 -c "import json; print(json.load(open('$FILEPATH'))['name'])")
    EXISTING=$(curl -sf "$URL/connectors/$NAME" 2>/dev/null)

    if [ -n "$EXISTING" ]; then
        CONFIG=$(python3 -c "import json; print(json.dumps(json.load(open('$FILEPATH'))['config']))")
        curl -sf -X PUT -H "Content-Type: application/json" -d "$CONFIG" "$URL/connectors/$NAME/config"
        echo "  Updated: $NAME"
    else
        curl -sf -X POST -H "Content-Type: application/json" -d @"$FILEPATH" "$URL/connectors"
        echo "  Created: $NAME"
    fi
done

echo ""
echo "=== Status ==="
for PORT in 8091 8092 8093 8094 8095; do
    echo "Port $PORT:"
    curl -sf "http://localhost:$PORT/connectors?expand=status" 2>/dev/null | \
      python3 -c "import sys,json; [print(f'  {n}: {i[\"status\"][\"connector\"][\"state\"]}') for n,i in json.load(sys.stdin).items()]" 2>/dev/null || echo "  (not ready)"
done
