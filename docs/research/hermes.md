# Hermes Agent as backend for "Lara im Ohr" — interface research

Research date: 2026-11-27. Author: research subagent (read-only; nothing was built, run or installed).

Legend for every statement: **[V] verified** (seen in a primary source, cited), **[I] inferred** (reasoned from cited sources), **[U] unknown** (not established; needs a test or a decision).

Paths: `HA/` = `<repos>/hermes-agent/` (shallow clone of `NousResearch/hermes-agent`, `main` @ `28ef6e4e7d1df3015bf786dea6b0d355baf5c211`, committed 2026-11-27). Other clones live next to it under `.../scratchpad/repos/<name>/`. AOSP excerpts were fetched from `android.googlesource.com` (`refs/heads/android17-release`) into `.../scratchpad/research/aosp/`.

---

## 0. Versions and facts about the upstream project

| Item | Value | Status / source |
|---|---|---|
| Repository | `NousResearch/hermes-agent`, MIT, Python, default branch `main` | [V] `gh api repos/NousResearch/hermes-agent` (license `MIT`); `HA/pyproject.toml` `license = "MIT"` |
| Latest stable release | **v0.23.4**, published 2026-11-24T08:17:06Z (tag commit `3ac54e1012be1784e00a0f23bdfdce46b769675d`) | [V] `gh api repos/NousResearch/hermes-agent/releases` |
| Previous stable releases | v0.23.3 (2026-11-20), v0.23.2 (11-16), v0.23.1 (11-10), v0.23.0 (11-05), v0.22.7 (11-03) … v0.21.6 (10-08); before that CalVer tags `v2026.9.24`, `v2026.9.21`, … | [V] same API call. The version scheme changed from CalVer to 0.x in October 2026 |
| Canary channel | daily canaries, e.g. `canary.20261125T064800Z-v0.23.4` | [V] same |
| Churn | `main` is **1093 commits ahead of v0.23.4** three days after the release | [V] `gh api repos/NousResearch/hermes-agent/compare/v0.23.4...main` -> `ahead_by=1093` |
| Version in source | `version = "0.0.0"` in `pyproject.toml` (stamped at release) | [V] `HA/pyproject.toml` |
| Key routes unchanged between v0.23.4 and the examined `main` | `/api/sessions/{id}/chat`, `/api/jobs`, `"audio_api": False`, port 8642, 10 s SSE keepalive; `/api/audio/*` routes | [V] `gh api .../contents/gateway/platforms/api_server.py?ref=v0.23.4` and `.../hermes_cli/web_routers/audio.py?ref=v0.23.4` |

Consequence [I]: Hermes moves extremely fast. Whatever interface the app targets must be pinned to a server version and guarded by a contract check at startup.

---

## 1. Short answer

1. **[V]** Hermes documents exactly three protocols for external programs (`HA/website/docs/developer-guide/programmatic-integration.md`): **ACP** (JSON-RPC over stdio), the **TUI-gateway JSON-RPC** (stdio or WebSocket `/api/ws`, subprotocol `hermes-gateway-v1`), and the **OpenAI-compatible API server** (HTTP + SSE, port 8642). Its own guidance: "You want any OpenAI-compatible frontend, a language-agnostic HTTP client, or curl-driven automation -> API server."
2. **Recommendation [I]:** target the **API server** for version 1: `POST /v1/runs` (+ `GET /v1/runs/{id}/events` SSE, `/stop`, `/approval`) or, for the very first spike, the synchronous `POST /api/sessions/{id}/chat`. Bearer token `API_SERVER_KEY`, connect on demand, no socket kept open.
3. **[V]** The API server has **no audio and no push**: `"audio_api": False, "realtime_voice": False` in `/v1/capabilities` (`HA/gateway/platforms/api_server.py:55`). STT/TTS exist on a *different* listener (dashboard backend, `/api/audio/*`). For v1 do speech on the phone or add the dashboard audio endpoints later.
4. **[V]** For the proactive direction Hermes delivers cron results and `hermes send` messages to messaging platforms; the lightest one for a custom app is the bundled **ntfy** platform adapter (`deliver="ntfy"`).
5. **[V]** Neither Hermes listener terminates TLS. Use a VPN (WireGuard/Tailscale) and/or a TLS reverse proxy.
6. **[V]** Both clients named in the planning notes (BlindPilot-Android, hermes-mobile-client) use the dashboard WebSocket, not the API server. Several more relevant projects exist (section 5.3).

---

## 2. Every way an external client can talk to a running agent

### 2.0 Overview

| # | Interface | Process / default listener | Transport | Auth | Documented? | Stability |
|---|---|---|---|---|---|---|
| A | **API server** (OpenAI-compatible + Hermes-native runs/sessions/jobs) | `hermes gateway` with `API_SERVER_ENABLED=true`; `127.0.0.1:8642` | HTTP/1.1 JSON + SSE | `Authorization: Bearer <API_SERVER_KEY>` | Yes, user guide + dev guide | Best available: `/v1/capabilities` is called "the stable, machine-readable API surface for external UIs" in the handler docstring |
| B | **Dashboard / TUI-gateway WebSocket** `/api/ws` + dashboard REST `/api/*` | `hermes serve` / `hermes dashboard` (`127.0.0.1:9119`), or the gateway's Webapp listener (`webapp.port` 9120) | WebSocket, newline-delimited JSON-RPC 2.0; REST | loopback: `?token=`; gated: login cookie -> one-use ticket, or session token / OAuth bearer | Yes, but as the backend of first-party UIs | High churn: generated OpenRPC contract with 265 methods, breaking capability handshake, ongoing `serve` -> gateway migration |
| C | **ACP** | `hermes acp` | JSON-RPC over **stdio** | process-level | Yes | Stable protocol, but not a network interface |
| D | **Messaging platform adapters** (Telegram, Signal, Matrix, ntfy, SMS, e-mail, ...; webhook; A2A; Home Assistant plugin; Relay) | `hermes gateway` | platform specific | platform specific | Yes | Mature for the big platforms; Relay is flagged experimental |
| E | **Custom platform plugin** (`kind: platform`, `ctx.register_platform`) | inside `hermes gateway` | whatever the plugin implements | plugin specific | Yes (`gateway/platforms/ADDING_A_PLATFORM.md`) | Plugin API is public but evolving; example: `hermes-gadget-sdk` |
| F | In-process Python (`run_agent.AIAgent`) | same process | n/a | n/a | Yes | Not relevant for a phone |

Sources [V]: `HA/website/docs/developer-guide/programmatic-integration.md` (table "Protocol / Transport / Best for / Defined by"; section "Which one should I use?"); `HA/website/docs/reference/cli-commands.md` ("## `hermes serve`": "Start the Hermes backend server — the JSON-RPC/WebSocket gateway the desktop app and remote clients connect to. It is the same server `hermes dashboard` runs, but headless ... default `9119`"); `HA/website/docs/user-guide/configuration.md` ("## Webapp": `webapp.port: 9120`).

There is **no `--mode rpc`** [V]: "Hermes does not have a `--mode rpc` flag. The three protocols above already cover the use cases" (same doc, last section).

### 2.1 Interface A — API server (recommended)

**Enable** [V] (`HA/website/docs/user-guide/features/api-server.md`):

```
# ~/.hermes/.env
API_SERVER_ENABLED=true
API_SERVER_KEY=<>=16 chars, e.g. openssl rand -hex 32>
# API_SERVER_HOST=127.0.0.1   (default)   API_SERVER_PORT=8642 (default)
hermes gateway
```

The key is mandatory and checked for strength [V] (`HA/gateway/platforms/api_server.py`, `_api_key_passes_startup_guard`): "Refusing to start: API_SERVER_KEY is a placeholder or too short (<16 chars). This endpoint dispatches terminal-capable agent work — a guessable key is remote code execution."

**Route table** [V] (`HA/gateway/platforms/api_server.py:1565` `_http_route_table`, plus `api_server_runs._http_routes`):

```
GET  /health, /v1/health            GET /health/detailed (auth)
GET  /v1/models                     GET /api/model/options
GET  /v1/capabilities               GET /v1/skills      GET /v1/toolsets
POST /v1/chat/completions           (stateless; SSE with "stream": true)
POST /v1/responses   GET|DELETE /v1/responses/{id}     (stateful: previous_response_id / conversation)
POST /v1/runs                       -> 202 {"run_id","status":"started","replayed":false}
GET  /v1/runs/{id}                  status / final output (poll)
GET  /v1/runs/{id}/events           SSE, resumable (Last-Event-ID or ?last_seq=)
POST /v1/runs/{id}/approval | /clarify | /steer | /stop | /resolve-unknown
GET|POST /api/sessions              GET|PATCH|DELETE /api/sessions/{id}
GET  /api/sessions/{id}/messages    POST /api/sessions/{id}/fork
POST /api/sessions/{id}/chat        one synchronous turn (JSON)
POST /api/sessions/{id}/chat/stream one turn as SSE
POST /api/sessions/{id}/model
GET|POST /api/jobs   GET|PATCH|DELETE /api/jobs/{id}   POST /api/jobs/{id}/pause|resume|run
```

Every route is mirrored under `/p/<profile>/...` when multi-profile routing is on, and then needs that profile's own key [V] (api-server.md, "Multi-profile routing"; "Breaking change (July 2026)").

**Capabilities document** [V] (`api_server.py:48-57`, `_handle_capabilities` at 2357):

```python
_STATIC_FEATURE_FLAGS = {
    "run_status": True, "run_events_sse": True, "run_stop": True, "run_steer": True,
    "run_approval_response": True, "tool_progress_events": True, "approval_events": True,
    "session_resources": True, "model_options": True, "session_chat": True,
    "session_chat_streaming": True, "session_fork": True, "session_model_lock": True,
    "reasoning_streaming": True,
    "admin_config_rw": False, "jobs_admin": False, "memory_write_api": False,
    "skills_api": True, "audio_api": False, "realtime_voice": False,
    "session_continuity_header": "X-Hermes-Session-Id",
    "session_key_header": "X-Hermes-Session-Key"}
```

The response also carries `"auth": {"type": "bearer", "required": true}`, `"runtime": {"mode": "server_agent", "tool_execution": "server", ...}` and an `endpoints` map. Docstring: "GET /v1/capabilities — the stable, machine-readable API surface for external UIs."

**Per-aspect summary**

