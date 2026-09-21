# TODO

Roadmap toward the [planned architecture](README.md#planned-architecture): Android as collector, Go as ingestion/control, Loki as log store, Grafana as search/observability UI.

## Done

- [x] Android Logcat → `LogcatCollector` → WebSocket → Go server → browser dashboard (live path working end-to-end)
- [x] `LogStreamService` as a foreground `specialUse` service — streaming continues after leaving the Activity
- [x] System-wide Logcat access via `adb shell pm grant com.example.logstream android.permission.READ_LOGS`

## Next up

1. [ ] Run Loki locally (Docker)
2. [ ] Go server → Loki ingestion (push each `LogEvent` alongside the existing dashboard broadcast)
3. [ ] Docker Compose to bring up Loki (+ Grafana) together
4. [ ] Grafana → Loki data source, basic dashboards/queries
5. [ ] Search/filtering in Grafana (by `device_id`, `priority`, `tag`, message content)
6. [ ] Reliable Android reconnect (WebSocket drop/retry handling in `LogStreamClient`)
7. [ ] Go server buffering/backpressure for bursty log volume
8. [ ] Authentication (device stream + dashboard/Grafana access)
9. [ ] Multiple devices (distinct `device_id`s feeding the same Go server; verify Loki label queries across devices)
10. [ ] Production deployment

## Notes

- The live browser dashboard (`Go → Browser`) is intentionally kept independent of Loki/Grafana — it should keep working for immediate monitoring even if the historical/search path is down.
- ADB permission grants are a setup step, not part of the runtime transport; once granted, the device streams independently.
