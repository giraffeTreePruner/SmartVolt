# SmartVolt

> Turn any EV charging off a wall outlet into a smart charger!
> Spring Boot service that subscribes to real-time MQTT telemetry from a KAUF PLF10 smart outlet (ESPHome), stores EV charging sessions in PostgreSQL, and publishes commands to schedule charging during off-peak TOU rate windows.

> :warning: **Work in progress** — actively being built.

---

## Tech Stack

| Backend | Messaging | Database | DevOps |
|---|---|---|---|
| Spring Boot 3, Spring Integration, Spring Security, Spring Scheduler | MQTT (Eclipse Paho), Mosquitto broker | PostgreSQL 17, Spring Data JPA | Docker Compose, GitHub Actions, Maven |

---

## Architecture

```
                    MQTT (QoS 0)
  ┌──────────────┐  telemetry   ┌──────────────────────┐
  │  KAUF PLF10  │ ──────────►  │  Mosquitto Broker    │
  │  ESPHome fw  │              │  Linode :8883 TLS    │
  │  120V outlet │ ◄──────────  └──────────┬───────────┘
  └──────────────┘  command                │
                    (QoS 1,                ▼
                     retain)   ┌──────────────────────┐
                               │   SmartVolt API      │
                               │   Spring Boot :8080  │
                               │                      │
                               │  MqttInboundAdapter  │
                               │  ChargingScheduler   │
                               │  CommandPublisher    │
                               └──────────┬───────────┘
                                          │
                                          ▼
                               ┌──────────────────────┐
                               │   PostgreSQL :5432   │
                               │   telemetry_readings │
                               │   devices            │
                               └──────────────────────┘
```

---

## Quick Start

```bash
docker compose up
```

Brings up the Spring Boot API and PostgreSQL. Mosquitto runs separately on a Linode VPS.

### Send a test telemetry reading

```bash
mosquitto_pub -h smartvolt.drewmeyers.com -p 8883 \
  -u smartvolt -P <password> --cafile /path/to/chain.pem \
  -t 'smartvolt/devices/kauf-01/tele/SENSOR' \
  -m '{"key":"sk-kauf01-xxxx","wattage":1140,"voltage":121,"amperage":9.4,"totalKwh":0.523}'
```

### Remote power control

```bash
# Turn outlet ON
curl -X POST 'https://smartvolt.drewmeyers.com/api/devices/kauf-01/power?on=true' \
  -H 'X-API-Key: <your-api-key>'

# Turn outlet OFF
curl -X POST 'https://smartvolt.drewmeyers.com/api/devices/kauf-01/power?on=false' \
  -H 'X-API-Key: <your-api-key>'
```

---

## MQTT Topic Scheme

| Topic | Direction | Purpose |
|---|---|---|
| `smartvolt/devices/+/tele/SENSOR` | Device → Server | Bundled telemetry with device key (QoS 0, every 10s) |
| `smartvolt/devices/{id}/cmnd/Power` | Server → Device | Power on/off commands (QoS 1, retained) |
| `smartvolt/devices/+/tele/LWT` | Device → Server | Device online/offline (LWT) |

### Telemetry payload (ESPHome `mqtt.publish_json`)

```json
{
  "key": "sk-kauf01-xxxx",
  "wattage": 1140.5,
  "voltage": 121.3,
  "amperage": 9.4,
  "totalKwh": 0.523
}


## Hardware

**KAUF PLF10** running ESPHome firmware, connected to the Mosquitto broker over TLS. ESPHome's `interval` block publishes bundled JSON telemetry every 10 seconds with a per-device secret key injected via `mqtt.publish_json`. The API validates this key on every inbound message and silently drops anything that doesn't match.

See [`esphome/kauf-plf10.yaml`](esphome/kauf-plf10.yaml) for the reference ESPHome configuration.

---

## REST API

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/devices` | Register a new device |
| `GET` | `/api/devices` | List all devices |
| `POST` | `/api/devices/{id}/power?on=true\|false` | Turn device on/off |
| `GET` | `/api/devices/{id}/readings?from=...&to=...` | Query telemetry (paginated) |

All endpoints require `X-API-Key` header (except `/actuator/health`).

---

## Roadmap

- [ ] Charging analytics and web dashboard
- [ ] Custom Mosquito deploy script for easy host deployment
- [ ] Dynamic TOU windows
- [ ] Charging cost dashboard
- [ ] Multi-device support