| Aspect | Finding | Status / source |
|---|---|---|
| Transport | aiohttp, plain HTTP; SSE for streams; `client_max_size` 10 MB (`MAX_REQUEST_BYTES = 10_000_000`) | [V] `api_server.py:186`, `:3937` |
| TLS | none: `web.TCPSite(runner, host, port, ...)` without `ssl_context` | [V] `HA/gateway/platforms/tcp_site.py:51` |
| Auth | static bearer; `secrets_match(token, expected_key)`; 401 otherwise | [V] `api_server.py:1429 _check_auth` |
| Session continuity | (a) `/v1/runs` body `session_id`; (b) header `X-Hermes-Session-Id` on chat completions/responses; (c) `/api/sessions/{id}/chat`; (d) `previous_response_id` / `conversation` on `/v1/responses`; (e) header `X-Hermes-Session-Key` names a conversation that survives transcript rotation. Precedence in `/v1/runs`: "body session_id > response chain > X-Hermes-Session-Key conversation > run_id" | [V] api-server.md "Conversation and terminal-result semantics"; comment in `api_server_runs.py` `_handle_runs` |
| Sessions are API-owned | "`session_id` ... may name a session the API server created or one it may create; they cannot bind a session that another surface owns — a CLI, TUI, Desktop, ACP or messaging conversation. Such a request is refused with HTTP 409 and error code `permission_denied`" | [V] api-server.md, "API sessions are their own sessions" |
| Long-term memory | `MEMORY.md` / `USER.md` in `~/.hermes/memories/`, per profile, injected as a frozen snapshot at session start; so the API client shares memory with Telegram/CLI sessions of the same profile, but only new sessions see new entries | [V] `HA/website/docs/user-guide/features/memory.md` ("How It Works", "Frozen snapshot pattern", "Memory is scoped per profile") |
| Memory-provider scope | `X-Hermes-Session-Key` (max 256 chars) is threaded to `AIAgent(gateway_session_key=...)` for providers such as Honcho | [V] api-server.md "Long-term memory scoping" |
| Per-request system prompt | `system` message / `instructions` (`/v1/responses`, `/v1/runs`) / `system_message` or `instructions` (session chat) are "layered on top" of the core prompt; tools, memory and skills stay | [V] api-server.md "System Prompt Handling"; `_prepare_session_chat` (`system_prompt = body.get("system_message") or body.get("instructions")`) |
| Per-request model | `model`, `provider`, `model_options` (`reasoning_effort` or `reasoning: {enabled, effort}`, `service_tier`) honoured on `/v1/runs` and session chat without opt-in | [V] api-server.md "Per-request model selection"; `_request_agent_overrides`; `api_server_request_options.py` |
| Streaming | Chat completions: `chat.completion.chunk` + custom `event: hermes.tool.progress`. Runs: JSON events `message.delta`, `message.interim`, `tool.started`, `tool.completed`, `reasoning.available`, `subagent.start|complete`, `approval.request`, `run.completed|failed|cancelled|interrupted`, `replay.truncated`. Session stream: `run.started`, `message.started`, `assistant.delta`, `assistant.commentary`, `tool.started|completed|failed`, `tool.progress`, `approval.request`, `assistant.completed`, `run.<status>`, `error`, `done` | [V] api-server.md; `api_server_runs.py` (`_run_event`, `_FIXED_EVENT_FIELDS`, `_execute_run`); `api_server.py:3078-3190` |
| Keepalive | `: keepalive` comment every 10 s on every SSE stream (`CHAT_COMPLETIONS_SSE_KEEPALIVE_SECONDS = 10.0`) | [V] `api_server.py:189`; api-server.md |
| Reconnect / resume | `/v1/runs/{id}/events` honours `Last-Event-ID` or `?last_seq=` and replays; frames are `id: <seq>` + `data: {"event":...,"run_id":...,"timestamp":...,"seq":...}`; emits `replay.truncated` when the buffer was cut. Unconsumed buffers expire after 300 s (`_RUN_STREAM_TTL = 300`); terminal status is kept 3600 s (`_RUN_STATUS_TTL = 3600`) for `GET /v1/runs/{id}` (which then carries `output`) | [V] `api_server_runs.py:1256-1348`; `api_server.py:3809-3810`; api-server.md |
| Idempotent retries | `Idempotency-Key` header (1-255 visible ASCII) on `POST /v1/runs`: identical retry returns the original `run_id` with `Idempotency-Replayed: true`, retained 24 h; different body -> 409 `idempotency_key_conflict` | [V] api-server.md "POST /v1/runs" |
| Tool approvals | Dangerous commands: default `approvals.mode: smart`. For API sessions `approvals.unattended_mode: deny` blocks instantly, **except** "an api_server session whose client can answer the card (`/v1/runs` and streaming chat completions, via `POST /v1/runs/{id}/approval`) still gets the approval request". Event `approval.request` carries `choices` (`["once","session","always","deny"]`, reduced to `["once","deny"]` when smart-denied). Reply `{"choice":"once"}`; with the canonical session authority `{"request_id":"<prompt_id>","execution_generation":N,"choice":"once"}`. Approval timeout default 300 s | [V] `HA/website/docs/user-guide/security.md` (table of `approvals.*` keys); `api_server.py:83-102`; `api_server_runs.py:1358-1414`; api-server.md "POST /v1/runs/{run_id}/approval" |
| Clarify questions | `POST /v1/runs/{id}/clarify` with `request_id`, `execution_generation`, `answer` | [V] api-server.md |
| Cancel | `POST /v1/runs/{id}/stop` -> `{"status":"stopping"}`, settles as `cancelled`. For `/api/sessions/{id}/chat/stream` a **client disconnect interrupts the run** ("Session SSE client disconnected; interrupted live run") | [V] `api_server_runs.py:1508-1540`; `api_server.py:3190-3200` |
| Steer | `POST /v1/runs/{id}/steer` only while `running`; text reaches the agent at the next tool boundary | [V] programmatic-integration.md |
| Attachments | inline images only (`image_url` / `input_image`, http(s) or `data:image/...`); "Uploaded files (`file` / `input_file` / `file_id`) and non-image `data:` URLs return `400 unsupported_content_type`" | [V] api-server.md |
| Audio | none (`audio_api: False`, `realtime_voice: False`) | [V] `api_server.py:55` |
| Concurrency | `gateway.api_server.max_concurrent_runs` default 10 -> HTTP 429 | [V] api-server.md "Concurrent-run cap" |
| Scheduler access | `/api/jobs` CRUD; `POST /api/jobs` body: `name` (required), `schedule` (required), `prompt`, `deliver` (default `"local"`), `skills`, `repeat`, `paused`, `timezone` | [V] `api_server.py:3331-3371` |
| Proactive push to the client | none: "A completion never starts an unsolicited model turn ... The client owns the next turn." and "this change does not create a new autonomous notification loop for an API request, ACP client, or one-shot CLI" | [V] api-server.md "Detached results and session history"; `HA/website/docs/developer-guide/completion-backlog-delivery.md` |

**Exact shapes for the two calls the app needs first** [V]:

Synchronous turn (`_handle_session_chat`, `api_server.py:3031-3075`; body parsing `_session_chat_user_message` at 588):

```
POST /api/sessions/{session_id}/chat
Authorization: Bearer <key>
{"message": "<text>" | "input": "<text>", "system_message": "<ephemeral instructions>",
 "model": "...", "provider": "...", "model_options": {...}}
-> 200 {"object":"hermes.session.chat.completion","status":"...","session_id":"...",
        "message":{"role":"assistant","content":"<final text>"},"usage":{...},"runtime":{...}}
   + response header X-Hermes-Session-Id
```

The session must exist: `POST /api/sessions` with optional `id` (default `api_<unixtime>_<8 hex>`), `title`, `system_prompt`, `model`, `provider` (`_handle_create_session`, `api_server.py:2578`). The returned `session_id` can differ from the requested one after context compression; always adopt the returned value [V] (`effective_session_id` in the handler; api-server.md: "keep sending that ID even after compression").

Detached run (`_handle_runs`, `api_server_runs.py:768`):

```
POST /v1/runs
Authorization: Bearer <key>
Idempotency-Key: <uuid>
X-Hermes-Session-Key: lara-ohr            (optional)
{"input":"<text>","session_id":"<id>","instructions":"<ephemeral instructions>",
 "model":"...","provider":"...","model_options":{...}}
-> 202 {"run_id":"run_<hex>","status":"started","replayed":false}

GET /v1/runs/{run_id}/events        (Accept: text/event-stream; Last-Event-ID: <seq> to resume)
: open
id: 0
data: {"event":"message.delta","run_id":"run_...","timestamp":1.7e9,"delta":"Heute ","seq":0}
...
data: {"event":"run.completed","run_id":"...","output":"...","usage":{...},"runtime":{...},"seq":N}
: stream closed
```

Note the two streams differ: run events put the name **inside** the JSON (`"event"`), session-chat streams use a real SSE `event:` line (`_sse_frame(payload, event=name)`) [V] (`api_server.py:211-217`, `api_server_runs.py:1294-1297`).

### 2.2 Interface B — dashboard / TUI-gateway WebSocket

What it is [V]: `tui_gateway/server.py` "is the method and event catalog behind the Ink TUI (`hermes --tui`), the Desktop app and the embedded dashboard PTY bridge" (programmatic-integration.md). Reached at `/api/ws` on the dashboard backend (`hermes serve`/`hermes dashboard`, port 9119) or on the gateway's own listener.

* **Framing** [V] (`HA/tui_gateway/AGENTS.md`, "Transport"): "Newline-delimited JSON-RPC frames over a WebSocket (`/api/ws`, `tui_gateway/ws.py`) or stdio". Three frame kinds: client->server method calls, server->client **requests** (`approval`, `clarify`, `sudo`, `secret`, `vault.*`, ...), and server->client `event` notifications.
* **Ready frame** [V] (`HA/tui_gateway/ws.py:664-670`): `{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{"skin":...,"change_events":true,"heartbeat":true,"replay_epoch":...}}}`.
* **Contract** [V]: generated from Pydantic models; `HA/apps/shared/src/gateway-contract.openrpc.json` has `openrpc 1.3.2`, `info.version "1"`, **265 methods**, 54 `x-canonical-methods`, 16 server requests, 79 notifications. "the dispatcher rejects unknown param keys (`4000` + key path)" (`tui_gateway/AGENTS.md`).
* **Core methods** [V]: `session.create`, `session.resume`, `session.list`, `prompt.submit` (`PromptSubmitParams`: `text`, `voice_turn`, `voice_context`, `surface`, `queued`, `interrupted`, truncation fields — `HA/tui_gateway/contracts/prompt_voice.py`), `session.interrupt`, `session.steer`, `approval.respond`, `clarify.respond`, `file.attach`, `image.attach_bytes`, `model.options`, `command.dispatch`, `slash.exec`.
* **Events** [V]: `message.delta`, `message.complete`, `tool.start`, `tool.generating`, `tool.complete`, `gateway.ready`, `request.cancel`, `notification.show`, `status.update`, `session.reclaimed`, `subagent.*`, `voice.*`, ... (OpenRPC `x-notifications`).
* **Approvals are server->client JSON-RPC requests** [V] (programmatic-integration.md, "Server->client requests"):

  ```
  <- {"jsonrpc":"2.0","id":"srq-7","method":"approval","params":{"session_id":"…","request_id":"…","command":"rm -rf build","description":"…"}}
  -> {"jsonrpc":"2.0","id":"srq-7","result":{"choice":"once"}}
  ```

  and the client must announce support: "Once per connection, after `gateway.ready`, call `client.capabilities` with `{"server_requests": true}` ... A WebSocket client that never does is treated as a build that predates server->client requests: the gateway fails every such request for it immediately ... There is no grace path" — flagged in the doc as "**breaking for existing WebSocket integrations**".
