# SmartVolt System Architecture

A detailed explanation of the SmartVolt architecture, why each decision was made, and how every component fits together. Written so you can study the codebase and speak to the design choices in depth.

---

## 1. The Problem

You have a Chevy Volt that charges from a standard 120V wall outlet (Level 1 charging). You want to:

- Monitor real-time power consumption (wattage, voltage, amperage, total kWh)
- Schedule charging during off-peak TOU rate windows to save money
- Detect and log charging sessions automatically
- Control the outlet remotely (on/off)
- Archive raw telemetry for analysis

The hardware constraint that shapes the entire architecture: the smart plug is an **ESP8285** (ESP8266 family) with **80KB of RAM**. It cannot perform TLS handshakes — BearSSL needs ~20-30KB of dynamic RAM for the handshake alone, and with the WiFi stack and ESPHome runtime already loaded, there isn't enough headroom. This was confirmed by testing: TLS connections fail silently or crash the device.

---

## 2. Why Edge + Cloud (Not a Monolith)

The original SmartVolt was a single Spring Boot app on Linode that connected directly to a Mosquitto broker also on Linode. The plug connected to that broker over plaintext MQTT on port 1883 (exposed to the internet alongside the TLS port 8883).

This had two problems:

1. **Security**: Plaintext MQTT over the internet means credentials and telemetry are sent in cleartext. The ESP8266 can't do TLS, so there's no fix at the device level.
2. **Portfolio gap**: A single Spring Boot app with MQTT doesn't demonstrate data pipeline skills, edge computing, offline resilience, or S3 archival — all things the target role (EnergyHub Battery Team) values.

The solution: split into **edge** (laptop on the same LAN as the plug) and **cloud** (Linode VPS).

- The plug talks plaintext MQTT to a local Mosquitto broker on the laptop. Since they're on the same LAN, plaintext is acceptable — the traffic never leaves the local network.
- The edge service validates, quality-checks, buffers, and batches readings, then forwards them to the cloud over HTTPS.
- The cloud service handles structured storage, archival, session detection, command queuing, and the dashboard.

