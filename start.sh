#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

trap 'trap - EXIT INT TERM; kill 0' EXIT INT TERM

./gradlew :server:run &
server=$!

until nc -z localhost 8080 2>/dev/null; do
  kill -0 "$server" 2>/dev/null || exit 1
  sleep 1
done

api=http://localhost:8080/api
user='{"username":"test","password":"testtest"}'
if curl -sf -o /dev/null -H 'Content-Type: application/json' -d "$user" "$api/auth/register"; then
  token=$(curl -sSf -H 'Content-Type: application/json' -d "$user" "$api/auth/login" |
    sed 's/.*"accessToken":"\([^"]*\)".*/\1/')
  curl -sSf -o /dev/null -H "Authorization: Bearer $token" -H 'Content-Type: application/json' -d '{
    "name": "Test",
    "icon": null,
    "folders": ["'"$PWD"'/test-resources"],
    "preferEmbeddedMetadata": false,
    "metadataAgents": [{"name": "audible"}],
    "combineMetadataAgentFields": true,
    "fileScanners": [{"name": "AudioTagScanner"}, {"name": "AudioFolderScanner"}],
    "combineFileScannerFields": true,
    "language": "English",
    "region": "US"
  }' "$api/libraries"
fi

(cd web && npm run dev) &

wait