* **Auth** [V] (`HA/hermes_cli/web_server_chat.py:204-300`, docstring of `_ws_auth_reason`; `HA/website/docs/user-guide/features/web-dashboard.md` "Authentication (gated mode)"):
  * loopback bind: legacy `?token=` (per-process session token);
  * any non-loopback bind engages the gate ("The gate is on if and only if the bind host is not `127.0.0.1`, `::1`, or `localhost`"; `--insecure` is a no-op since "the June 2026 hardening"); the server refuses to bind without an auth provider;
  * gated: `POST /auth/password-login` (`{"provider":"basic","username","password"}`) -> cookies -> `POST /api/auth/ws-ticket` -> `?ticket=` ("browser-minted, single-use, 30s TTL"), or `?token=` holding a session access token, or the **deployment session token** `HERMES_DASHBOARD_SESSION_TOKEN` (REST header `X-Hermes-Session-Token` or `Authorization: Bearer`, WS `?token=`);
  * providers: username/password (`dashboard.basic_auth`, "trusted networks only"), Nous OAuth, self-hosted OIDC, session-token.
  * close codes 4401 (credential), 4403 (host/origin), 4408 (peer) (`_ws_gate`).
* **TLS** [V]: none; uvicorn is configured without certificate options and the comment says "Gated mode runs behind a TLS terminator" (`HA/hermes_cli/web_server.py:1087-1092`).
* **Keepalive and detach behaviour** [V] (`HA/website/docs/user-guide/configuration.md:3467-3483`): `ws_ping_interval: 20.0`, `ws_ping_timeout: 20.0` on non-loopback binds; `ws_orphan_reap_grace_s: 20.0` ("Grace before a WS-detached session is reaped"); `ws_orphan_activity_stale_s: 600.0` ("A client-absent turn that is still actively producing keeps running to completion detached — closing the laptop, backgrounding the mobile app ... no longer cancels healthy long turns").
* **Reconnect** [V]: `session.resume` returns `inflight` (the running turn) and `open_requests` (unanswered approvals/clarify), so a reconnecting client can re-render and still answer (programmatic-integration.md, "Rebuilding the in-flight turn on reconnect").
* **Attachments** [V]: `file.attach {session_id, name, data_url}`, `image.attach_bytes`; the Webapp stages browser files with a 16 MiB limit (`HA/website/docs/user-guide/desktop.md`).
* **Voice turn flag** [V]: `prompt.submit` with `voice_turn: true` routes the turn to the `auxiliary.voice_chat` model (`HA/tui_gateway/methods_prompt.py:908`, `prompt_turn.py:751`).
* **Audio** [V]: on the same listener, see section 3.
* **Local-only variants** [V]: `hermes gateway attach --json` mints a single-use, 30 s, loopback-only ticket for the gateway's own `/api/ws` ("from loopback, with **no `Origin` header**"); remote use goes through SSH (`HA/website/docs/developer-guide/gateway-internals.md` "`hermes gateway attach`"; `gateway-ssh-clients.md`). Not usable from a phone without SSH.
* **In flux** [V]: the docs describe "Moving a dashboard backend to the gateway" and exit code 78 for an old `hermes serve` after `backend_migration.json`; the gateway can now serve the Webapp itself (`webapp.enabled`, port 9120) and "the gateway still serves native clients" (`web-dashboard.md`, `desktop.md`, `cli-commands.md`).

Assessment [I]: this is the richest interface (voice-turn routing, streaming TTS, attachments, slash commands, multi-client attach) and it is what every Desktop-like client uses, but it is the internal contract of first-party UIs: 265 methods, strict parameter validation, at least one documented breaking handshake change in 2026, and a process/port migration in progress. A third-party Android project (`Hy4ri/hermes-mobile`) runs a **daily** CI job to re-sync the OpenRPC snapshot [V] (`.github/workflows/gateway-contract-sync.yml`, `cron: '23 4 * * *'`).

### 2.3 Interface C — ACP

[V] `hermes acp` "starts a stdio JSON-RPC server speaking ACP" for VS Code, Zed, JetBrains; capabilities: session creation, prompt, streamed chunks, tool-call events, permission requests, session fork, cancel, authentication (programmatic-integration.md; `HA/website/docs/user-guide/features/acp.md`: "letting ACP-compatible hosts talk to Hermes over stdio"). The `hermes-acp` toolset "intentionally excludes ... messaging delivery and cronjob management". No network transport is documented [V]; a phone could only use it through an SSH-spawned process [I]. Not suitable.

### 2.4 Interface D — messaging adapters, webhooks, A2A

* **Platforms** [V] (`HA/plugins/platforms/`): a2a, bluebubbles, buzz, dingtalk, discord, email, feishu, google_chat, irc, line, matrix, mattermost, **ntfy**, photon, qqbot, raft, signal, simplex, slack, sms, teams, telegram, wecom, weixin, whatsapp, whatsapp_cloud; in core: `api_server`, `webhook`, `msgraph_webhook`. Home Assistant is an official plugin outside core (`HA/website/docs/user-guide/messaging/homeassistant.md`).
* **Voice on platforms** [V]: voice notes on Telegram, Discord, WhatsApp, Slack, Signal are transcribed automatically; replies can be sent as voice (Telegram Opus `.ogg` bubble etc.) (`HA/website/docs/user-guide/features/tts.md`, "Platform Delivery", "Voice Message Transcription (STT)").
* **Inbound webhooks** [V]: `http://your-server:8644/webhooks/<route-name>`, HMAC-validated, prompt templates, `deliver` to a platform, `deliver_only` (no LLM), `cron_job` trigger (`HA/website/docs/user-guide/messaging/webhooks.md`). Meant for services calling Hermes, not for a chat client.
* **Outbound webhooks** [V]: `hooks.outbound` in `config.yaml` POSTs signed JSON (`X-Hermes-Signature-256`) for plugin-hook events such as `on_session_end`, `subagent_stop`, `post_tool_call`; "Fire-and-forget ... best-effort, not guaranteed" (`HA/website/docs/user-guide/features/hooks.md`, "Outbound Webhooks"). Needs an HTTP endpoint the server can reach, so not a phone directly.
* **A2A** [V]: Agent2Agent v1.0 over HTTP, plugin platform, port 9900 (`HA/website/docs/user-guide/messaging/a2a.md`). Designed for agent-to-agent calls; no audio. Possible but an odd fit [I].
* **Relay** [V]: "Relay is **experimental**. The wire contract, auth scheme, and configuration may change without a deprecation cycle" (`HA/website/docs/user-guide/messaging/relay.md`).

### 2.5 Interface E — your own platform plugin

[V] A directory plugin with `kind: platform` registers an adapter via `ctx.register_platform(...)`; plugins can also add dashboard routes under `/api/plugins/<name>/...`, RPC methods `plugin.<id>.<name>` and hooks (`on_session_start`, `on_session_end`, `on_stream_end`, `pre_approval_request`, `on_human_input_request`, ...) (`HA/website/docs/user-guide/features/hooks.md` hook catalog; programmatic-integration.md "Plugins add `plugin.<plugin_id>.<name>` methods").

Working example [V]: `Adolanium/hermes-gadget-sdk` (MIT, v0.6.0 of 2026-11-17, 593 stars) — "Hold a button. Ask Hermes. Hear the answer." Its plugin opens its own WebSocket (`ws://<host>:8765/gadget`, optional `tls_cert`/`tls_key` for `wss://`), authenticates devices by key/HMAC with pairing codes, accepts push-to-talk PCM16 audio, lets Hermes do STT and TTS, streams reply audio back (PCM16 or Opus 24 kbps), maps Hermes approvals to yes/no `prompt` messages ("TALK to yes and CANCEL to no"), and each device "is a direct-message chat", so "sessions and memory, `/new`, `/stop`, `/voice`, model routing, approvals and delivery" work as on any platform (`repos/hermes-gadget-sdk/docs/protocol.md`, `docs/hermes-integration.md`). Heartbeat: server `ping` every `heartbeat_s` (`# heartbeat_s: 20` in the commented config example), connection considered dead after 3x that interval. Its own doc says it relies in places "on behavior that is not a formal API".

Assessment [I]: conceptually the closest existing design to "button -> speak -> hear answer" including proactive speech, but it is a third-party plugin on a moving plugin API and assumes a permanently connected device. Good phase-2 candidate and an excellent protocol reference; not the v1 target.

---

## 3. Voice: what Hermes offers server-side

**TTS providers** [V] (`HA/website/docs/user-guide/features/tts.md`): Edge TTS (default, free, no key), ElevenLabs, OpenAI, MiniMax, Mistral Voxtral, Google Gemini, xAI, DeepInfra, and local NeuTTS, KittenTTS, Piper ("local, 44 languages"); plus custom command providers and Python plugin providers. Per-request caps e.g. Edge 5000 chars, OpenAI 4096; longer text is split sentence-aware.

**STT providers** [V] (same doc): `stt.provider: "local" | "groq" | "openai" | "mistral" | "xai" | "elevenlabs" | "deepinfra"`; default local faster-whisper (models tiny ... large-v3, turbo), `stt.language` hint; custom command and plugin providers. Streaming STT (`stt.streaming: true`) only with openai / xai / elevenlabs / plugins (`HA/website/docs/user-guide/features/voice-mode.md`, "Live transcription").

**Where a remote client can send and receive audio** [V] — only on the dashboard backend, `HA/hermes_cli/web_routers/audio.py`:

