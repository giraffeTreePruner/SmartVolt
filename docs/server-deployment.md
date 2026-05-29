# SmartVolt Server Deployment Guide

Step-by-step instructions for deploying the redesigned SmartVolt (edge/cloud split) to the Linode VPS and the edge laptop.

---

## Step 0: Audit What Already Exists on the Server

The VPS already has Docker, nginx, certbot, and PostgreSQL from the original monolith deployment. Run these commands to see what's in place before making changes.

### 0a — Docker state

```bash
docker ps --format "table {{.Names}}\t{{.Image}}\t{{.Ports}}\t{{.Status}}"
docker ps -a --format "table {{.Names}}\t{{.Image}}\t{{.Status}}"
docker compose ls
docker volume ls
docker network ls --filter type=custom
```

### 0b — SmartVolt application files

```bash
ls -la /opt/smartvolt* 2>/dev/null
find /opt /home -name ".env" -path "*smartvolt*" 2>/dev/null -exec echo "--- {} ---" \; -exec cat {} \;
find /opt /home -name "docker-compose*" -path "*smartvolt*" 2>/dev/null -exec echo "--- {} ---" \; -exec cat {} \;
```

### 0c — PostgreSQL

```bash
systemctl is-active postgresql 2>/dev/null; echo "(system service)"
docker ps --filter "ancestor=postgres" --format "{{.Names}} {{.Ports}}" 2>/dev/null; echo "(docker)"
sudo -u postgres psql -c "\l" 2>/dev/null
```

### 0d — Mosquitto / MQTT

```bash
systemctl is-active mosquitto 2>/dev/null; echo "(system service)"
docker ps --filter "ancestor=eclipse-mosquitto" --format "{{.Names}} {{.Ports}}" 2>/dev/null; echo "(docker)"
cat /opt/smartvolt-mqtt/config/mosquitto.conf 2>/dev/null
```

### 0e — nginx and TLS

```bash
systemctl is-active nginx
ls -la /etc/nginx/sites-enabled/
cat /etc/nginx/sites-available/smartvolt 2>/dev/null
sudo certbot certificates 2>/dev/null
```

### 0f — Ports in use

```bash
sudo ss -tlnp | grep -E ':80 |:443 |:8080 |:8883 |:1883 |:5432 |:5433 '
```

---

### Audit Summary and Decision Table

Based on the audit, here's what to do with each component:

| Component | Current state | Action | Reason |
|---|---|---|---|
| **nginx** | Active, proxying `smartvolt.drewmeyers.com:443 → :8080` | **Keep as-is** | Already correct. Also serves `drewmeyers.com` and `sthv.org` — do not touch those configs. |
| **Certbot** | 3 certs valid (67-78 days), auto-renewal | **Keep as-is** | SmartVolt cert is valid through Aug 15. |
| **Docker Postgres** (`:5433`) | `smartvolt-postgres-1`, healthy | **Keep — will be recreated** | The `smartvolt_pgdata` volume persists data. `docker compose up` will reuse it. |
| **System Postgres** (`:5432`) | Active, has `sthv` database | **Do not touch** | Used by the Django site `sthv.org`. Completely separate from SmartVolt. |
| **`smartvolt-api-1`** container | Running the old monolith | **Tear down** | Built from the old single-module Dockerfile. Will be replaced by the new `cloud` service. |
| **`smartvolt-mqtt`** container | Mosquitto on `:8883` (TLS) | **Tear down** | In the new architecture, Mosquitto runs on the edge laptop, not the server. |
| **`/opt/smartvolt-mqtt/`** | Certs, config, data | **Remove** | No longer needed on the server. |
| **`/opt/smartvolt/`** | Old monolith repo (single `src/`, `Dockerfile`) | **`git pull` to update** | Same repo, new multi-module structure. The old `Dockerfile` at root is gone; `cloud/Dockerfile` replaces it. |
| **`/opt/smartvolt/.env`** | Has DB creds, API key, MQTT creds | **Update** | Add `DASHBOARD_USERNAME`, `DASHBOARD_PASSWORD`, S3 vars. Remove `MQTT_*` (cloud no longer connects to MQTT). |
| **3 anonymous Docker volumes** | Orphaned from old builds | **Prune after switchover** | `docker volume prune` after verifying everything works. |

---

## Part 1: Cloud Deployment (Linode VPS)

### Step 1 — Stop old containers and remove Mosquitto

```bash
# Stop everything
cd /opt/smartvolt
docker compose down

# Stop and remove the standalone Mosquitto container
docker stop smartvolt-mqtt
docker rm smartvolt-mqtt

# Remove the server-side Mosquitto config (no longer needed)
sudo rm -rf /opt/smartvolt-mqtt
```

### Step 2 — Pull the new codebase

```bash
cd /opt/smartvolt
git pull
```

The repo now has `shared/`, `edge/`, `cloud/` modules instead of a single `src/` directory. The `cloud/Dockerfile` replaces the root `Dockerfile`.

### Step 3 — Update the environment file

```bash
nano .env
```

The file should contain (keep existing DB password and API key, add dashboard and S3 vars):

```
SPRING_DATASOURCE_USERNAME=smartvolt
SPRING_DATASOURCE_PASSWORD=3pM3RPKHwE6vERk
SMARTVOLT_API_KEY=4b719e3e10ad8821fba866c4e059e686295f15b65a7e4866e96250fe9ba604eb
DASHBOARD_USERNAME=admin
DASHBOARD_PASSWORD=<pick-a-password>

# S3 archival (optional — set S3_ENABLED=true to activate)
S3_ENABLED=false
S3_ENDPOINT=https://us-east-1.linodeobjects.com
S3_BUCKET=smartvolt-telemetry-archive
S3_ACCESS_KEY=
S3_SECRET_KEY=
```

