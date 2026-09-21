# LogStream

Stream an Android device's `logcat` output in real time to a browser dashboard over WebSockets.

## How it works

- **Android app** (`Android/`) — tails `logcat` in a foreground service and pushes each log line to a server as JSON over a WebSocket.
- **Server** (`Go/server/`) — a Go WebSocket hub that receives log events from device(s) and broadcasts them to connected dashboards. Also serves the dashboard's `index.html`.
- **Test client** (`Go/testclient/`) — a standalone Go program that simulates a device by sending one fake log event per second, useful for testing the server/dashboard without a real phone.

```
Android device --(WebSocket: /v1/device/stream)--> Go server --(WebSocket: /v1/dashboard/stream)--> Browser dashboard
```

## Running the server

```
cd Go/server
go run .
```

The server listens on `:8080`:
- `POST/GET /v1/device/stream` — devices connect here and push log events
- `GET /v1/dashboard/stream` — browsers connect here to receive broadcasts
- `GET /` — serves the dashboard (`index.html`)
- `GET /healthz` — health check

Open `http://localhost:8080` in a browser to view the dashboard.

## Trying it without a phone

```
cd Go/testclient
go run .
```

This connects to `ws://localhost:8080/v1/device/stream` and streams synthetic log events once per second, which will appear on the dashboard.

## Running the Android app

1. Copy the local properties template and fill in your values:
   ```
   cd Android
   cp local.properties.example local.properties
   ```
2. Edit `local.properties`:
   - `sdk.dir` — path to your Android SDK
   - `SERVER_URL` — the server's WebSocket device endpoint, e.g. `ws://192.168.1.100:8080/v1/device/stream` (use your machine's LAN IP, reachable from the device)
3. Build and install:
   ```
   ./gradlew installDebug
   ```
4. The app requires `READ_LOGS`; on a non-rooted device this permission must be granted manually. Then run:
   ```
   adb shell pm grant com.example.logstream android.permission.READ_LOGS
   ```
5. Restart the app:
   ```
   adb shell am force-stop com.example.logstream
   ```

Now open LogStream and press **Start Streaming**.

## Requirements

- Go 1.27+
- Android SDK 35, minSdk 26
- A device and server on the same network (the app uses `usesCleartextTraffic` for plain `ws://`)

## Planned architecture

The current server fans events straight to connected dashboards. The planned direction adds Loki as a persistent, searchable log store and Grafana as an operator UI on top of it, while keeping the existing live browser dashboard for immediate monitoring:

```
                         ┌─────────────────────────────┐
                         │       Android Device        │
                         │                             │
                         │  LogStream App              │
                         │      │                      │
                         │      ▼                      │
                         │  Foreground Service         │
                         │  (specialUse)               │
                         │      │                      │
                         │      ▼                      │
                         │  Android Logcat             │
                         │  READ_LOGS granted via ADB  │
                         │      │                      │
                         │      ▼                      │
                         │  LogStreamClient             │
                         └──────┬──────────────────────┘
                                │
                                │ WebSocket
                                │ /v1/device/stream
                                ▼
                    ┌──────────────────────────┐
                    │        Go Server         │
                    │                          │
                    │  Device WebSocket        │
                    │          │               │
                    │          ▼               │
                    │     LogEvent             │
                    │          │               │
                    │     ┌────┴─────┐         │
                    │     │          │         │
                    │     ▼          ▼         │
                    │   Loki      Dashboard    │
                    │     │      WebSocket     │
                    │     │          │         │
                    └─────┼──────────┼─────────┘
                          │          │
                          ▼          ▼
                    ┌─────────┐   Browser
                    │  Loki   │   Dashboard
                    │  :3100  │
                    └────┬────┘
                         │
                         ▼
                   ┌───────────┐
                   │  Grafana  │
                   │   :3000   │
                   └───────────┘
```

- **Android** — the collector. `LogStreamService` (a foreground `specialUse` service) keeps `logcat -v threadtime` running after the app is backgrounded, using system-wide `READ_LOGS` granted via `adb shell pm grant`. Parsed lines become structured `LogEvent` JSON and are sent over `/v1/device/stream` — raw logcat text never leaves the device.
- **Go server** — the ingestion/control layer. Each `LogEvent` received from a device does two things: it's persisted to Loki for historical search, and it's broadcast live to any connected dashboard over `/v1/dashboard/stream`. The live dashboard doesn't depend on Loki/Grafana being up.
- **Loki** — the log storage/search backend (planned to run locally via Docker, `:3100`). Fields like `device_id`, `priority`, and `tag` become labels (e.g. `{device_id="android-01", priority="E"}`), with the message stored as log content.
- **Grafana** — the operator/search UI (planned, `:3000`), for historical queries, time-range filtering, log tailing, and dashboards across one or many devices.
- **Two views**: a live view (`Go → Browser`) for immediate monitoring, and a historical/search view (`Go → Loki → Grafana`) for investigation.
- **Multiple devices** — the architecture already supports this: additional Android devices connect to the same `/v1/device/stream` endpoint with distinct `device_id`s, and Loki labels make it possible to query a single device or across all of them.

See [TODO.md](TODO.md) for the remaining work to get there.
