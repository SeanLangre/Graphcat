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
- [x] Resolve logcat UIDs to package names on-device (`AppResolver`, incl. platform/isolated UIDs) and send as `package`
- [x] Parse `logcat -v long` and include TID in `LogEvent`
- [x] Loki `level` label (Grafana-recognized level names) and `app` label; log line mirrors `adb logcat` format
- [x] Loki and Grafana share the `logstream` Docker network (data source added manually at `http://logstream-loki:3100`)

## Grafana UI (next phase)

Core pipeline (Android → Go → Loki → Grafana) works end-to-end. Architectural decision: Grafana is the main UI for live tailing, search, filtering and investigation; `index.html` stays a rough "is it working" view with no filtering or search. So UI work goes into Grafana, not `index.html`.

1. [ ] LogStream dashboard — logs panel with variables for `device_id`, `level`, `app`, `tag`, plus a free-text search box (line filter)
2. [ ] Overview panels — log volume / error rate over time by `level` and `app`
3. [ ] Provision from files — Loki data source and the dashboard JSON under `infrastructure/grafana/`, so a fresh `docker compose up` needs no manual setup
4. [ ] Optional: alert on error spikes (e.g. rate of `level="error"` per `app`)

## Next up

1. [ ] Reliable Android reconnect (WebSocket drop/retry handling in `LogStreamClient`)
2. [ ] Go server buffering/backpressure for bursty log volume
3. [ ] Authentication (device stream + dashboard/Grafana access)
4. [ ] Multiple devices (distinct `device_id`s feeding the same Go server; verify Loki label queries across devices)
5. [ ] Production deployment

## Notes

- `index.html` (`Go → Browser`) is only a rough check that events are flowing — it doesn't depend on Loki/Grafana, which also makes it handy for telling Android→Go problems apart from Go→Loki ones. No filtering/search planned there; use Grafana.
- ADB permission grants are a setup step, not part of the runtime transport; once granted, the device streams independently.
