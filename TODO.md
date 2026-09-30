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
- [x] Loki and Grafana share the `logstream` Docker network (data source at `http://logstream-loki:3100`)

## Grafana UI (next phase)

Core pipeline (Android → Go → Loki → Grafana) works end-to-end. Architectural decision: Grafana is the main UI for live tailing, search, filtering and investigation; `index.html` stays a rough "is it working" view with no filtering or search. So UI work goes into Grafana, not `index.html`.

1. [x] LogStream dashboard — logs panel filtered by `device_id`, `level`, `app`, `tag` (multi-select, All) plus a free-text `search` box (case-insensitive regex line filter)
2. [x] Overview panels — log volume by `level` and top-10 errors by `app`, both following the dashboard filters
3. [x] Provision from files — `infrastructure/grafana/provisioning/` (data source, dashboard provider, alert rule) and `infrastructure/grafana/dashboards/logstream.json`
4. [x] Alert on error spikes — fires when an app logs >50 errors in 5 min for 2 min (LogStream folder)
   - [ ] Configure a contact point (only the default email one exists, and SMTP isn't set up, so alerts are visible in Grafana but not delivered)

## Known issues

Things that are wrong or don't work yet in the current code. Several overlap with "Next up" below; this list records the concrete failure, "Next up" the planned work.

### Go server

- [ ] **Loki push blocks ingestion.** `deviceHandler` calls `loki.Push` synchronously, one HTTP request per log line, before reading the next message. A busy device (hundreds of lines/s) outpaces it, and the backpressure reaches the phone: OkHttp queue → 500-slot `logChannel` → `LogcatCollector` stops reading → logcat drops lines. If Loki is down, each line waits the full 5 s client timeout, so the live dashboard stalls too, despite the README's claim that it doesn't depend on Loki. Fix: buffered channel + background goroutine that batches pushes (group by stream, flush every ~100 lines / ~500 ms, drop when full).
- [ ] **Loki label cardinality.** `tag` is a stream label, and Android has thousands of distinct tags; `app` also grows unbounded via `isolated:<uid>` names, and `priority` duplicates `level`. This creates many tiny streams, which Loki handles poorly. Move `tag` (and anything per-process) to structured metadata or the log line, and drop `priority`. The dashboard's `tag` variable (`label_values(..., tag)`) and `tag=~"$tag"` filter must change with it.
- [ ] **Slow dashboard stalls devices.** `Hub.broadcast` writes to each dashboard serially under the read lock with a 2 s timeout per client, inside the device read loop. Clients whose writes fail are logged but never removed.
- [ ] **Per-event server logging.** Every event is `log.Printf`'d, flooding stdout under real volume.
- [ ] **`index.html` served by relative path.** `http.ServeFile(w, r, "index.html")` only works when started from `Go/server`; use `//go:embed`. The `/` handler also serves it for every unknown path.

### Android

- [ ] **Hardcoded `device_id`.** `LogStreamClient` always sends `"android-01"`, so multiple devices merge into one stream (the README's multi-device claim is not true yet). Use `Settings.Secure.ANDROID_ID` or a configurable name.
- [ ] **No reconnect.** After a WebSocket failure the status goes to "Connection failed" and nothing retries; the user must stop and start again.
- [ ] **Collector keeps running while disconnected.** On failure only the sender is stopped; the collector fills `logChannel` (500), then blocks. A reconnect first sends that stale burst.
- [ ] **Unsynchronized socket state.** `webSocket` is written from OkHttp callback threads and read from the sender coroutine and main thread without `@Volatile`/locking.
- [ ] **`seq` resets** to 0 whenever the collector restarts, and the server doesn't use it, so gaps/drops can't be detected.

### Repo / infra

- [ ] `infrastructure/loki/data/chunks/loki_cluster_seed.json` is tracked despite `infrastructure/loki/data/` being in `.gitignore` (committed before the rule) — `git rm --cached` it.
- [ ] No auth and cleartext `ws://` on all interfaces, carrying the full system log (may include tokens/PII). Only safe on a trusted LAN.

## Next up

1. [ ] Reliable Android reconnect (WebSocket drop/retry handling in `LogStreamClient`)
2. [ ] Go server buffering/backpressure for bursty log volume
3. [ ] Authentication (device stream + dashboard/Grafana access)
4. [ ] Multiple devices (distinct `device_id`s feeding the same Go server; verify Loki label queries across devices)
5. [ ] Production deployment

## Notes

- `index.html` (`Go → Browser`) is only a rough check that events are flowing — it doesn't depend on Loki/Grafana, which also makes it handy for telling Android→Go problems apart from Go→Loki ones. No filtering/search planned there; use Grafana.
- ADB permission grants are a setup step, not part of the runtime transport; once granted, the device streams independently.
