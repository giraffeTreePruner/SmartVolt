# SmartVolt

> Turn any EV charging off a wall outlet into a smart charger.

Spring Boot edge/cloud platform that monitors EV charging via a KAUF PLF10 smart outlet (ESPHome). An edge gateway on the local network ingests MQTT telemetry, validates data quality, and batches readings to a cloud service that stores them in PostgreSQL, archives to S3, detects charging sessions, and serves a real-time web dashboard.

---

## Architecture

```
KAUF PLF10 (ESPHome)                 Ubuntu Laptop                        Linode VPS
┌──────────────────┐   plain MQTT    ┌──────────────────────┐   HTTPS     ┌──────────────────────────┐
│  ESP8285 relay   │───(LAN:1883)───▶│  Mosquitto broker    │            │  nginx (TLS termination) │
│  10s telemetry   │                 │                      │            │         :443             │
│  per-device key  │◀───────────────│  SmartVolt Edge       │───────────▶│  SmartVolt Cloud  :8080  │
└──────────────────┘   ON/OFF cmd    │  - MQTT subscribe    │  batched   │  - REST API (ingest)     │
                                     │  - Key validation    │  JSON      │  - PostgreSQL            │
                                     │  - Data quality      │  every     │  - S3 archival           │
                                     │  - H2 buffer         │  30s       │  - Charging sessions     │
                                     │  - Command polling   │◀───────────│  - Command queue         │
                                     └──────────────────────┘  pending   │  - Web dashboard         │
                                                               cmds      │  - TOU scheduler         │
                                                                         └──────────────────────────┘
```

**Why edge + cloud?** The KAUF PLF10 is ESP8285-based (ESP8266 family) with only 80KB RAM — it can't do TLS. The edge gateway sits on the same LAN, accepts plaintext MQTT, validates and buffers readings in H2 (surviving internet outages), and forwards to the cloud over HTTPS.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Device firmware | ESPHome on KAUF PLF10 (ESP8285) |
| Edge gateway | Spring Boot 4, Spring Integration MQTT, H2, RestClient |
| Cloud platform | Spring Boot 4, Spring Security, Spring Data JPA, Thymeleaf |
| Database | PostgreSQL 17 |
| Object storage | Linode Object Storage (S3-compatible, AWS SDK v2) |
| Dashboard | Thymeleaf + HTMX + Tabler UI + ApexCharts |
| DevOps | Docker, Docker Compose, GitHub Actions, nginx, Let's Encrypt |

---

## Project Structure

```
smartvolt/
├── pom.xml                          # Parent POM (Maven multi-module)
├── shared/                          # Plain JAR — DTOs, validation, utilities
│   └── src/.../shared/
│       ├── dto/                     # TelemetryBatchRequest, CommandDto, etc.
│       ├── validation/              # DataQualityValidator, QualityFlag
│       └── util/                    # KeyHashUtil (SHA-256)
├── edge/                            # Spring Boot app (runs on laptop)
│   ├── Dockerfile
│   └── src/.../edge/
│       ├── mqtt/                    # MQTT subscribe, key validation
│       ├── buffer/                  # H2 entity, BatchUploader (@Scheduled 30s)
│       ├── command/                 # CommandPoller (@Scheduled 5s), LocalMqttPublisher
│       └── health/                  # Edge health endpoint
├── cloud/                           # Spring Boot app (runs on Linode)
│   ├── Dockerfile
│   └── src/.../cloud/
│       ├── ingest/                  # Batch ingest controller, S3 archival
│       ├── device/                  # Device registration and management
│       ├── telemetry/               # Query endpoints
│       ├── command/                 # Command queue (PENDING → ACKNOWLEDGED)
│       ├── session/                 # Charging session state machine
│       ├── scheduler/               # TOU rate scheduler
│       ├── dashboard/               # Thymeleaf controllers, HTMX API
│       └── security/               # API key filter + form login
├── esphome/                         # Device firmware config
├── mosquitto/                       # Local broker config
├── docker-compose.yml               # Cloud deployment (PostgreSQL + cloud app)
├── docker-compose.edge.yml          # Edge deployment (Mosquitto + edge app)
└── .github/workflows/               # CI: ci-edge.yml, ci-cloud.yml
```

---

## Quick Start (Local Dev)

Run the cloud service locally with an in-memory H2 database and sample data:

