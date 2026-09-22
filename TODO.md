# TODO

Roadmap toward the [planned architecture](README.md#planned-architecture): Android as collector, Go as ingestion/control, Loki as log store, Grafana as search/observability UI.

## Done

- [x] Android Logcat → `LogcatCollector` → WebSocket → Go server → browser dashboard (live path working end-to-end)
- [x] `LogStreamService` as a foreground `specialUse` service — streaming continues after leaving the Activity
- [x] System-wide Logcat access via `adb shell pm grant com.example.logstream android.permission.READ_LOGS`
- [x] Run Loki locally (Docker Compose in `infrastructure/loki/`)
- [x] Go server → Loki ingestion (`LokiClient.Push` in `Go/server/loki.go`, pushed alongside the existing dashboard broadcast in `deviceHandler`)

## Next up

1. [ ] Add Grafana to `infrastructure/loki/docker-compose.yml` alongside Loki
2. [ ] Grafana → Loki data source, basic dashboards/queries
3. [ ] Search/filtering in Grafana (by `device_id`, `priority`, `tag`, message content)
4. [ ] Make the Loki URL in `Go/server/main.go` (`NewLokiClient("http://localhost:3100")`) configurable instead of hardcoded
5. [ ] Reliable Android reconnect (WebSocket drop/retry handling in `LogStreamClient`)
6. [ ] Go server buffering/backpressure for bursty log volume
7. [ ] Authentication (device stream + dashboard/Grafana access)
8. [ ] Multiple devices (distinct `device_id`s feeding the same Go server; verify Loki label queries across devices)
9. [ ] Production deployment

## Notes

- The live browser dashboard (`Go → Browser`) is intentionally kept independent of Loki/Grafana — it should keep working for immediate monitoring even if the historical/search path is down.
- ADB permission grants are a setup step, not part of the runtime transport; once granted, the device streams independently.
