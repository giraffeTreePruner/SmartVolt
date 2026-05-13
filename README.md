# SmartVolt

> Spring Boot service that subscribes to real-time MQTT telemetry from a Sonoff S31 smart outlet, stores EV charging sessions in PostgreSQL, and publishes commands to schedule charging during off-peak TOU rate windows.

> ⚠️ **Work in progress** — actively being built.

---

## Tech Stack

| Backend | Messaging | Database | DevOps |
|---|---|---|---|
| Spring Boot 3, Spring Integration, Spring Security, Spring Scheduler | MQTT (Eclipse Paho), Mosquitto broker | PostgreSQL 15, Spring Data JPA | Docker Compose, GitHub Actions, Maven |

---

## Architecture

```
                    MQTT (QoS 0)
  ┌──────────────┐  telemetry   ┌──────────────────────┐
  │  Sonoff S31  │ ──────────►  │  Mosquitto Broker    │
  │  Tasmota fw  │              │  Linode :1883        │
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
mosquitto_pub -h <broker-ip> -p 1883 -u smartvolt -P <password> \
  -t 'smartvolt/devices/sonoff-01/telemetry' \
  -m '{"key":"sk-sonoff01-a3f9","ENERGY":{"Power":1140,"Voltage":121,"Current":9.4,"Today":0.523}}'
```

---

## MQTT Topic Scheme

| Topic | Direction | Purpose |
|---|---|---|
| `smartvolt/devices/+/telemetry` | Device → Server | Power readings (QoS 0, every 10s) |
| `smartvolt/devices/{id}/command` | Server → Device | Power on/off commands (QoS 1, retained) |
| `smartvolt/devices/+/status` | Device → Server | Online/offline via LWT |



## Hardware

**Sonoff S31** flashed with Tasmota firmware, connected to the Mosquitto broker over home Wi-Fi. Tasmota's Rules engine injects a per-device secret key into every telemetry payload — the API validates this key on every inbound message and silently drops anything that doesn't match.

---

## Roadmap

- [ ] TLS on port 8883 (encrypt credentials in transit)
- [ ] Custom Mosquito Deploy Script for easy host deployment
- [ ] Dynamic TOU windows
- [ ] Charging cost dashboard
- [ ] Multi-device support
