set shell := ["bash", "-euo", "pipefail", "-c"]

port := env("DEWEY_PORT", "8080")
api := "http://localhost:" + port + "/api"

default:
    @just --list

# Regenerate the Kotlin, TypeScript and Go clients and the CLI commands from the server's routes
generate: && format
    ./gradlew :server:generateClients

format:
    ./gradlew ktlintFormat
    gofmt -l -w cli
    cd web && npm run format

build: build-server build-web build-cli

build-server *gradle_args:
    ./gradlew :server:installDist {{ gradle_args }}

build-web:
    cd web && npm run build-routes && npm run build

build-taglib:
    ./gradlew :taglib:buildNativeHost

build-cli:
    cd cli && go build -o ../build/dewey .

[no-cd]
[no-exit-message]
[positional-arguments]
cli *args: build-cli
    @"{{ justfile_directory() }}/build/dewey" "$@"

lint: lint-server lint-web lint-cli

lint-server:
    ./gradlew ktlintCheck

lint-web:
    cd web && npm run build-routes && npm run typecheck && npm run lint

lint-cli:
    cd cli && go vet ./... && go vet -tags e2e ./...

test: test-server test-cli

test-server *gradle_args:
    ./gradlew check {{ gradle_args }}

test-taglib *gradle_args:
    ./gradlew :taglib:test {{ gradle_args }}

test-cli:
    cd cli && go test ./...

# Builds and starts the server, runs the CLI against it and stops it again
test-cli-e2e: build-server run-cli-e2e

# Runs the end-to-end tests against the server as built, like CI does with the one the server workflow uploads
run-cli-e2e: (_with-server "server/build/install/server/bin/server" ("cd cli && DEWEY_E2E_URL=http://localhost:" + port + " go test -tags e2e -count=1 ./e2e/..."))

# Starts the server and the web dev server, seeding a user and a library on the first start
dev: (_with-server "./gradlew :server:run" "cd web && npm run dev")

# Starts the server alone, the arguments go to Gradle
start-server *gradle_args: (_with-server ("./gradlew :server:run " + gradle_args) "wait")

# Runs the command once the server is up and seeded, and stops the server when the command exits
_with-server server command:
    #!/usr/bin/env bash
    set -euo pipefail

    # Job control puts the server in a process group of its own, so stopping it reaches Gradle's children but not
    # just or whatever called it, which kill 0 would take down with it and fail the run
    set -m
    {{ server }} &
    server=$!
    set +m
    trap 'trap - EXIT INT TERM; kill -- -'"$server"' 2>/dev/null || true' EXIT INT TERM
    until nc -z localhost {{ port }} 2>/dev/null; do
      kill -0 "$server" 2>/dev/null || exit 1
      sleep 1
    done

    user='{"username":"test","password":"testtest"}'
    if curl -sf -o /dev/null -H 'Content-Type: application/json' -d "$user" "{{ api }}/auth/register"; then
      token=$(curl -sSf -H 'Content-Type: application/json' -d "$user" "{{ api }}/auth/login" |
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
      }' "{{ api }}/libraries"
    fi

    {{ command }}