**Removed from the old `.env`:** `MQTT_USERNAME` and `MQTT_PASSWORD` — the cloud service no longer connects to MQTT.

### Step 4 — Build and start

```bash
docker compose up -d --build
```

This builds the new multi-module `cloud/Dockerfile` (which compiles `shared` + `cloud`), starts the `cloud` service and reuses the existing `smartvolt_pgdata` volume for PostgreSQL.

Verify:

```bash
docker compose logs -f cloud
# Look for: "Started CloudApplication in X seconds"

curl -s http://localhost:8080/actuator/health
# {"status":"UP"}
```

### Step 5 — Verify nginx (no changes needed)

The existing nginx config already proxies `smartvolt.drewmeyers.com:443 → localhost:8080`, which is exactly what the new cloud service listens on. No config changes required.

Verify:

```bash
curl -s https://smartvolt.drewmeyers.com/actuator/health
# {"status":"UP"}
```

### Step 6 — Register the device

```bash
curl -X POST https://smartvolt.drewmeyers.com/api/devices \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: 4b719e3e10ad8821fba866c4e059e686295f15b65a7e4866e96250fe9ba604eb' \
  -d '{"deviceId":"kauf-01","deviceKey":"<raw-device-key>","name":"EV Charger","location":"Garage"}'
```

### Step 7 — Verify the dashboard

Open `https://smartvolt.drewmeyers.com/dashboard` in a browser.
Log in with the dashboard credentials from your `.env` file.

### Step 8 — Clean up orphaned volumes (optional)

After verifying everything works:

```bash
docker volume prune
```

This removes the 3 anonymous volumes from old builds. The named `smartvolt_pgdata` volume is not affected.

---

## Part 2: Edge Deployment (Ubuntu Laptop)

### Step 1 — Clone the repo

```bash
cd ~
git clone https://github.com/drewsmeyers/smartvolt.git
cd smartvolt
```

### Step 2 — Create the Mosquitto password file

```bash
# Install mosquitto-clients if needed: sudo apt install mosquitto-clients
mosquitto_passwd -c mosquitto/passwd smartvolt
# Enter a password when prompted — use the same one in .env and ESPHome secrets
```

### Step 3 — Configure environment

```bash
cp .env.example .env
nano .env
```

Fill in the edge-relevant values:

```
MQTT_USERNAME=smartvolt
MQTT_PASSWORD=<the-password-you-just-set>
CLOUD_API_KEY=4b719e3e10ad8821fba866c4e059e686295f15b65a7e4866e96250fe9ba604eb
KAUF01_DEVICE_KEY=<raw-device-key-matching-what-you-registered>
```

### Step 4 — Start the edge services

```bash
docker compose -f docker-compose.edge.yml up -d --build
```

Verify:

```bash
docker compose -f docker-compose.edge.yml logs -f edge
# Look for: "Started EdgeApplication in X seconds"

curl http://localhost:8081/actuator/health
# {"status":"UP"}
```

### Step 5 — Check edge health details

```bash
curl http://localhost:8081/health
```

This returns buffer depth, last upload time, and MQTT connection status.

---

## Part 3: ESPHome Device (KAUF PLF10)

### Step 1 — Configure secrets

```bash
cd esphome
cp secrets.yaml.example secrets.yaml
nano secrets.yaml
```

Fill in:

```yaml
wifi_ssid: "YourWiFiNetwork"
wifi_password: "YourWiFiPassword"

device_key: "<same-raw-key-as-registered>"

mqtt_broker: "192.168.1.100"    # ← your laptop's LAN IP
mqtt_username: "smartvolt"
mqtt_password: "<same-password-as-mosquitto>"
```

### Step 2 — Find your laptop's LAN IP

```bash
ip addr show | grep "inet " | grep -v 127.0.0.1
# Use the 192.168.x.x address
```

### Step 3 — Flash the plug

Plug the KAUF PLF10 into a wall outlet. On first boot it creates a WiFi AP.
Connect to it, then flash over the air:

```bash
esphome run kauf-plf10.yaml
```

After flashing, the plug connects to your WiFi and starts publishing telemetry.

### Step 4 — Verify end-to-end

1. Check edge logs: `docker compose -f docker-compose.edge.yml logs -f edge`
   - You should see "Received telemetry from kauf-01" every 10 seconds
2. Check cloud dashboard: `https://smartvolt.drewmeyers.com/dashboard`
   - Live readings should appear within 30 seconds (the batch interval)
3. Test remote control: click ON/OFF in the dashboard
   - The plug should switch within 5 seconds (the command poll interval)

---

## Updating

### Cloud

```bash
ssh your-vps
cd /opt/smartvolt
git pull
docker compose up -d --build
```

### Edge

```bash
cd ~/smartvolt
git pull
docker compose -f docker-compose.edge.yml up -d --build
```

---

## Troubleshooting

| Symptom | Check |
|---|---|
| No readings in dashboard | Edge logs for MQTT connection errors; check Mosquitto password file |
| Readings arrive on edge but not cloud | Edge health endpoint — check `lastUploadTime` and `bufferDepth` |
| Command sent but plug doesn't switch | Edge logs for command polling; check MQTT topic matches ESPHome config |
| Dashboard shows "No devices registered" | Register the device via the REST API (Step 6 in Part 1) |
| S3 archival not working | Check `S3_ENABLED=true` and verify Linode Object Storage credentials |
| `drewmeyers.com` or `sthv.org` broken | SmartVolt deployment should not touch those nginx configs. Check `/etc/nginx/sites-enabled/` — the `drewmeyers.com` and `sthv.org` symlinks should still be there. |
