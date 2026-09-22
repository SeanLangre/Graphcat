# TODO

Roadmap toward the [planned architecture](README.md#planned-architecture): Android as collector, Go as ingestion/control, Loki as log store, Grafana as search/observability UI.

## Done

- [x] Android Logcat → `LogcatCollector` → WebSocket → Go server → browser dashboard (live path working end-to-end)
- [x] `LogStreamService` as a foreground `specialUse` service — streaming continues after leaving the Activity
- [x] System-wide Logcat access via `adb shell pm grant com.example.logstream android.permission.READ_LOGS`
- [x] Run Loki locally (Docker Compose in `infrastructure/loki/`)
- [x] Go server → Loki ingestion (`LokiClient.Push` in `Go/server/loki.go`, pushed alongside the existing dashboard broadcast in `deviceHandler`)
- [x] Run Grafana locally (`infrastructure/grafana/docker-compose.yml`, `:3000`)
- [x] Make the Loki URL in `Go/server/main.go` configurable via `LOKI_URL` env var (defaults to `http://localhost:3100`)
- [x] Grafana → Loki data source (provisioning or manual), basic dashboards/queries
- [x] Search/filtering in Grafana (by `device_id`, `priority`, `tag`, message content)

## Dashboard UX (next phase)

Core pipeline (Android → Go → Loki → Grafana) works end-to-end, so the next priority is making the live dashboard (`index.html`) pleasant to use, before reconnect/backpressure/auth. Architectural decision: keep Grafana for historical/search analysis, and make `index.html` the polished live/operator UI — Grafana and the LogStream dashboard stay complementary, not redundant.

1. [ ] Improve log display — better timestamp formatting, show `device_id`/`priority`/`tag`/PID/UID, eventually app/package name, make long messages easier to read
2. [ ] Message search — free-text search inside log messages (e.g. `exception`, `crash`, `Bluetooth`), combinable with filters
3. [ ] Better filtering — by device, priority, tag, app/package, possibly PID; an easy "clear filters" action
4. [ ] Time controls — last 5/15 min, last hour, today, custom range
5. [ ] Log-level visualization — errors clearly visible, warnings distinguishable, debug/info less dominant; fix the confusing `UNK` priority presentation
6. [ ] Live-tail experience — explicit Live toggle, auto-follow newest logs, pause while inspecting an old entry, "jump to latest" button
7. [ ] Log details — click a log to expand full contents; copy message; copy structured event; show all metadata
8. [ ] Dashboard layout redesign — header/status bar, filter/search bar, log list (see mockup below); build incrementally rather than a full rewrite

```text
┌─────────────────────────────────────────────────────────────┐
│ LogStream                              🟢 Live              │
├─────────────────────────────────────────────────────────────┤
│ Device ▼    Priority ▼    Tag ▼    App ▼    🔍 Search      │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│ 12:41:03  E  AndroidRuntime                                 │
│           FATAL EXCEPTION: main                             │
│                                                             │
│ 12:41:04  W  WifiHAL                                       │
│           connection retry...                               │
│                                                             │
│ 12:41:05  I  ActivityManager                               │
│           START ...                                         │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

## Next up

1. [ ] Reliable Android reconnect (WebSocket drop/retry handling in `LogStreamClient`)
2. [ ] Go server buffering/backpressure for bursty log volume
3. [ ] Authentication (device stream + dashboard/Grafana access)
4. [ ] Multiple devices (distinct `device_id`s feeding the same Go server; verify Loki label queries across devices)
5. [ ] Production deployment

## Notes

- The live browser dashboard (`Go → Browser`) is intentionally kept independent of Loki/Grafana — it should keep working for immediate monitoring even if the historical/search path is down.
- ADB permission grants are a setup step, not part of the runtime transport; once granted, the device streams independently.