| Route | Shape | Notes |
|---|---|---|
| `POST /api/audio/transcribe` | `{"data_url":"data:audio/<type>;base64,...","mime_type":"audio/m4a"}` -> `{"ok":true,"transcript":"...","provider":"..."}` | max 25 MiB (`_MAX_TRANSCRIPTION_UPLOAD_BYTES = 25 * 1024 * 1024`); accepted MIME: aac, flac, m4a, mp3, mp4, mpeg, ogg, wav, webm; empty transcript is a success |
| `POST /api/audio/speak` | `{"text":"..."}` -> `{"ok":true,"data_url":"data:audio/mpeg;base64,...","mime_type":"...","provider":"..."}` | whole clip; mp3/ogg/wav/flac |
| `WS /api/audio/transcribe-stream` | client: `{"sample_rate":N}` (8000-48000), binary s16le mono PCM, `{"eos":true}`; server: `{"type":"partial"}`, `{"type":"final","transcript":...}`, `{"type":"error"}` | needs a streaming STT provider, else error -> fall back to the POST |
| `WS /api/audio/speak-stream` | client: `{"text":"..."}` increments, `{"done":true}`, `{"stop":true}`; server: `{"type":"start","sample_rate":N,"channels":1}`, binary int16 PCM, `{"type":"end"}` / `{"type":"fallback"}` | sentence-chunked; works with Edge per sentence |
| `GET /api/audio/voice-config` | resolved STT/TTS provider settings incl. credential, for "client-direct voice" | Desktop calls providers directly and keeps keys in memory only (voice-mode.md, "Desktop remote: client-direct voice") |
| `POST /api/audio/voice-live/session` | WebRTC SDP offer for OpenAI `gpt-live-1` full-duplex mode | Desktop only; OpenAI key; "$0.05 per minute of session time" per the doc |
| `POST /api/audio/tts-lease`, `/api/audio/stt-lease` | warm-up of local engines | optional |

The JSON-RPC methods `voice.*` / `wake.*` drive the **server's** microphone and speaker, not the client's [V] (`HA/tui_gateway/methods_voice.py`; confirmed by a client author: `repos/hermes-mobile-client/AGENTS.md`: "the `voice.*`/`wake.*` RPCs (those drive the *server's* mic and speaker). Mobile voice goes through `/api/audio/*` with on-device capture").

**Voice model routing** [V]: `auxiliary.voice_chat` lets spoken turns run on a different, faster model with reasoning off by default; applies to CLI/TUI/Desktop voice and "voice notes on messaging platforms" (voice-mode.md, "Voice chat model"). The API server has no equivalent flag (no match for `voice_turn` in `gateway/platforms/api_server*.py`) [V]; an API client reaches the same effect with `model` / `provider` / `model_options` per request [I].