This mirrors real-world energy platform architectures (like EnergyHub's) where field devices talk to a local gateway that aggregates and forwards to a cloud platform.

---

## 3. The Device: KAUF PLF10

### Hardware

The KAUF PLF10 is a consumer smart plug with an ESP8285 chip and an HLW8012 power monitoring IC. It ships pre-flashed with ESPHome (the KAUF open-source firmware), so you don't need to physically open the plug or solder anything — you flash it over WiFi.

### Firmware (ESPHome)

ESPHome is a YAML-based firmware framework for ESP8266/ESP32 devices. The KAUF PLF10 config (`esphome/kauf-plf10.yaml`) does three things:

1. **Publishes bundled telemetry every 10 seconds.** An `interval` block fires a `mqtt.publish_json` lambda that reads the HLW8012 sensor values and bundles them into a single JSON payload with the device key injected:

   ```json
   {"key":"sk-kauf01-xxxx","wattage":1140.5,"voltage":121.3,"amperage":9.4,"totalKwh":0.523}
   ```

   Why bundled? Tasmota (the previous firmware) published each sensor as a separate MQTT message. ESPHome's `mqtt.publish_json` lets us bundle them into one authenticated payload, reducing MQTT overhead and ensuring atomicity.

2. **Accepts power commands.** The relay switch is extended with a `command_topic` so the edge service can publish `ON` or `OFF` to `smartvolt/devices/kauf-01/cmnd/Power`.

3. **Publishes birth/will messages.** When the plug comes online, it publishes `Online` to `smartvolt/devices/kauf-01/tele/LWT`. If it disconnects unexpectedly, the broker publishes the will message `Offline`.

### Why ESPHome over Tasmota

The previous plug was a Sonoff S31 running Tasmota. It broke during disassembly (stripped screws). The KAUF PLF10 ships with ESPHome pre-installed, and ESPHome's `packages` system lets us inherit the KAUF hardware config while customizing only the MQTT integration. The tradeoff: ESPHome configs are YAML-based and require the ESPHome CLI to flash, while Tasmota has a web UI.

### Naming Convention

The codebase uses `wattage`, `voltage`, `amperage`, and `totalKwh` consistently — never `power`/`watts` or `current`. This is a deliberate choice to avoid ambiguity (`current` could mean electrical current or "the current value") and to match the `-age` suffix pattern used in electrical engineering.

---

## 4. The Edge Service

**Module:** `edge/` — Spring Boot app running on the Ubuntu laptop.

### MQTT Integration (`edge/mqtt/`)

`EdgeMqttConfig` sets up a Spring Integration MQTT inbound channel adapter that subscribes to `smartvolt/devices/+/tele/SENSOR` and `smartvolt/devices/+/tele/LWT` on the local Mosquitto broker.

Why Spring Integration MQTT instead of raw Eclipse Paho? Spring Integration provides:
- Automatic reconnection with configurable backoff
- Message channel abstraction (decouples MQTT from business logic)
- Thread-safe message handling
- Clean shutdown

The connection is plaintext (`tcp://localhost:1883`). No TLS needed — the broker is on the same machine.

### Message Handling (`TelemetryHandler`)

When a message arrives on the SENSOR topic, the handler:

1. **Extracts the device ID** from the topic path (`smartvolt/devices/{deviceId}/tele/SENSOR`)
2. **Deserializes** the JSON payload to `TelemetryPayload`
3. **Validates the device key** by hashing the incoming `key` field with SHA-256 and comparing it against a stored hash using `MessageDigest.isEqual()` (constant-time comparison to prevent timing attacks)
4. **Runs data quality validation** using `DataQualityValidator` from the shared module — checks for out-of-range values, null fields, kWh rollbacks, and wattage spikes
5. **Writes to the H2 buffer** as a `BufferedReading` entity

If the key is invalid or the JSON is malformed, the reading is silently dropped (logged at WARN level). This is intentional — an MQTT handler shouldn't throw exceptions back to the broker.

### H2 Buffer (`edge/buffer/`)

`BufferedReading` is a JPA entity stored in an H2 file-mode database (`jdbc:h2:file:./data/edge-buffer`). Each reading has a `synced` boolean that starts as `false`.

Why H2 file-mode? It survives process restarts (unlike in-memory). If the laptop loses internet, readings accumulate in H2. When the connection is restored, the `BatchUploader` drains the buffer. This offline resilience is a key selling point of the edge architecture.

Why H2 over SQLite? H2 integrates natively with Spring Data JPA — same annotations, same repository pattern, zero additional dependencies. SQLite would require a separate JDBC driver and doesn't support Spring's auto-DDL.

### Batch Uploading (`BatchUploader`)

A `@Scheduled(fixedRate = 30000)` method that:

1. Queries up to 100 unsynced readings from H2
2. Builds a `TelemetryBatchRequest` with the readings
3. POSTs it to `https://smartvolt.drewmeyers.com/api/telemetry/batch` via Spring's `RestClient`
4. On success (2xx), marks all readings as `synced = true`
5. On failure, logs a warning and retries next cycle (30 seconds later)

The 30-second batch interval is a balance between latency (readings are at most 30s behind) and efficiency (one HTTP request per batch instead of one per reading). With 10-second telemetry intervals, each batch contains ~3 readings.

### Command Polling (`edge/command/`)

`CommandPoller` runs every 5 seconds (`@Scheduled(fixedRate = 5000)`) and:

1. GETs `https://smartvolt.drewmeyers.com/api/commands/pending?deviceId=kauf-01`
2. For each pending command, publishes the appropriate message to local MQTT (`ON` or `OFF` to `smartvolt/devices/kauf-01/cmnd/Power`)
3. POSTs an acknowledgment back to the cloud

Why polling instead of WebSocket? Simpler, firewall-friendly, and sufficient for the use case. A user clicks a button and the plug responds within 5 seconds. WebSocket would add complexity (connection management, reconnection, heartbeats) with no meaningful benefit for a 5-second latency budget.

### Cloud Client Config (`CloudClientConfig`)

Configures a `RestClient` bean with the cloud base URL and API key header pre-set. Both the `BatchUploader` and `CommandPoller` share this client.

---

## 5. The Cloud Service

**Module:** `cloud/` — Spring Boot app running on Linode.

### Batch Ingest (`cloud/ingest/`)

`TelemetryIngestController` exposes `POST /api/telemetry/batch`. It:

1. Validates that the device is registered
2. Persists each reading to PostgreSQL as a `TelemetryReading` entity
3. Fires S3 archival (async, non-blocking)
4. Feeds the charging session detector (sync)
5. Returns a `TelemetryBatchResponse` with accepted/rejected counts

### S3 Archival (`S3ArchivalService`)

After every batch ingest, the raw JSON is written to Linode Object Storage (S3-compatible API) under the key pattern:

```
raw/{deviceId}/{year}/{month}/{day}/{hour}-{batchId}.json
```

This runs asynchronously (`@Async`) so it doesn't slow down the ingest response. If S3 is unreachable, the failure is logged but doesn't affect PostgreSQL persistence — S3 is a supplementary archive, not the source of truth.

Why S3? It demonstrates the data lake pattern: structured data in PostgreSQL for queries, raw JSON in S3 for replay/audit. The S3-compatible API means the code works with AWS S3, Linode Object Storage, MinIO, or any S3 clone.

The archival is disabled by default (`smartvolt.s3.enabled=false`) so the app runs locally without configuring S3.

### Command Queue (`cloud/command/`)

The `Command` entity models the lifecycle of a remote power command:

```
PENDING → DELIVERED → ACKNOWLEDGED
                    └→ EXPIRED (after 5 minutes)
```

`CommandController` exposes three endpoints:
- `GET /api/commands/pending?deviceId=...` — returns unacknowledged commands
- `POST /api/commands/{id}/ack` — marks as acknowledged
- (Commands are created by `DeviceController.setPower()` or `ChargingScheduler`)

A `@Scheduled(fixedRate = 60000)` method in `CommandService` expires stale commands that have been pending for over 5 minutes. This prevents a backlog of undeliverable commands if the edge is offline.

Why a queue instead of direct MQTT? The cloud no longer connects to MQTT — that's the edge's job. The command queue decouples command creation (dashboard/scheduler) from command delivery (edge polling). It also provides an audit trail: you can query the `commands` table to see what was sent, when, and whether it was acknowledged.

### Charging Session Detection (`cloud/session/`)

`ChargingSessionDetector` is a per-device state machine:

- **IDLE → CHARGING**: When wattage exceeds 100W for 3 consecutive readings (30 seconds). This threshold is above standby power but below minimum EV charging draw.
- **CHARGING → IDLE**: When wattage drops below 50W for 6 consecutive readings (60 seconds). The asymmetric threshold (100W to start, 50W to stop) with a longer cooldown prevents flapping from momentary power fluctuations.

The detector maintains in-memory state (`ConcurrentHashMap<String, DeviceState>`) for consecutive reading counts, and persists session start/end to PostgreSQL. The `ChargingSession` entity tracks start/end timestamps, start/end kWh readings (for calculating energy consumed), estimated cost, and session status.

### TOU Rate Scheduler (`cloud/scheduler/`)

`ChargingScheduler` uses Spring's `@Scheduled` with cron expressions to create power commands:
- **10:00 PM**: Creates a `POWER_ON` command (off-peak starts)
- **8:00 AM**: Creates a `POWER_OFF` command (on-peak starts)

This is a simplified version — a production system would pull rate schedules from a utility API. But it demonstrates the pattern: the scheduler creates commands, the edge delivers them.

### Security (`cloud/security/`)

Three Spring Security filter chains, ordered by priority:

1. **API chain** (`/api/**`, Order 1): Stateless, CSRF disabled, requires `X-API-Key` header. The `ApiKeyFilter` compares the header against the configured key using `MessageDigest.isEqual()` (constant-time) to prevent timing attacks.

2. **Dashboard chain** (`/dashboard/**`, `/login`, `/logout`, `/css/**`, `/js/**`, Order 2): Session-based, form login. Uses an in-memory `UserDetailsService` with a BCrypt-hashed password. Static assets and the login page are permitted without auth.

3. **Default chain** (everything else, Order 3): Permits `/actuator/health` for Docker healthchecks, denies all other requests.

Why separate chains? The edge-to-cloud API is machine-to-machine (API key, no cookies, no CSRF). The dashboard is human-facing (session cookies, CSRF protection, form login). Mixing them in one filter chain would require awkward conditional logic.

---

## 6. The Shared Module

**Module:** `shared/` — Plain JAR (no Spring Boot plugin), depended on by both edge and cloud.

### DTOs

- `TelemetryPayload`: The flat JSON structure the device publishes (key, wattage, voltage, amperage, totalKwh)
- `TelemetryBatchRequest`: Wrapper with deviceId, edgeApiKey, and a list of `Reading` objects (each with timestamp, sensor values, and quality flags)
- `TelemetryBatchResponse`: accepted/rejected counts
- `CommandDto`: Serializable command for the edge (id, deviceId, commandType, payload, expiresAt)
- `CommandAckRequest`: Edge sends this back to acknowledge a command

These DTOs are the **contract** between edge and cloud. Both modules depend on shared, so if a field changes, it breaks compilation in both places. This prevents drift.

### Data Quality Validation

`DataQualityValidator` checks each reading against physical constraints:

| Flag | Rule | Why |
|---|---|---|
| `OUT_OF_RANGE` | wattage 0-2000W, voltage 100-135V, amperage 0-20A | The KAUF PLF10 is a 15A/120V outlet; readings outside these ranges indicate sensor errors |
| `MISSING_FIELD` | Any null sensor value | The HLW8012 occasionally reports nulls during calibration |
| `TIMESTAMP_GAP` | Gap between readings > 30s | Detects lost readings or clock skew |
| `KWH_ROLLBACK` | totalKwh decreased from previous reading | The energy counter should be monotonically increasing; a decrease means the plug rebooted |
| `SPIKE_DETECTED` | Wattage jump > 500W in one interval | EV chargers have a consistent draw; a 500W spike in 10 seconds is likely noise |

Readings with quality flags are still persisted (with flags attached) — they're not dropped. This lets you analyze data quality trends without losing data points.

### KeyHashUtil

Wraps `MessageDigest.getInstance("SHA-256")` for hashing device keys. Used by the edge service to validate incoming telemetry and by the cloud service when registering new devices.

---

## 7. The Dashboard

**Tech:** Thymeleaf + HTMX + Tabler UI + ApexCharts. No separate frontend build step.

Why Thymeleaf over React/Vue? For a server-rendered dashboard with a few dynamic elements, Thymeleaf is simpler: one build system (Maven), one language (Java), no Node.js toolchain. HTMX adds interactivity (live polling, form submission without page reload) with zero JavaScript framework overhead.

Why Tabler UI? It's a Bootstrap-based admin template with a dark theme, responsive grid, and pre-built card/table/badge components. It gives the dashboard a professional look without custom CSS.

### Pages

1. **Live** (`/dashboard`): Shows all registered devices with their latest telemetry readings (wattage, voltage, amperage, total kWh), online/offline status, and ON/OFF toggle buttons. HTMX polls `/dashboard/api/live` every 5 seconds and swaps the inner HTML of the readings container — the page never fully reloads.

2. **History** (`/dashboard/history`): ApexCharts line chart with wattage, voltage, and amperage on separate y-axes. Device selector dropdown and time range buttons (1H, 6H, 24H, 7D). The chart data is loaded via `fetch()` from `/dashboard/api/history/data` which returns JSON.

3. **Sessions** (`/dashboard/sessions`): Table of detected charging sessions with device, start/end times, duration, energy consumed (kWh), estimated cost, and status badges. Includes an ApexCharts timeline (rangeBar) chart showing session durations visually.

### HTMX Pattern

The live readings fragment (`fragments/live-readings.html`) is a partial HTML template — no `<html>` or `<body>` tags. HTMX fetches it and swaps it into the `#live-readings` div:

```html
<div id="live-readings"
     hx-get="/dashboard/api/live"
     hx-trigger="load, every 5s"
     hx-swap="innerHTML">
```

The server returns a fully-rendered HTML fragment. No client-side templating, no JSON-to-DOM mapping. The server controls the markup, which means you can change the layout without touching JavaScript.

---

## 8. Data Model

### PostgreSQL Tables (cloud)

```
devices
├── id (UUID, PK)
├── device_id (unique, e.g. "kauf-01")
├── device_key_hash (SHA-256)
├── online (boolean)
├── name, location
└── first_registered_at

telemetry_readings
├── id (UUID, PK)
├── device_id (indexed with timestamp)
├── timestamp
├── wattage, voltage, amperage, total_kwh
└── (index: device_id + timestamp)

commands
├── id (UUID, PK)
├── device_id (indexed with status)
├── command_type (POWER_ON, POWER_OFF)
├── payload (jsonb)
├── status (PENDING, DELIVERED, ACKNOWLEDGED, EXPIRED)
├── created_at, delivered_at, acknowledged_at, expires_at
└── (index: device_id + status)

charging_sessions
├── id (UUID, PK)
├── device_id (indexed with status)
├── started_at, ended_at
├── start_kwh, end_kwh, energy_used_kwh
├── estimated_cost
└── status (ACTIVE, COMPLETED, INTERRUPTED)
```

### H2 Table (edge)

```
buffered_readings
├── id (bigint auto-increment, PK)
├── device_id
├── timestamp
├── wattage, voltage, amperage, total_kwh
├── quality_flags (comma-separated)
├── synced (boolean, default false)
└── created_at
```

---

## 9. Network and Security

### Traffic Flow

```
Plug ──(plaintext MQTT, LAN only)──▶ Laptop (Mosquitto :1883)
                                          │
                                     Edge service
                                          │
                                     (HTTPS, API key header)
                                          │
                                          ▼
                              Internet ──▶ Linode VPS
                                          │
                                     nginx :443 (TLS termination)
                                          │
                                     Cloud :8080 (localhost only)
                                          │
                                     PostgreSQL :5432 (localhost only)
```

Key security properties:

- **Plaintext MQTT never leaves the LAN.** The Mosquitto broker listens on `0.0.0.0:1883` but is behind the laptop's firewall. Only the plug and the edge service connect to it.
- **Edge-to-cloud is HTTPS.** The edge's `RestClient` connects to `https://smartvolt.drewmeyers.com`. TLS is terminated by nginx with Let's Encrypt certs.
- **API key auth is constant-time.** `MessageDigest.isEqual()` compares every byte regardless of where mismatches occur, preventing timing side-channels.
- **Device keys are hashed at rest.** The raw device key is only in ESPHome's `secrets.yaml` and the edge's environment variable. The cloud stores only the SHA-256 hash.
- **Docker ports bind to localhost.** Both `docker-compose.yml` and `docker-compose.edge.yml` bind published ports to `127.0.0.1`, preventing external access to PostgreSQL or the Spring Boot app directly.

---

## 10. Build and CI

### Maven Multi-Module

The parent POM (`pom.xml`) uses `<packaging>pom</packaging>` with three modules: shared, edge, cloud. Shared is a plain JAR (no `spring-boot-maven-plugin`). Edge and cloud are Spring Boot apps.

The dependency graph:

```
shared ← edge
shared ← cloud
```

`mvn test` from the root runs all 53 tests across all modules. `mvn test -pl shared,cloud -am` runs only shared and cloud tests (with `-am` to also build shared, which cloud depends on).

### Docker Multi-Stage Builds

Both Dockerfiles use a two-stage build:

1. **Build stage** (JDK Alpine): Copies POMs first (for dependency caching), then sources, runs `mvn package -DskipTests`
2. **Runtime stage** (JRE Alpine): Copies only the fat JAR, runs as a non-root `smartvolt` user

The layer ordering means Maven dependencies are cached — rebuilds that only change source code skip the dependency download.

### GitHub Actions

Two separate workflows with path filters:

- `ci-cloud.yml`: Triggers on changes to `shared/` or `cloud/`. Runs `mvn verify -pl shared,cloud -am`.
- `ci-edge.yml`: Triggers on changes to `shared/` or `edge/`. Runs `mvn verify -pl shared,edge -am`.

Both use `actions/setup-java@v4` with Maven cache. No external services (Postgres, MQTT) are needed — all tests use in-memory H2 and mocks.

---

## 11. Design Tradeoffs

| Decision | Alternative considered | Why this choice |
|---|---|---|
| Polling for commands (5s) | WebSocket push | Simpler, firewall-friendly, sufficient latency |
| H2 file-mode for edge buffer | SQLite | Native Spring Data JPA support, zero extra deps |
| Thymeleaf + HTMX | React SPA | One build system, server-rendered, no Node toolchain |
| Per-device key auth | mTLS, JWT | ESP8266 can't do TLS; simple key injection works |
| Batch upload every 30s | Stream each reading | Fewer HTTP requests, better offline behavior |
| S3 as supplementary archive | S3 as primary store | PostgreSQL is the query engine; S3 is for raw replay |
| In-memory user store | Database-backed users | Single admin user doesn't justify a user table |
| Separate edge + cloud modules | Monolith with MQTT | Demonstrates edge computing, data pipeline, offline resilience |
