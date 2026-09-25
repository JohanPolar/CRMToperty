#!/usr/bin/env bash
# Envía cada elemento de data/webhooks.json como un POST /aplicaciones, igual que lo haría el emisor real.
# Uso: scripts/enviar-webhooks.sh [url] [archivo]     Requiere python3 para separar el arreglo JSON.
set -euo pipefail
URL="${1:-http://localhost:8080}"
ARCHIVO="${2:-$(dirname "$0")/../data/webhooks.json}"

python3 -c 'import json,sys; [print(json.dumps(w, ensure_ascii=False)) for w in json.load(open(sys.argv[1], encoding="utf-8"))]' "$ARCHIVO" |
while IFS= read -r webhook; do
  printf '%s' "$webhook" | curl -s -w "  HTTP %{http_code}\n" -H "Content-Type: application/json" --data-binary @- "$URL/aplicaciones"
done