```bash
cd cloud
../mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Open `http://localhost:8080/dashboard` — login with `admin` / `admin`.

---

## Deployment

### Cloud (Linode VPS)

```bash
# 1. Create .env from the template
cp .env.example .env
# Edit .env with real credentials

# 2. Build and start
docker compose up -d --build

# 3. nginx reverse proxy (already configured on the VPS)
# Proxies :443 → localhost:8080
```

### Edge (Ubuntu laptop)

```bash
# 1. Create Mosquitto password file
mosquitto_passwd -c mosquitto/passwd smartvolt

# 2. Set environment variables in .env
# MQTT_USERNAME, MQTT_PASSWORD, CLOUD_API_KEY, KAUF01_DEVICE_KEY

# 3. Start edge + local broker
docker compose -f docker-compose.edge.yml up -d --build
```

### ESPHome device

```bash
cd esphome
cp secrets.yaml.example secrets.yaml
# Edit secrets.yaml: WiFi, broker IP (laptop's LAN IP), device key
esphome run kauf-plf10.yaml
```

---

## Data Flow

1. **KAUF PLF10** publishes JSON telemetry every 10s to `smartvolt/devices/kauf-01/tele/SENSOR`
2. **Edge** subscribes to local MQTT, validates the per-device key (SHA-256), runs data quality checks (range, gaps, spikes), and writes to an H2 buffer
3. **Edge BatchUploader** runs every 30s: queries up to 100 unsynced readings, POSTs `TelemetryBatchRequest` to the cloud, marks as synced on success
4. **Cloud TelemetryIngestController** persists readings to PostgreSQL, archives the raw batch to S3 (async), and feeds the charging session detector
5. **ChargingSessionDetector** state machine: IDLE → CHARGING after 3 readings > 100W; CHARGING → IDLE after 6 readings < 50W

### Command Flow (Remote On/Off)

1. User clicks ON/OFF in the dashboard (or `POST /api/devices/{id}/power`)
2. Cloud creates a `Command` with status `PENDING` (auto-expires in 5 minutes)
3. Edge polls `GET /api/commands/pending` every 5s
4. Edge publishes ON/OFF to local MQTT → plug switches
5. Edge calls `POST /api/commands/{id}/ack` → status becomes `ACKNOWLEDGED`

---

## REST API

All `/api/**` endpoints require `X-API-Key` header.

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/devices` | Register a new device |
| `GET` | `/api/devices` | List all devices |
| `POST` | `/api/devices/{id}/power?on=true\|false` | Queue power command |
| `GET` | `/api/devices/{id}/readings?from=...&to=...` | Query telemetry (paginated) |
| `POST` | `/api/telemetry/batch` | Ingest telemetry batch (edge → cloud) |
| `GET` | `/api/commands/pending?deviceId=...` | Get pending commands (edge polls this) |
| `POST` | `/api/commands/{id}/ack` | Acknowledge a command |

### Dashboard (session auth)

| URL | Description |
|---|---|
| `/dashboard` | Live readings with HTMX polling (5s) |
| `/dashboard/history` | ApexCharts telemetry chart with time range picker |
| `/dashboard/sessions` | Charging session table with timeline chart |

---

## MQTT Topics

| Topic | Direction | Purpose |
|---|---|---|
| `smartvolt/devices/+/tele/SENSOR` | Device → Edge | Bundled telemetry with device key |
| `smartvolt/devices/{id}/cmnd/Power` | Edge → Device | Power on/off commands |
| `smartvolt/devices/+/tele/LWT` | Device → Edge | Online/offline (LWT) |

---

## Testing

```bash
# Run all tests (53 total)
./mvnw test

# By module
./mvnw test -pl shared        # 9 tests  — DataQualityValidator
./mvnw test -pl edge           # 15 tests — TelemetryHandler, BatchUploader, CommandPoller
./mvnw test -pl cloud          # 29 tests — controllers, services, session detector, dashboard
```

---

## Hardware

**KAUF PLF10** running ESPHome firmware. ESP8285-based (ESP8266 family), 80KB RAM. Publishes bundled JSON telemetry every 10 seconds with a per-device secret key injected via `mqtt.publish_json`. The edge service validates this key on every inbound message.

See [`esphome/kauf-plf10.yaml`](esphome/kauf-plf10.yaml) for the ESPHome configuration.