Whether the gateway's Webapp listener (9120) also serves `/api/audio/*` is [I] likely (`HA/hermes_cli/web_server_surface.py:50-52`: the Webapp "relays through the host's /api/audio/* endpoints instead") but not exercised here.

---

## 4. Proactive direction: how the agent can reach the phone

**Scheduler** [V] (`HA/website/docs/user-guide/features/cron.md`): durable cron jobs created by chat (`/cron`), CLI (`hermes cron create "0 8 * * *" "Morning briefing"`), the agent's `cronjob` tool, the dashboard, or `POST /api/jobs`. Schedules: relative one-shot delays, intervals, natural day/time, cron expressions, ISO timestamps; per-job timezone; `context_from` chaining; no-agent script mode; `approvals.cron_mode: deny`. Also `/heartbeat every 10m <prompt>` for a recurring turn inside one session (`heartbeat.md`).

**Delivery targets** [V] (cron.md "Delivery options"): `origin`, `local` (files under `~/.hermes/cron/output/`), `telegram[:chat[:thread]]`, `discord`, `slack`, `whatsapp`, `signal`, `matrix`, `mattermost`, `email`, `sms`, `homeassistant`, `dingtalk`, `feishu`, `wecom`, `weixin`, `bluebubbles`, `qqbot`, `bot-chat`, `all`, comma lists; and `ntfy` (`HA/website/docs/user-guide/messaging/ntfy.md`: `cronjob(action="create", schedule="every 1h", deliver="ntfy", ...)`, "uses NTFY_HOME_CHANNEL"). "The agent's final response is automatically delivered to the configured `deliver:` target". There is **no** `api_server` or generic HTTP delivery target [V] (not in the table; API jobs default to `deliver: "local"`).

**`hermes send`** [V]: "a small, scriptable CLI that pushes a message to any messaging platform Hermes is already configured for ... you don't need a running gateway, you don't need an LLM" (`HA/website/docs/guides/pipe-script-output.md`), e.g. `hermes send ntfy:alerts-channel "Done!"`.

**ntfy adapter** [V] (`HA/plugins/platforms/ntfy/plugin.yaml`, ntfy.md): `NTFY_TOPIC`, `NTFY_SERVER_URL` (self-hosted possible), `NTFY_TOKEN`, `NTFY_PUBLISH_TOPIC`, `NTFY_HOME_CHANNEL`; outgoing-only mode documented; 4096-char message cap; "the topic name itself is the identity ... anyone who knows the topic can talk to the agent" unless the topic is access-controlled.

**What a custom client can subscribe to**

| Option | How | Cost / caveats | Status |
|---|---|---|---|
| P1: pull on activation | on each wake-up `GET <ntfy>/<topic>/json?poll=1&since=<last id>`; speak "you have N new notes" | no background connection at all; not instant | [V] ntfy API (`docs/subscribe/api.md`: `poll=1`, `since=<id>`); design [I] |
| P2: live subscription inside the app's foreground service | long-lived `GET <topic>/json` (or `/sse`, `/ws`) | one persistent socket; this is what the ntfy app does: "Instant delivery allows you to receive messages on your phone instantly, even when your phone is in doze mode ... achieved with a foreground service" | [V] ntfy `docs/subscribe/phone.md`; battery impact on the Pixel [U] |
| P3: ntfy Android app + broadcast | register for `io.heckel.ntfy.MESSAGE_RECEIVED` (extras `topic`, `message`, `title`, `priority`, ...) | needs the ntfy app installed; receiver registered at runtime by our service | [V] ntfy phone.md "Automation apps" |
| P4: Hermes plugin with hooks | like `server-plugin/hermes-push` in hermes-mobile-client (hooks `on_stream_end`, approval polling -> Expo push) or `kaishi00/hermes-conduit-notifier` used by alfatherapp (FCM relay) | you maintain server-side code against a moving plugin API; FCM needs a Firebase project | [V] `repos/hermes-mobile-client/server-plugin/hermes-push/README.md`; `repos/alfatherapp/README.md` |
| P5: dashboard WebSocket events | stay attached to `/api/ws` and receive `notification.show`, `background.complete`, `cron.changed`, ... | permanent socket with 20 s pings | [V] event names in OpenRPC; unsuitable for a dozing phone [I] |
| P6: gadget plugin | agent tools `gadget_speak`, `gadget_display`; `speech_queue_wait_s: 600`, `speech_overflow_target` | permanent socket; third-party | [V] `repos/hermes-gadget-sdk/docs/hermes-integration.md` |
| P7: Home Assistant / Telegram / Signal | existing apps' own push | not spoken by our app unless we read notifications | [V] delivery table |

ntfy server: v2.38.2 (2026-11-24), Apache-2.0; ntfy-android Apache-2.0 [V] (`gh api repos/binwiederhier/ntfy/releases/latest`).

Recommendation [I]: start with P1 (zero idle cost, trivially debuggable with `curl`), self-host ntfy with access control, then measure P2 inside the foreground service that already exists for BLE scanning. A reminder flow is then: user says "erinnere mich um 15 Uhr an X" -> the agent calls its `cronjob` tool with `deliver="ntfy"` -> ntfy -> phone speaks it (P2) or mentions it at the next activation (P1).

Bonus [I]: the same ntfy topic can act as a **cache for spoken briefings**. A cron job every 15-30 min with a prompt such as "one-sentence weather + next appointment, plain German" and `deliver="ntfy"` to a dedicated topic lets the menu entry "Wetter" answer instantly from `?poll=1&since=latest`, even when the agent would need 10 s. Whether cron output is retrievable through `/api/jobs/{id}` instead is [U] (the handler returns the job definition and last-run state; an output field was not confirmed).

---

## 5. Existing clients

### 5.1 serrebidev/BlindPilot-Android (Kotlin, MIT)

Repository facts [V]: `gh api repos/serrebidev/BlindPilot-Android` -> MIT, created 2026-10-20, 11 commits, 1 star, last push 2026-11-24; releases v0.1.0 (prerelease flag), v0.2.0, v0.2.1, **v0.2.2 (2026-11-24, not flagged prerelease)**; README still says "This is a pre-release." Clone @ `e836504f3626be1bf2e2a34b14bf3621aae8075a`. Build: `compileSdk = 37`, `targetSdk = 37`, `minSdk = 28`, AGP `9.4.1`, Gradle `9.8.2`, single dependency `com.squareup.okhttp3:okhttp:4.12.0` (`app/build.gradle.kts`, `build.gradle.kts`, `gradle/wrapper/gradle-wrapper.properties`). Plain Views, `org.json`, blocking I/O on worker threads.

**Hermes interface: interface B (dashboard WebSocket).** Everything is in one file, `app/src/main/java/org/blindpilot/android/backends/HermesBackend.kt` (446 lines) [V]:

* Endpoint normalisation: any input becomes `<scheme>://host[:port][/prefix]/api/ws` (lines 424-438); refuses credentials in the URL.
* TLS rule: "Hermes credentials require a wss:// address. Cleartext is allowed only for explicitly configured loopback development." (line 175); manifest has `android:usesCleartextTraffic="false"`.
* Auth, two modes (lines 192-225): `"token"` -> `?token=<session token>`; `"password"` -> `POST <base>/auth/password-login` with `{"provider":"basic","username":..,"password":..}`, then `POST <base>/api/auth/ws-ticket` -> `{"ticket"}` -> `?ticket=`. In-memory cookie jar per connection.
* Handshake (lines 244-252): wait up to 60 s for an `event` frame with `params.type == "gateway.ready"`, then send `client.capabilities {"server_requests": true}` with a 5 s wait and tolerate refusal ("Older gateways may reject or ignore this optional capability advertisement").
* Session (lines 255-273): `session.resume {session_id, omit_messages}` or `session.create {model?, provider?, reasoning_effort?}`; durable id taken from `session_key` / `resumed` / `stored_session_id`; replays `open_requests`.
* Turn: `prompt.submit {session_id, text}` (120 s), then consume events until `message.complete`; idle deadline 900 s, re-armed on every frame; "Hermes is still working." status every 30 s.
* Events handled (lines 343-366): `message.delta`, `thinking.delta`, `reasoning.available`, `status.update`, `notification.show`, `tool.start`, `tool.complete`, `subagent.*`, `message.complete` (status `interrupted`/`cancelled`/`error`/`failed`), `error`.
* Approvals and questions (lines 369-415): supports **both** generations — server->client JSON-RPC requests (`method` = `approval` / `clarify` / `sudo` / `secret`, answered with a response frame carrying the same `id`) and the older event form (`approval.request` -> RPC `approval.respond {request_id, choice}`). Choices default to `["once","session","always","deny"]`; anything unrecognised becomes `deny`. Unknown server requests get JSON-RPC error `-32601`.
* Cancel: `session.interrupt {session_id}` then close code 1000 (lines 240-243).
* Attachments: `file.attach {session_id, name, data_url}`, 25 MB cap, then the returned host path is appended to the prompt text (lines 75-95).
* Other calls: `model.options`, `session.list {limit:200}`, `session.active_list`, `complete.slash`, `slash.exec`.
* Reconnect: **none**. One socket per operation; on any close: "Hermes disconnected. The prompt was not resent." A resumed session that reports `running` is observed, not resubmitted (lines 58-62).
* Secret redaction in surfaced errors (`safe()`), `retryOnConnectionFailure(false)`.
* Token storage [V] (`data/Store.kt`, 120 lines): AES/GCM key in `AndroidKeyStore` (alias `blindpilot`, no user-authentication binding), ciphertext in `SharedPreferences("secrets")`.
* Tests [V]: unit tests exist for `ActionLoop`, `ChatGptAuth`, `CloudBackend`, `ReportIssue`, `Store`, `ModelCatalog`; **no test references Hermes** (`grep -rn -i hermes app/src/test app/src/androidTest` -> no hits), although its own plan lists "gateway contract tests".
* Hermes version targeted: **not stated anywhere** [V] (no pin in README/docs); the dual approval handling shows it was written against a late-2026 gateway [I].
* Voice with Hermes: none [V].

Quality [I]: compact, defensive, readable; synchronous design with a polling queue (100 ms) is simple but not coroutine-friendly; no automated proof against a real or mocked gateway.

Worth reusing: `Store.kt` Keystore pattern (as a template, MIT); the endpoint/credential hygiene rules; the server-request answer logic if the WebSocket path is ever needed.

### 5.2 darth-modder/hermes-mobile-client (TypeScript, Expo / React Native, MIT)

Repository facts [V]: MIT, created and last pushed 2026-09-22, >= 100 commits, 4 stars, single release v0.1.0 (2026-09-22). Clone @ `2e35092ef42fb1ba21b81e848e24e25d4e66e95b`. Expo `~57.0.20`, React Native `0.86.3`, TypeScript `6.0.3`, vitest `4.1.10`; targets API 36. "There is no CI in this repo. `npm run check` is the gate" (README).

* **Hermes interface: interface B**: "Your phone connects to a `hermes serve` gateway you run yourself, over WebSocket JSON-RPC (`/api/ws`) and REST (`/api/*`)" (README).
* **Pinned upstream**: `src/upstream/UPSTREAM.json` -> commit `ee84ccd8bd13d0025e98bb6be8ceb93303e3bdff`, `syncedAt 2026-09-08` [V]; that commit dates 2026-09-08 (`gh api .../commits/ee84ccd...`), i.e. between releases `v2026.9.7` and `v2026.9.11`. It vendors upstream TypeScript (`shared/json-rpc-gateway.ts`, `lib/gateway-events.ts`, `lib/reconnect-backoff.ts`, `types/hermes.ts`, ...).
* Auth [V]: token mode `?token=` (dev/loopback only), password mode `POST /auth/password-login` + `POST /api/auth/ws-ticket` with the ticket carried in the subprotocol list `['hermes-gateway-v1', 'hermes-gateway-ticket.<ticket>']` (`src/gateway/dial.ts`), Nous OAuth with refresh (`src/net/auth/*`). Secrets in `expo-secure-store`, metadata in MMKV (`src/connections/secure.ts`).
* Reauth ladder [V] (`src/net/auth/ladder.ts`): 401 / WS close 4401 -> one refresh+retry -> `NeedsLoginError`; 403 / 4403 -> stop; everything else -> backoff, never a login prompt.
* Reconnect [V]: full-jitter exponential backoff, base 300 ms, cap 15 s (`src/upstream/lib/reconnect-backoff.ts`); redial + 5 s `ping` probe on foreground/network return (`src/gateway/lifecycle.ts`); after reconnect `session.resume` and both `session.reclaimed` outcomes handled. Decision log D2/D10 (`project-planning/DECISIONS.md`) records measured server behaviour: 20 s orphan grace, 600 s activity-stale interrupt, and "Android freezes the JS thread for the whole background window, so a JS timer cannot close anything on schedule".
* Approvals [V]: RPCs `approval.respond`, `clarify.respond`, `sudo.respond`, `secret.respond` driven by `*.request` events (`src/gateway/session-connection.ts:1281-1352`). The string `client.capabilities` / `server_requests` does **not occur** in `src/` (grep) [V]. Current Hermes requires that call (section 2.2), so approvals from this client are very likely refused by Hermes v0.23.x [I]. In addition the methods `sudo.respond` and `secret.respond` that it calls are **absent from the current contract** (`HA/apps/shared/src/gateway-contract.openrpc.json` lists `approval.respond` and `clarify.respond` but neither of those two) [V].
* Voice [V]: `POST /api/audio/transcribe` with an m4a recording as data URL and `POST /api/audio/speak` (`src/voice/api.ts`, `recorder.ts`, `tts.ts`), timeouts 180-600 s.
* Push [V]: optional server plugin `server-plugin/hermes-push` (Python, hooks `on_session_start`, `on_session_end`, `on_stream_end`, `pre_approval_request`; approval detection by polling `tools.approval.has_blocking_approval`; device registry under `/api/plugins/hermes-push/devices`; payload only `{kind, session_id, title}`), sent through Expo push. README: "Server-pushed notifications are not configured: what you get are local notifications".
* Cleartext [V]: `plugins/withCleartextTraffic.js` sets `<base-config cleartextTrafficPermitted="true">` because "`hermes serve` has no TLS option at all" and "`<domain>` takes a host, never a CIDR range".
* Tests [V]: 85 `*.test.ts` files (session reducer, auth ladder, URL guard, reconnect, push), plus Python `unittest` for the plugin. Verified "extensively on an Android emulator against throwaway gateways" (README).

Quality [I]: unusually well documented decisions and invariants (`AGENTS.md`, `DECISIONS.md`), strong unit-test coverage, but frozen two months behind a server that changes daily, and built on React Native — the wrong base for a locked-screen, BLE-driven, audio-only Kotlin service (JS thread suspension is documented by the project itself).

Worth reusing as *knowledge*, not code: the reauth ladder rules, the reconnect/reclaim contract, `docs/CONNECTING.md` (host-side recipe: bind to the tailnet address, register an auth provider, set `HERMES_DASHBOARD_BASIC_AUTH_SECRET` or every restart signs clients out), the `hermes-push` plugin as a template for a server-side notifier.

### 5.3 Other projects found (not named in the planning notes)

All [V] by `gh api search/repositories` on 2026-11-27 and a shallow read where noted.

| Project | Lang / licence | Interface used | Why it matters here |
|---|---|---|---|
| `Hy4ri/hermes-mobile` (509 stars, v3.6.2, on F-Droid) | Kotlin + Compose, **Apache-2.0** | B (dashboard REST + `/api/ws`) | Same licence as our repo. Typed Kotlin contract (`data/ws/contract/*.kt`, `HermesWsClient.kt` with `client.capabilities`), **daily OpenRPC contract sync in CI**, 512 test files, password/OAuth auth plus a client-certificate (mTLS) selection flow (`ui/authlogin/CertificatePromptController.kt`), global cleartext opt-in (`res/xml/network_security_config.xml`), German UI strings, `/api/audio/transcribe`, `/api/audio/speak` and a Kotlin client for the `speak-stream` PCM WebSocket (`ui/chat/components/GatewaySpeechConnection.kt`). Toolchain: Kotlin 2.4.21, OkHttp 5.5.1, kotlinx-serialization 1.13.0, Retrofit 3.0.0, compileSdk 37 |
| `JusticeFist/alfatherapp` ("AllFather", 22 stars) | Kotlin, MIT | B + host plugin `conduit_push` | Declares a `VoiceInteractionService` and a `RecognitionService` in its manifest ("Assistant: Hold the power button"), Wear OS companion, dictation, read-aloud, wake phrase; `docs/protocol.md` is a compact, source-cited description of login -> ticket -> `/api/ws` -> `client.capabilities` -> reconnect |
| `Adolanium/hermes-gadget-sdk` (593 stars, v0.6.0) | C++/Python, MIT | E (own platform plugin, `ws://host:8765/gadget`) | Push-to-talk voice devices for Hermes; documented, versioned device protocol with audio both ways, yes/no approvals, proactive speech |
| `shang1626/hermes-dialog-app` | Kotlin, MIT | **A (API server)**: `POST /v1/runs` + `GET /v1/runs/{id}/events` with `Last-Event-ID`, `/stop`, `/steer`, `/api/sessions/{id}/messages` | The only Android client found on the interface recommended here. Caveats: old toolchain (AGP 8.5.2, Kotlin 1.9.24, targetSdk 34), README lists routes absent from current upstream (`/v1/artifacts/upload`, `/health/sysinfo`) and says server patches are needed for some features |
| `JemaOS/hermes-onyx` | Kotlin, **FSL-1.1-MIT** (source-available, not OSI-open until the MIT conversion) | B + A (`/v1/chat/completions`) | "hands-free voice", Wear OS, Android Auto; read for ideas only, do not copy code |
| `angel12/mercury-voice` | Swift, MIT | B incl. `speak-stream`, client-direct voice via `/api/audio/voice-config` | Voice-conversation client; documents speak-stream edge cases |
| `AF360/TalkToHermes` | Swift/Python, MIT | push-to-talk with self-hosted STT/TTS chains (faster-whisper, Wyoming, Piper) | Privacy-first voice pipeline ideas |
| `unsupportedpastels/mercury`, `kaishi00/hermes-conduit`, `cogwheel0/conduit` (GPL-3.0), `nesquena/hermes-webui` | various | B or A | general clients; lower relevance |

Observation [I]: almost all rich clients use interface B and pay for it with continuous contract tracking; the one API-server client is small and simple.

---

## 6. Android side

### 6.1 Network access while locked, screen off, in Doze

* Official statement [V] (developer.android.com/training/monitoring-device-state/doze-standby): in Doze the system "Suspends network access", "Ignores wake locks", defers alarms/jobs/syncs; "An app that is partially exempt can use the network and hold partial wake locks during Doze and App Standby" (battery-optimisation exemption list). The page does not mention foreground services under Doze restrictions.
* AOSP, `refs/heads/android17-release` [V]:
  * `frameworks/base/core/java/android/net/NetworkPolicyManager.java`: `FOREGROUND_THRESHOLD_STATE = ActivityManager.PROCESS_STATE_BOUND_FOREGROUND_SERVICE` and

    ```java
    public static boolean isProcStateAllowedWhileIdleOrPowerSaveMode(int procState, int capability) {
        if (procState == PROCESS_STATE_UNKNOWN) { return false; }
        return procState <= FOREGROUND_THRESHOLD_STATE
                || (capability & ActivityManager.PROCESS_CAPABILITY_POWER_RESTRICTED_NETWORK) != 0;
    }
    ```
  * `services/core/java/com/android/server/net/NetworkPolicyManagerService.java` (`updateRulesForAllowlistedPowerSaveUL`, chain `FIREWALL_CHAIN_DOZABLE`): allowed are the power-save allowlists **and** every uid for which `isProcStateAllowedWhileIdleOrPowerSaveMode(...)` is true.
  * `services/core/java/com/android/server/power/PowerManagerService.java` (`setWakeLockDisabledStateLocked`): in device idle a partial wake lock is disabled only if the app is not allowlisted **and** `state.mProcState > ActivityManager.PROCESS_STATE_BOUND_FOREGROUND_SERVICE`.
  * `VoiceInteractionManagerServiceImpl.startLocked()` binds the active assistant with `Context.BIND_AUTO_CREATE | Context.BIND_FOREGROUND_SERVICE | Context.BIND_INCLUDE_CAPABILITIES | Context.BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS`.
* Conclusions:
  * [I, strong] A process with a **running foreground service** (proc state `FOREGROUND_SERVICE` <= threshold) keeps network access and effective partial wake locks in Doze on AOSP 17. The app needs such a service anyway for BLE scanning.
  * [I] A process that hosts the **active `VoiceInteractionService`** is bound by the system with `BIND_FOREGROUND_SERVICE`, which should place it at `BOUND_FOREGROUND_SERVICE` and therefore also inside the allowed range. The resulting proc state was not read off a device.
  * [U] Pixel 10 Pro behaviour after hours of real Doze (deep idle, Adaptive Battery, possible vendor tweaks) — must be measured.
  * Belt and braces [V]: the app may also ask for the battery-optimisation exemption (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`); the Play restriction on that intent does not bind a sideloaded app, and the docs' own table lists "Task automation app" as an acceptable case.
* Foreground-service types [V] (developer.android.com/develop/background-work/services/fgs/service-types): network access is not tied to a type. `connectedDevice` needs one of `BLUETOOTH_CONNECT` / `BLUETOOTH_SCAN` / ... ; `microphone` is subject to while-in-use rules ("you cannot create a `microphone` foreground service while your app is in the background ... with a few exceptions"); `mediaPlayback` has no runtime prerequisite; `shortService` ~3 minutes; `specialUse` is free-form. (Details belong to the BLE/audio research, not here.)

### 6.2 Keep a WebSocket open, or connect on demand?

* [V] Hermes pings non-loopback WebSockets every 20 s with a 20 s pong timeout and reaps a detached session after a further 20 s (`ws_ping_interval`, `ws_ping_timeout`, `ws_orphan_reap_grace_s`). A phone that wants to keep `/api/ws` alive must therefore wake at least every 20 s, all day.
* [V] The Android docs advise against app-owned persistent connections ("we strongly recommend you use FCM if possible, rather than maintaining your own persistent network connection").
* [V] Third-party evidence: hermes-mobile-client dropped its background-socket handling and reconnects on return (DECISIONS D10).
* [I] The interaction model here is "button -> one request -> one spoken answer". Connect on demand costs one TCP + TLS 1.3 handshake (a few hundred ms on a VPN) against an agent turn that takes seconds; OkHttp's pool reuses the connection for follow-ups within its idle window.
* **Decision [I]: connect on demand over HTTP for requests; no socket kept open in v1.** A persistent stream is only justified for instant proactive speech (option P2), and then towards ntfy, not Hermes.

### 6.3 VPN versus TLS reverse proxy

Facts:
* [V] Hermes offers no TLS on either listener (sections 2.1, 2.2). API server default bind `127.0.0.1` (`DEFAULT_HOST`).
* [V] The API key equals remote code execution if leaked (startup-guard text); the dashboard docs say the password provider is for "trusted networks only ... or reachable only over a VPN" and recommend binding to the Tailscale IP.
* [V] Apps targeting API 28+ refuse cleartext unless opted in; a `<domain>` rule takes a hostname or literal IP (developer.android.com/privacy-and-security/security-config, "Cleartext traffic"; hermes-mobile-client comment on CIDR).
* [V] Android VPN basics: always-on VPN is started by the system at boot and kept running; "Block connections without VPN" exists; per-app allow/deny lists exist (developer.android.com/develop/connectivity/vpn).
* [V, anecdotal] Tailscale's issue tracker has open Android reports about battery use and connectivity transitions (tailscale/tailscale #3363 "Mobile battery usage still bad in some situations", #18281 "Stateless VPN connection on mobile devices to improve battery life", #19783 connectivity loss on Wi-Fi -> mobile data, #20517 slow relay -> direct transition). No official Tailscale statement about Doze was found in this search; behaviour on the Pixel is [U].

Options:

| Option | Pros | Cons |
|---|---|---|
| WireGuard (official app, always-on) + plain HTTP to the server's tunnel IP | stateless protocol, nothing to keep alive, no public exposure | needs a cleartext exception for that one IP/host in `network_security_config.xml`; key rotation manual |
| WireGuard/Tailscale + **HTTPS** inside the tunnel (Caddy or `tailscale serve` in front of `127.0.0.1:8642`) | no cleartext exception, defence in depth, works unchanged if the tunnel is ever replaced | one more moving part; with Tailscale certificates no private CA is needed, with an internal CA see 6.5 |
| TLS reverse proxy on the public internet | no VPN app on the phone | the whole agent (terminal tool) hangs on one bearer token; add mTLS or do not do it |

Recommendation [I]: VPN first (WireGuard preferred for predictability; Tailscale acceptable if already in use), HTTPS inside it, API server kept on loopback behind the proxy. First-request latency and success after hours of Doze is a device test (section 8).

### 6.4 Storing the token

* [V] Android Keystore: "Key material never enters the application process"; keys can be bound to TEE or StrongBox (`setIsStrongBoxBacked(true)`, "For most apps, StrongBox is not necessary") (developer.android.com/privacy-and-security/keystore).
* [V] `setUnlockedDeviceRequired(true)`: "such keys cannot be used ... until the device is unlocked"; `setUserAuthenticationRequired(true)`: "The key can only be used if the user has been authenticated". **Both must stay off**, otherwise the token is unreadable exactly when the phone is locked [I].
* [V] Jetpack `security-crypto` 1.1.0 (2025-07-30): "Deprecated all APIs in favour of existing platform APIs and direct use of Android Keystore" (developer.android.com/jetpack/androidx/releases/security). So no `EncryptedSharedPreferences` in new code.
* Pattern [I]: AES-256-GCM key in `AndroidKeyStore` (purpose encrypt|decrypt, no auth binding), 12-byte IV + ciphertext stored in DataStore or a private file; decrypt per request, never log; redact the token in debug logs and in any exported diagnostics. BlindPilot's `Store.kt` is a 30-line example of exactly this.
* [U] Behaviour before the first unlock after a reboot (credential-encrypted storage is locked; the service would have to be direct-boot aware). Out of scope for v1 unless the owner wants "works after reboot without unlocking".

### 6.5 Private CA / certificates

[V] (developer.android.com/privacy-and-security/security-config):
* Default for targetSdk >= 28: `cleartextTrafficPermitted="false"`, trust anchors `system` only; user-installed CAs are **not** trusted unless the app opts in.
* Custom CA: put the PEM/DER in `res/raw/` and reference it:

  ```xml
  <network-security-config>
      <domain-config>
          <domain includeSubdomains="true">lara.home.example</domain>
          <trust-anchors><certificates src="@raw/my_ca"/></trust-anchors>
      </domain-config>
      <debug-overrides>
          <trust-anchors><certificates src="user"/></trust-anchors>
      </debug-overrides>
  </network-security-config>
  ```
* Android 17 (API 37): certificate transparency is on by default, but "If any trust-anchors is `"user"` or inline (i.e., `"@raw/cert.pem"`), disable the verification" — a bundled private CA keeps working without a CT opt-out.
* Pinning (`<pin-set>`) is available; always with a backup pin.

[I] Because the APK is the owner's own build, bundling the CA in `res/raw` is the cleanest option; a public-CA certificate obtained by DNS-01 (or a `*.ts.net` certificate) avoids the question altogether. A runtime "trust this certificate?" dialog (as in `Hy4ri/hermes-mobile`) is unnecessary for a single-user app.

---

## 7. Recommendation

### 7.1 Interface for version 1

**Target interface A, the API server**, in two steps:

1. **Spike:** `POST /api/sessions` once, then `POST /api/sessions/{id}/chat` per menu action. One request, one JSON answer, `approvals.unattended_mode: deny` protects against dangerous commands without any client code.
2. **v1:** `POST /v1/runs` with `Idempotency-Key`, `GET /v1/runs/{id}/events` (SSE) for sentence-wise speech, `GET /v1/runs/{id}` as fallback after a dropped stream, `POST /v1/runs/{id}/stop` for the "abort" button, and an `approval.request` handler that answers `deny` unless the owner later designs a spoken confirmation.

Reasons [I, each resting on verified facts in section 2]: officially recommended for language-agnostic HTTP clients; machine-readable capability document; one static bearer secret instead of login/cookie/30-second ticket; request/response fits connect-on-demand and Doze; resumable event stream and idempotent creation fit a mobile link; per-request instructions and model selection give short, fast answers; smallest surface to keep compatible with a server that ships daily.

What it does not give: audio, push, slash commands, attaching to a Telegram/Desktop conversation. Covered by on-device speech (or later `/api/audio/*`), ntfy, and plain-language prompts.

When to reconsider interface B: if server-side streaming TTS, the `voice_chat` model slot, or shared sessions with Hermes Desktop become requirements. Then start from `Hy4ri/hermes-mobile` (Apache-2.0) and `alfatherapp/docs/protocol.md`, and use the deployment session token (`HERMES_DASHBOARD_SESSION_TOKEN`) instead of password login [I].

### 7.2 Server configuration to request from the owner [I unless marked]

```
# ~/.hermes/.env
API_SERVER_ENABLED=true
API_SERVER_KEY=<openssl rand -hex 32>          # [V] >= 16 chars enforced
# keep API_SERVER_HOST=127.0.0.1 and put Caddy / tailscale serve in front, or bind to the tunnel IP
# optional proactive channel [V]:
NTFY_SERVER_URL=https://ntfy.<internal host>
NTFY_TOPIC=lara-in            NTFY_PUBLISH_TOPIC=lara-out
NTFY_HOME_CHANNEL=lara-out    NTFY_TOKEN=tk_...
```
`config.yaml`: leave `approvals.unattended_mode: deny` (default) [V]; consider `gateway.api_server.max_concurrent_runs: 2`.
Pin the Hermes version (stable tag, no canary) and re-test the app after each server update.

### 7.3 Minimal Kotlin client outline

Versions verified on 2026-11-27 from Maven Central / Google Maven metadata [V]:

| Artifact | Version |
|---|---|
| Kotlin | 2.4.21 (2.5.0 is at RC2) |
| Android Gradle Plugin | 9.5.3 (9.6.0 in alpha); Gradle 9.9.2 |
| `com.squareup.okhttp3:okhttp`, `okhttp-sse`, `mockwebserver3` | 5.5.1 (2026-10-22) |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` (use `-android` of the same train) | 1.11.0 |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.13.0 |
| `androidx.datastore:datastore-preferences` | 1.3.0 |
| `androidx.lifecycle:lifecycle-service` | 2.11.1 |
| `androidx.core:core-ktx` | 1.19.1 |
| `androidx.media3:*` | 1.11.1 |
| (alternative HTTP stack) `io.ktor:ktor-client-*` | 3.6.2 |
| (optional) `com.google.crypto.tink:tink-android` | 1.24.0 |
| compileSdk seen in current Android clients | 37 |

OkHttp 5.5.1 SSE entry points [V] (`okhttp-sse/src/main/kotlin/okhttp3/sse/EventSources.kt` @ tag `parent-5.5.1`): `EventSources.createFactory(callFactory)` and `EventSources.processResponse(response, listener)`.

Sketch (untested, illustrative):

```kotlin
// hermes/HermesApi.kt
@Serializable data class RunRequest(
    val input: String,
    @SerialName("session_id") val sessionId: String? = null,
    val instructions: String? = null,
    val model: String? = null, val provider: String? = null,
    @SerialName("model_options") val modelOptions: JsonObject? = null)
@Serializable data class RunAccepted(@SerialName("run_id") val runId: String, val status: String, val replayed: Boolean = false)

sealed interface HermesEvent {
    data class Delta(val text: String) : HermesEvent                 // "message.delta" -> field "delta"
    data class Tool(val name: String, val done: Boolean) : HermesEvent // "tool.started" / "tool.completed" -> field "tool"
    data class Approval(val raw: JsonObject, val choices: List<String>) : HermesEvent // "approval.request"
    data class Done(val output: String) : HermesEvent                // "run.completed" -> field "output"
    data class Failed(val status: String, val error: String?) : HermesEvent // run.failed / run.cancelled / run.interrupted
}

class HermesApi(private val base: HttpUrl, private val token: suspend () -> String, private val http: OkHttpClient) {
    suspend fun capabilities(): JsonObject                 // GET  /v1/capabilities   (check features.run_events_sse etc.)
    suspend fun createSession(id: String, title: String)   // POST /api/sessions {"id","title"}
    suspend fun startRun(req: RunRequest, idempotencyKey: String): RunAccepted   // POST /v1/runs -> 202
    fun events(runId: String, lastSeq: Int? = null): Flow<HermesEvent>           // GET  /v1/runs/{id}/events, header Last-Event-ID
    suspend fun status(runId: String): JsonObject          // GET  /v1/runs/{id}  (fallback: field "output")
    suspend fun stop(runId: String)                        // POST /v1/runs/{id}/stop
    suspend fun approval(runId: String, choice: String, requestId: String? = null) // POST /v1/runs/{id}/approval
}
```

Rules for the implementation [I]:
* One `OkHttpClient`: `connectTimeout` ~5 s, `readTimeout` 0 for SSE calls (the server sends `: keepalive` every 10 s; treat 30 s of silence as dead), `callTimeout` on JSON calls, `retryOnConnectionFailure(false)` for `POST /v1/runs` and retry manually with the **same** `Idempotency-Key`.
* SSE parsing: ignore comment lines (`: open`, `: keepalive`, `: stream closed`); the event name is in the JSON field `event`; remember `seq` and resume with `Last-Event-ID`; on `replay.truncated` or 404 fall back to `GET /v1/runs/{id}`.
* Map HTTP 401 -> spoken "Zugang abgelehnt" (no retry), 409 -> session problem (create a new session), 429 / 503 with `Retry-After` -> spoken "Lara ist beschaeftigt", network errors -> one retry then a spoken error. Never prompt for credentials by voice.
* Adopt `session_id` returned by the server (compression can rotate it).
* Sentence splitter on `message.delta` feeding TTS; strip Markdown, URLs and emoji defensively even though the instructions forbid them.
* Hide the client behind an interface (`AssistantBackend`) so interface B or the gadget protocol can be added later; same for `SpeechIn` / `SpeechOut`.
* Tests: `mockwebserver3` fixtures for 202 + SSE happy path, stream cut + resume, 409/429, approval -> deny, stop.
* Debug build: an activity/adb broadcast that runs a canned prompt and logs timings (connect, first delta, done).

### 7.4 Mapping spoken-menu entries to Hermes requests

Mechanism [V]: every request may carry ephemeral instructions (`instructions` on `/v1/runs`, `system_message` on session chat) layered on the agent's own prompt, plus `model` / `provider` / `model_options`.

Shared instructions (German, example) [I]:

```
Du sprichst ueber ein Hoergeraet mit <Name>. Antworte auf Deutsch in hoechstens zwei kurzen
Saetzen, insgesamt unter 35 Woertern. Reiner Fliesstext: kein Markdown, keine Listen, keine
Emojis, keine Links. Uhrzeiten und Zahlen so, wie man sie spricht. Wenn es mehr gibt, ende
mit dem Wort "Mehr?". Fuehre keine veraendernden Aktionen aus, ausser ich bitte ausdruecklich darum.
```

| Menu entry | `input` (example) | Notes |
|---|---|---|
| Wetter | "Wetter heute und morgen an meinem Wohnort: Temperatur, Regen, eine Empfehlung." | [U] depends on which web/search tool or skill the instance has; no bundled weather skill was found in `HA/skills` |
| Termine | "Welche Termine habe ich heute noch? Hoechstens drei, mit Uhrzeit." | [V] a `google-workspace` skill is bundled (`HA/skills/productivity/google-workspace`); [U] whether it is configured |
| To-dos | "Nenne meine drei wichtigsten offenen Aufgaben." | [V] Hermes' built-in `todo` toolset is the agent's per-session task list, not a personal to-do store (`HA/website/docs/reference/tools-reference.md`); the owner's real to-do source (kanban, a notes skill, MCP server) is [U] |
| Auswahl / "Mehr?" | "Ja, weiter." in the same session | session continuity makes follow-ups cheap |
| Aktionen | e.g. "Erinnere mich in 20 Minuten an den Herd." | agent uses its `cronjob` tool; delivery via ntfy (section 4); the app speaks the confirmation sentence |
| Freie Frage | transcript of the push-to-talk recording | same pipeline |

Session policy [I]: one explicit session per day (`lara-ohr-YYYYMMDD`, created with `POST /api/sessions`), so follow-ups work, context stays small and a fresh memory snapshot is loaded daily; long-press "new conversation" simply creates the next id.

Latency controls [I]: a fast model with reasoning disabled via `model_options`; play an earcon at request start and on each `tool.started`; hard client cap (e.g. speak at most ~300 characters, then ask "Mehr?" locally); optional precomputed briefings through cron + ntfy (section 4).

Safety [I]: BLE presses are not authentication (planning notes, section 8). Keep `unattended_mode: deny`, answer every `approval.request` with `deny` in v1, and put a sentence like the last one in the instructions above. A later "spoken confirmation + long press" flow can send `once` — never `session` or `always`.

### 7.5 Speech in v1 (boundary to the audio research)

[I] Menu labels and error messages must be spoken instantly and without network -> on-device TTS. For answers and for dictation there are three interchangeable back ends behind `SpeechIn` / `SpeechOut`: on-device Android services, Hermes `/api/audio/transcribe` + `/api/audio/speak` (needs the dashboard listener and its session token), or a self-hosted STT/TTS service called directly. Start on-device, add the Hermes relay if recognition quality through the hearing-aid microphone is insufficient.

---

## 8. Load-bearing claims and how to test them on the Pixel

| # | Claim | Status | Device / lab test |
|---|---|---|---|
| L1 | The API server answers `POST /api/sessions/{id}/chat` and `POST /v1/runs` with a static bearer key as documented | verified in source and docs, not run | From a laptop on the VPN: `curl -H "Authorization: Bearer $KEY" $BASE/v1/capabilities`; create a session; one chat turn; one run with `curl -N .../events`. Record the JSON as test fixtures |
| L2 | A process with a foreground service can open a new TCP/TLS connection while the phone is locked, screen off and in Doze | AOSP source verified; device behaviour unknown | Debug build with the FGS; `adb shell dumpsys battery unplug`; `adb shell dumpsys deviceidle force-idle`; trigger a request via `adb shell am broadcast` (and later the BLE button); expect HTTP 200 and log the timings. Repeat after 3 h and overnight of real idle without `force-idle`. `adb shell dumpsys deviceidle unforce; adb shell dumpsys battery reset` afterwards |
| L3 | The process hosting the active `VoiceInteractionService` has a proc state <= BOUND_FOREGROUND_SERVICE and thus network in Doze even without its own FGS | inferred from the bind flags | With the app as default assistant and no FGS: `adb shell dumpsys activity processes <pkg> | grep -i procstate`; then repeat L2 |
| L4 | First request after long Doze succeeds through the VPN within an acceptable time | unknown | Same as L2 through WireGuard and through Tailscale, always-on enabled; measure connect time, TLS time, failures; compare with the VPN app exempted from battery optimisation |
| L5 | An agent turn for a menu entry returns in a tolerable time and obeys the brevity instructions | unknown | 20 runs per menu entry against the real instance; log time to first `message.delta`, time to `run.completed`, word count; try with and without a fast model / reasoning off |
| L6 | A dropped SSE stream can be resumed (`Last-Event-ID`) or recovered by `GET /v1/runs/{id}` | verified in source | Toggle airplane mode for 5 s mid-run; the client must finish speaking the answer exactly once |
| L7 | Retrying `POST /v1/runs` with the same `Idempotency-Key` does not start a second run | verified in docs | Kill the connection right after sending; retry; expect the same `run_id` and header `Idempotency-Replayed: true` |
| L8 | Dangerous commands are denied without client action on the synchronous endpoint and surface as `approval.request` on `/v1/runs` | verified in docs/source | Ask the agent to run `rm -rf` on a scratch directory through both endpoints; observe the outcome; answer `deny` |
| L9 | `POST /v1/runs/{id}/stop` ends the turn quickly enough for an "abort" button | verified in source (cooperative) | Stop during a long tool call; measure time to `run.cancelled` |
| L10 | Hermes can push to a self-hosted ntfy topic from cron and from `hermes send` | verified in docs/plugin manifest | `hermes send ntfy:<topic> "test"`; a one-shot cron job with `deliver="ntfy"`; `curl -s "$NTFY/<topic>/json?poll=1&since=10m"` |
| L11 | A long-lived ntfy subscription inside the foreground service delivers within seconds in Doze at an acceptable battery cost | ntfy docs claim it for their app; unknown for ours | 24 h soak: publish every 30 min, log delivery delay; compare battery stats with and without the subscription |
| L12 | The Keystore-wrapped token can be decrypted while the device is locked | verified by API docs (no auth binding) | Call the decrypt path from the FGS with the screen locked for > 1 h |
| L13 | TLS to the reverse proxy validates with the chosen certificate strategy on API 37 | verified in docs | Release build (not debuggable) against the real host; then present a wrong certificate and expect failure |

---

## 9. Corrections to the inherited planning notes

File: `<planning-notes>` (sections 3, 7, 8, 9, 12 were read).

1. **"Hermes Gateway" is treated as one thing reached by WebSocket.** Wrong simplification. Hermes has two separate listeners and three documented protocols: the API server (HTTP + SSE, port 8642, inside `hermes gateway`) and the dashboard backend (`hermes serve` / `hermes dashboard`, port 9119, REST + WebSocket `/api/ws`), plus ACP over stdio. The notes never mention the API server, which is the interface Hermes itself recommends for a "language-agnostic HTTP client".
2. **"Hermes-Gateway, API/WebSocket-Version festlegen" (phase 4).** There is no versioned public WebSocket API to pick: the WebSocket is the generated first-party contract (`hermes-gateway-v1`, OpenRPC `info.version "1"`, 265 methods) and changes continuously; the API server offers feature detection through `/v1/capabilities` instead of version numbers.
3. **BlindPilot-Android: "Pre-Release; ... Lizenz und Gateway-Version pruefen".** Licence is MIT (verified). The latest release v0.2.2 (2026-11-24) is not flagged as a prerelease on GitHub, although the README still calls the project a pre-release. No Hermes version is pinned or documented, and there are no tests for its Hermes backend.
4. **hermes-mobile-client: "Sitzungen und Freigaben".** True for the upstream commit it vendors (2026-09-08). Two of the RPCs it uses (`sudo.respond`, `secret.respond`) no longer exist in the current contract, and it never sends `client.capabilities {server_requests: true}`, which current Hermes requires before it will route approvals to a WebSocket client, so approvals are very likely broken against v0.23.x (inferred from two verified sources). Last commit 2026-09-22, one release. "Noch jung" understates this: it is effectively frozen.
5. **"Vorhandene Hermes-Clients koennen besonders bei Gateway-Protokoll, Authentifizierung, Antworten und Freigaben Zeit sparen."** Only if the WebSocket interface is chosen. For the API server nothing from either named client applies except the Keystore pattern and operational lessons.
6. **The list of related projects is incomplete.** Not in the notes but more relevant: `Hy4ri/hermes-mobile` (Kotlin, Apache-2.0, actively tracking the contract), `JusticeFist/alfatherapp` (Kotlin, assistant role + voice + Wear OS), `Adolanium/hermes-gadget-sdk` (push-to-talk devices via a Hermes platform plugin), `shang1626/hermes-dialog-app` (Kotlin on the API server runs API).
7. **"Fernzugriff auf Hermes: authentifiziert und verschluesselt (z. B. TLS/VPN)".** Correct as a requirement; add the fact that Hermes cannot terminate TLS itself on either listener and that a non-loopback dashboard bind always forces an auth provider (`--insecure` is ignored since mid-2026).
8. **Nothing in the notes covers the proactive direction.** Hermes has no push channel for API clients; delivery goes through messaging platforms (ntfy being the simplest), outbound webhooks, or plugins.
9. **Speech.** The notes leave "TTS/STT-Implementierung" open; relevant fact: Hermes' STT/TTS are reachable for remote clients only via the dashboard backend (`/api/audio/*`), not via the API server, and the `voice.*` RPCs control the server's own audio devices.

No statement in the notes about Hermes was found to be an invented product or API; the two named repositories exist and are MIT-licensed.

---

## 10. Risks and open questions

1. **Upstream churn** (1093 commits in three days; releases every few days; documented breaking changes in June, July and later 2026). Mitigation: pin the server version, snapshot `/v1/capabilities`, fixture-based client tests, update server and app together.
2. **One bearer key = full agent, including the terminal tool.** Mitigation: VPN-only reachability, Keystore, log redaction, optional mTLS at the proxy, a dedicated Hermes profile with a reduced toolset for the ear client (`/p/<profile>/...` routing with its own key) [I].
3. **Latency of agent turns is unmeasured** [U]. Spoken UX needs earcons, a fast model, and possibly precomputed briefings.
4. **LLM brevity is not guaranteed.** Client-side truncation and a "Mehr?" paging mechanism are required.
5. **`/api/sessions/{id}/chat/stream` cancels the run when the stream drops** [V]. Use `/v1/runs` for anything that must survive a flaky link.
6. **Whether a dropped synchronous `POST .../chat` cancels or completes the turn** is [U] (not traced).
7. **Event buffer and status retention** (300 s / 3600 s) [V]: a phone that reconnects later than that loses the answer unless it reads `GET /api/sessions/{id}/messages`.
8. **VPN in Doze** [U]: first-packet delay or failure after hours idle; test both WireGuard and Tailscale.
9. **Approvals from an unauthenticated BLE button**: keep deny-by-default; design spoken confirmation separately.
10. **ntfy topic secrecy and integrity**: self-host with ACL and token; do not act on inbound ntfy messages as commands in v1.
11. **Dashboard backend is being moved into the gateway** [V]: if `/api/audio/*` is used later, host and port may change (9119 -> 9120) and the auth story with it.
12. **Tool availability on the owner's instance** (weather source, calendar, real to-do store) is [U] and decides whether the three menu entries work at all.
13. **Reboot before first unlock** [U]: credential-encrypted storage and therefore the token are unavailable; decide whether that case matters.
14. **Third-party code reuse**: `JemaOS/hermes-onyx` is FSL-licensed (no code reuse into an Apache-2.0 project); `cogwheel0/conduit` is GPL-3.0; MIT/Apache projects are fine with attribution.

---

## 11. Source index

Hermes (clone `HA/` @ `28ef6e4e7d1df3015bf786dea6b0d355baf5c211`; GitHub: `https://github.com/NousResearch/hermes-agent/blob/28ef6e4e7d1df3015bf786dea6b0d355baf5c211/<path>`):
* `website/docs/developer-guide/programmatic-integration.md` — the three protocols, server requests, reconnect, run status table.
* `website/docs/user-guide/features/api-server.md` — API server guide (endpoints, idempotency, sessions, auth, limits).
* `gateway/platforms/api_server.py` — flags (48-57), limits (170-189), SSE frame (211), auth (1429), routes (1565), capabilities (2357), sessions (2578, 2798, 3031, 3078), jobs (3331).
* `gateway/platforms/api_server_runs.py` — run events (141, 225), `POST /v1/runs` (768), events SSE (1256), approval (1358), stop (1508).
* `gateway/platforms/tcp_site.py` — no TLS.
* `tui_gateway/AGENTS.md`, `tui_gateway/ws.py`, `tui_gateway/contracts/prompt_voice.py`, `tui_gateway/methods_prompt.py`, `apps/shared/src/gateway-contract.openrpc.json` — WebSocket contract.
* `hermes_cli/web_server_chat.py` (204-300), `hermes_cli/web_server.py` (1087), `hermes_cli/dashboard_auth/routes.py` (506, 592, 624) — WebSocket auth, uvicorn config, login/ticket routes.
* `hermes_cli/web_routers/audio.py` — audio REST and WebSocket routes.
* `website/docs/user-guide/features/web-dashboard.md`, `user-guide/desktop.md`, `reference/cli-commands.md`, `user-guide/configuration.md` (3424-3483) — dashboard auth, remote backends, `hermes serve`, Webapp, WebSocket keepalive settings.
* `website/docs/user-guide/features/tts.md`, `voice-mode.md`, `developer-guide/streaming-tts.md` — speech.
* `website/docs/user-guide/features/cron.md`, `heartbeat.md`, `hooks.md`, `user-guide/messaging/ntfy.md`, `webhooks.md`, `a2a.md`, `relay.md`, `homeassistant.md`, `guides/pipe-script-output.md`, `developer-guide/completion-backlog-delivery.md`, `plugins/platforms/ntfy/plugin.yaml` — proactive direction.
* `website/docs/user-guide/security.md`, `user-guide/features/memory.md`, `user-guide/features/acp.md`.
* GitHub API: releases list, `compare/v0.23.4...main`, file contents at `?ref=v0.23.4`.

Clients (clones under `.../scratchpad/repos/`):
* `BlindPilot-Android` @ `e836504` — `app/src/main/java/org/blindpilot/android/backends/HermesBackend.kt`, `data/Store.kt`, `app/build.gradle.kts`, `AndroidManifest.xml`.
* `hermes-mobile-client` @ `2e35092` — `README.md`, `AGENTS.md`, `docs/CONNECTING.md`, `project-planning/DECISIONS.md`, `src/gateway/*`, `src/net/auth/*`, `src/voice/*`, `src/upstream/UPSTREAM.json`, `server-plugin/hermes-push/*`, `plugins/withCleartextTraffic.js`.
* `hermes-mobile` (Hy4ri) @ `ff10d4b`, `alfatherapp` @ `39a01b3`, `hermes-gadget-sdk` @ `2cc49a2`, `hermes-onyx` @ `2ff4081`, `hermes-dialog-app` @ `de54e29` — READMEs, protocol docs, selected sources as cited.

Android:
* https://developer.android.com/training/monitoring-device-state/doze-standby
* https://developer.android.com/develop/background-work/services/fgs/service-types
* https://developer.android.com/privacy-and-security/security-config
* https://developer.android.com/privacy-and-security/keystore
* https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.Builder
* https://developer.android.com/jetpack/androidx/releases/security
* https://developer.android.com/develop/connectivity/vpn
* AOSP `android17-release`: `frameworks/base/core/java/android/net/NetworkPolicyManager.java`, `services/core/java/com/android/server/net/NetworkPolicyManagerService.java`, `services/core/java/com/android/server/power/PowerManagerService.java`, `services/voiceinteraction/.../VoiceInteractionManagerServiceImpl.java` (fetched with `?format=TEXT` from android.googlesource.com; local copies in `.../scratchpad/research/aosp/`).

Other:
* ntfy docs: https://github.com/binwiederhier/ntfy/blob/main/docs/subscribe/phone.md and `docs/subscribe/api.md`; release v2.38.2.
* Maven Central / Google Maven `maven-metadata.xml` for the library versions; https://services.gradle.org/versions/current; `square/okhttp` tag `parent-5.5.1`.
