# Verification: Hermes API claims (cluster hermes-api)

Date: 2026-11-27. Method: read-only code and docs review at the release tag. Nothing was built, installed or executed; no request was sent to a running Hermes. Every "confirmed" below rests on a line I opened myself. Anything that depends on runtime behaviour is marked as needing a server test.

## Sources

- `HA4/` = `<repos>/hermes-agent-v0.23.4/` — shallow clone of `NousResearch/hermes-agent`, HEAD `8fc0374447313e276f6b9a2764bfc750190c966a`, `git describe` = `v0.23.4`, working tree clean.
- Authenticity check: local blob hashes equal `gh api repos/NousResearch/hermes-agent/contents/<file>?ref=v0.23.4` for `gateway/platforms/api_server.py` (`8e4213ac…`), `api_server_runs.py` (`868ed43a…`), `api_server_run_idempotency.py` (`42343773…`), `tcp_site.py` (`77c9f698…`).
- The earlier report read `main` @ `28ef6e4e` (the other clone, `repos/hermes-agent/`). I diffed the files it relied on against the tag: `api_server.py`, `api_server_run_idempotency.py`, `api_server_authority_runs.py`, `session_api_turn.py`, `session_pending_controls.py`, `tools/approval.py`, `tools/approval_context.py`, `gateway/config_env.py`, `gateway/run_api.py`, `run_turn_runner.py`, `plugins/platforms/ntfy/adapter.py`, `hermes_cli/send_cmd.py`, `web_routers/audio.py`, `web_server.py`, `api-server.md`, `security.md` are byte-identical; `api_server_runs.py` differs only by a steer helper added after the release (14 lines). So its quotations hold at the release; where it is wrong, the cause is interpretation, not the branch.
- GitHub API: `gh api repos/NousResearch/hermes-agent/releases/latest`, `.../git/ref/tags/v0.23.4`, `.../git/tags/3ac54e10…`, `.../compare/v0.23.4...main`.

## Verdict table

| # | Claim | Verdict |
|---|---|---|
| 1 | Latest release v0.23.4 (2026-11-24) | confirmed (one label in the earlier report is wrong: `3ac54e10…` is the annotated tag object, the commit is `8fc03744…`) |
| 2 | API server, port 8642, part of the gateway, static bearer `API_SERVER_KEY` | partly — the enable switch is the key itself, not `API_SERVER_ENABLED` |
| 3 | Paths, methods, shapes, event names | partly — paths and methods are right; approval body, approval event field name and the event list are different on a real v0.23.4 gateway |
| 4 | `Idempotency-Key` on `POST /v1/runs`; resume with `Last-Event-ID` | partly — both exist; resume is in-memory, bounded, and expires |
| 5 | Per-request `instructions` and model override | partly — both exist; "force" is not something the code guarantees |
| 6 | Dangerous tool calls denied by default or surfaced as `approval.request` | partly — default value confirmed; on a real gateway the instant deny very likely never applies to API turns, they park on a prompt |
| 7 | No audio on the API server; `/api/audio/*` only on the 9119 dashboard backend | partly — first half confirmed; "only on `hermes serve`/`dashboard`" is wrong, the gateway process serves the same app |
| 8 | ntfy adapter, `deliver="ntfy"`, `hermes send` | partly — all exist; `hermes send` needs `--to`, the upstream doc example is wrong |
| 9 | Neither listener terminates TLS | confirmed |

---

## Claim 1 — latest release

- `gh api repos/NousResearch/hermes-agent/releases/latest` -> `tag_name v0.23.4`, `published_at 2026-11-24T08:17:06Z`, `target_commitish 8fc0374447313e276f6b9a2764bfc750190c966a`, not prerelease, not draft.
- Release list: the only newer entry is the prerelease `canary.20261125T064800Z-v0.23.4`.
- `gh api .../git/ref/tags/v0.23.4` -> object `3ac54e1012be1784e00a0f23bdfdce46b769675d`, `type: tag`; `gh api .../git/tags/3ac54e10…` -> `object.sha 8fc03744…`, `type: commit`, tagger "Hermes Release Automation" 2026-11-24T08:17:03Z.
- The release body says "**Release Date:** November 23, 2026" (the commit is dated 2026-11-23T23:04:14-08:00, which is 2026-11-24 in UTC).
- `gh api .../compare/v0.23.4...main` -> `ahead_by 1098` today.
- `HA4/pyproject.toml:7` `version = "0.0.0"` (stamped at release). PyPI `hermes-agent` latest is `0.19.0` uploaded 2026-07-20 — PyPI does not carry this release; the release notes name `hermes update`, the installer and Docker `nousresearch/hermes-agent:stable`.

## Claim 2 — the API server, its configuration and what happens without a key

Confirmed:
- Defaults: `HA4/gateway/platforms/api_server.py:168-169` `DEFAULT_HOST = "127.0.0.1"`, `DEFAULT_PORT = 8642`.
- It is a gateway platform adapter (`class APIServerAdapter`, `Platform.API_SERVER`); the upstream e2e helper starts it with `python -m gateway.run` (`HA4/tests/e2e/core/dashboard/_issue_helpers.py:160-176`).
- Auth: `api_server.py:1429-1447` `_check_auth`: `if auth_header.startswith("Bearer "): token = auth_header[7:].strip(); if secrets_match(token, expected_key): return None` else 401 `{"error":{"message":"Invalid gateway API key (API_SERVER_KEY)","type":"gateway_auth_error","code":"gateway_auth_failed"}}`. `secrets_match` is `hmac.compare_digest` (`HA4/gateway/platforms/_shared.py:34-43`).
- `/health` and `/v1/health` have no auth decorator (`api_server.py:2270-2272`), every other route is wrapped by `_require_auth` or `_admit_api_agent_request`.

Correction — what actually enables it (`HA4/gateway/config_env.py:121-129`):

```python
def _api_server(config: GatewayConfig) -> None:
    """Require a usable key: an unauthenticated adapter refuses to start and the reconnect watcher would spin."""
    key = getenv("API_SERVER_KEY")
    if not _has_usable_api_server_key(key):
        return
    extra = _enable_from_env(config, Platform.API_SERVER, pop_marker=True, warn=False).extra
    extra["key"] = key
    csv_extras(extra, (("cors_origins", getenv("API_SERVER_CORS_ORIGINS")),))
    env_extras(extra, (("port", "API_SERVER_PORT", INT), ("host", "API_SERVER_HOST"), ("model_name", "API_SERVER_MODEL_NAME")))
```

- `API_SERVER_ENABLED` is documented (`api-server.md:24`, `:830`) but no code in `gateway/` reads it at this tag (`grep -rn API_SERVER_ENABLED` hits only `agent/secret_scope.py:218` (a pass-through whitelist), `hermes_cli/web_server_messaging.py:31` (a dashboard card) and `hermes_cli/config_defaults.py:3026` (a description)). A usable `API_SERVER_KEY` alone turns the platform on; `API_SERVER_ENABLED=true` without a usable key does nothing. Setting both is harmless.
- "Usable" = `has_usable_secret(key, min_length=16)` (`HA4/hermes_cli/auth.py:305-312`): at least 16 characters, not in `{"*","**","***","changeme","your_api_key","your_api_key_here","your-api-key","placeholder","example","dummy","null","none"}`, not a `your_…_here` / `sk-xxxx` / all-`x` shape.
- `platforms.api_server.enabled: false` in `config.yaml` beats the env key (`config_env.py:95-118`; test `HA4/tests/gateway/test_config.py:1461-1493`).
- config.yaml form (`api-server.md:837-852`): `gateway.api_server.{enabled, port, host, key, cors_origins, model_name, max_concurrent_runs, history_tool_output_max_chars}`; "also accepted under `gateway.platforms.api_server:` or a top-level `platforms.api_server:`". Env values overwrite config values when set (`env_extras` assigns `extra[key]`).
- Other env keys: `API_SERVER_HOST`, `API_SERVER_PORT`, `API_SERVER_CORS_ORIGINS`, `API_SERVER_MODEL_NAME`.

No key set:
- From env only: the platform is simply not enabled, no listener, no error.
- Enabled through config.yaml without a usable key: `api_server.py:3874-3903` logs "Refusing to start: API_SERVER_KEY is required for the API server, including loopback-only binds" (or "…is a placeholder or too short (<16 chars). This endpoint dispatches terminal-capable agent work — a guessable key is remote code execution.") and `connect()` sets the non-retryable fatal error `api_server_key_invalid` (`:3912-3931`); the rest of the gateway keeps running. Recovery: set the key, then `/platform resume api_server`.
- The unauthenticated branch of `_check_auth` exists "for tests/manual wiring on the default listener only" (docstring, `:1430-1432`).

Other facts that matter:
- Bind is loopback by default; a phone needs `API_SERVER_HOST=<VPN or LAN address>` or a reverse proxy. A network-reachable bind with `terminal.backend: local` only logs a warning (`:3955-3968`).
- An `Origin` header that is not allow-listed is answered 403 (`cors_middleware`, `:661-677`); a native HTTP client sends none.
- Limits: body 10 MB (`MAX_REQUEST_BYTES`, `:186`), concurrent runs 10 -> 429 `rate_limit_exceeded` with `Retry-After: 1` (`:3521-3536`), draining -> 503 `gateway_draining` with `Retry-After: 1` (`:1183-1189`).

## Claim 3 — routes, shapes, events

Route table at the tag (`api_server.py:1565-1608`, `api_server_runs.py:285-293`): all claimed paths and methods exist.

```
POST /api/sessions/{session_id}/chat          POST /api/sessions/{session_id}/chat/stream
POST /v1/runs        GET /v1/runs/{run_id}    GET /v1/runs/{run_id}/events
POST /v1/runs/{run_id}/approval | /clarify | /steer | /resolve-unknown | /stop
GET  /v1/capabilities
```

Capabilities (`api_server.py:48-57`, `:2356-2382`): `features` holds `chat_completions, chat_completions_streaming, responses_api, responses_streaming, run_submission, runs_idempotency {supported, durable, retention_seconds}, run_status, run_events_sse, run_stop, run_steer, run_approval_response, tool_progress_events, approval_events, session_resources, model_options, session_chat, session_chat_streaming, session_fork, session_model_lock, reasoning_streaming, admin_config_rw:false, jobs_admin:false, memory_write_api:false, skills_api, audio_api:false, realtime_voice:false, session_continuity_header, session_key_header, run_unknown_resolution, cors`. Top level: `object "hermes.api_server.capabilities"`, `platform`, `model`, `auth {type:"bearer", required}`, `runtime`, `endpoints`. The endpoint needs the bearer key.

### The point the first pass missed: canonical mode is always on

`HA4/gateway/run_bootstrap.py:870` `await initialize_gateway_runtime(runner)` runs on every gateway start, and `HA4/gateway/session_authority.py:1562` sets `runner.session_authority = authority`. Every handler branches on `getattr(self.gateway_runner, 'session_authority', None) is not None`. So the "legacy" bodies quoted in the earlier report are dead code on a real gateway. Two upstream tests that start a real daemon show the live behaviour: `HA4/tests/gateway/test_api_pending_daemon.py` and `HA4/tests/e2e/core/dashboard/test_gateway_sse_approvals.py`. A client can detect the mode: `features.run_unknown_resolution` is `true` exactly then (`api_server.py:2365`).

### POST /api/sessions/{id}/chat (`api_server.py:2798-2867`, `:3030-3075`)

- The session must exist, else 404 `session_not_found`. Create with `POST /api/sessions` body `{"id"?, "title"?, "system_prompt"?, "model"?, "provider"?}` -> 201 `{"object":"hermes.session","session":{…}}`; an existing id -> 409 (`:2577-2628`).
- Body: `message` or `input` (string or multimodal list), `system_message` or `instructions` (string), `model`, `provider`, `model_options`, `author`.
- 200: `{"object":"hermes.session.chat.completion","status":"completed|failed|cancelled|incomplete","session_id":…,"message":{"role":"assistant","content":…},"usage":{…},"runtime":{…}}` plus header `X-Hermes-Session-Id`. A failed turn is still HTTP 200 with `status: "failed"`.
- `content` passes `_resolve_media_to_data_urls` (`:713-747`): an image the agent references becomes `![image](data:image/png;base64,…)` inside the text, up to 5 MB.
- Other answers: 429 (cap), 503 with header `x-should-retry: false` and message "Hermes is restarting. Your input is saved and will run once when it is back; do not resend it." (`api_server_authority_runs.py:24-33`, `session_api_turn.py:32-41`), 409 `permission_denied` for a session owned by another surface.

### POST /v1/runs (`api_server_runs.py:768-901`)

- Body: `input` (required; string, or a message list whose last item is used), `session_id`, `instructions`, `model`, `provider`, `model_options`, `conversation_history`, `previous_response_id`, `author`. Headers: `Idempotency-Key`, `X-Hermes-Session-Key`.
- 202 `{"run_id":"run_<32 hex>","status":"started","replayed":false}` (`:550-557`).
- `session_id` may name a session that does not exist yet (the daemon test posts `{'input': text, 'session_id': 'api-controls'}` cold). Without one the session id is the run id.
- A second run on a session that is busy is queued behind the first, not rejected (`HA4/tests/gateway/test_api_run_progress.py`).

### GET /v1/runs/{id} (`api_server_authority_runs.py:67-96`, `api_server_runs.py:460-470`)

`{"object":"hermes.run","model":…,"created_at":…,"run_id":…,"status":…,"session_id":…,"admission_id":…,"execution_generation":N,"pending_controls":[…],"output":"…","usage":{…}}`, plus `tool`/`progress_at` while a tool runs. `status`: `queued`, `running`, `waiting_for_approval`, `unknown` (not terminal), `completed`, `failed`, `cancelled`; `interrupted` and `stopping` come from the adapter's own record.

### GET /v1/runs/{id}/events (`api_server_runs.py:1256-1346`)

- Frames: first `: open`, then `id: <seq>\ndata: {"event":…,"run_id":…,"timestamp":…,…,"seq":<seq>}\n\n`. There is no `event:` line; the name is inside the JSON. `: keepalive` every 10 s (`CHAT_COMPLETIONS_SSE_KEEPALIVE_SECONDS = 10.0`). End: `: stream closed`.
- Events that the canonical path can emit (`_execute_run`, `:1131-1134`, passes only `stream_delta_callback`, `tool_start_callback`, `tool_complete_callback` and the control sink):
  - `message.delta` `{delta}`
  - `tool.started` `{tool, preview, tool_call_id}`; `tool.completed` `{tool, duration, error, tool_call_id, preview}`
  - `approval.request` `{kind:"approval", prompt_id, execution_generation, command, description, choices, timeout_s?}`; `approval.settled` `{prompt_id, execution_generation}` (`HA4/gateway/session_pending_controls.py:19-39`, `:63-73`)
  - `clarify.request` `{kind:"clarify", prompt_id, execution_generation, question, choices, multi_select}`; `clarify.settled`
  - terminal `run.completed` `{completed, partial, interrupted, output, usage, runtime}`, `run.failed` `{…, error}` or `{…, output}` for a turn that ran out of budget, `run.cancelled`, `run.interrupted`; also `run.queued` and `replay.truncated`.
- `message.interim`, `reasoning.available`, `subagent.start|complete`, `approval.responded`, `run.steered` are produced only by the legacy branch or the legacy control handlers. Treat them as optional.
- Field name trap: on this stream the approval id is `prompt_id`. On the chat-completions stream it is renamed to `request_id` (`session_api_turn.py:304-310`). The answer always sends it as `request_id`.

### POST /v1/runs/{id}/approval

`api_server_runs.py:1369-1372` hands off to `respond_run`, and `api_server_authority_runs.py:123`:

```python
if set(body) != {'request_id', 'execution_generation', field}:
    raise AuthorityRefusal('invalid_params')
```

- Body must be exactly `{"request_id":"<prompt_id>","execution_generation":<int>,"choice":"once|deny|session|always"}`; `choice` must be one of the prompt's `choices` (`session_pending_controls.py:106`). `{"choice":"once"}` alone -> 400 `invalid_params`.
- 200 `{"run_id":…,"status":"resolved"|"already_resolved","prompt_id":…}`. Wrong generation -> 409 `stale_generation`; unknown prompt -> 409 `approval_not_pending`. Both asserted by the daemon test (`test_api_pending_daemon.py:96-103`) and the e2e test (`test_gateway_sse_approvals.py:197-210`).
- Docs agree: "Canonical controls do not accept identityless or bulk responses." (`api-server.md:656-670`).

### POST /v1/runs/{id}/stop (`api_server_authority_runs.py:139-169`)

Running -> 200 `{"run_id":…,"status":"stopping","admission_id":…}`; queued -> cancelled and the status projection is returned; finished -> the status projection; `unknown` -> 409 `unknown_execution`.

### Smallest curl examples

Derived from the code and the two daemon tests above. Not executed by me.

```bash
BASE=http://127.0.0.1:8642
KEY='<API_SERVER_KEY, at least 16 characters>'
A="Authorization: Bearer $KEY"; J='Content-Type: application/json'

# 0. liveness and version, no auth
curl -s $BASE/health
#    {"status":"ok","platform":"hermes-agent","version":"<base version>"}
#    version comes from the install stamp or git (hermes_cli/version_info.py); expected "0.23.4",
#    but "unknown" on an install that has neither

# 1. capabilities
curl -s $BASE/v1/capabilities -H "$A"

# 2. synchronous turn (the session has to exist first)
curl -s -X POST $BASE/api/sessions -H "$A" -H "$J" -d '{"id":"ear-1"}'
curl -s -X POST $BASE/api/sessions/ear-1/chat -H "$A" -H "$J" \
     -d '{"message":"Wie wird das Wetter morgen?","instructions":"Antworte in ein bis zwei kurzen Sätzen."}'
#    200 {"object":"hermes.session.chat.completion","status":"completed","session_id":"ear-1",
#         "message":{"role":"assistant","content":"…"},"usage":{…},"runtime":{…}}

# 3. detached run
curl -s -X POST $BASE/v1/runs -H "$A" -H "$J" -H 'Idempotency-Key: 0b9c5e0e-6d0e-4a52-9a6e-2f0f3b3c8f11' \
     -d '{"input":"Wie wird das Wetter morgen?","session_id":"ear-1","instructions":"Antworte in ein bis zwei kurzen Sätzen."}'
#    202 {"run_id":"run_…","status":"started","replayed":false}
RUN=run_…

# 4. events; add -H 'Last-Event-ID: <last seq>' to resume
curl -N $BASE/v1/runs/$RUN/events -H "$A"
#    : open
#    id: 0
#    data: {"event": "message.delta", "run_id": "run_…", "timestamp": 1795…, "delta": "Morgen ", "seq": 0}
#    …
#    data: {"event": "run.completed", "run_id": "run_…", "timestamp": …, "completed": true, "partial": false,
#           "interrupted": false, "output": "…", "usage": {…}, "runtime": {…}, "seq": 17}
#    : stream closed

# 5. status
curl -s $BASE/v1/runs/$RUN -H "$A"

# 6. stop
curl -s -X POST $BASE/v1/runs/$RUN/stop -H "$A"

# 7. answer an approval; prompt_id and execution_generation come from the approval.request
#    event or from pending_controls[] in step 5
curl -s -X POST $BASE/v1/runs/$RUN/approval -H "$A" -H "$J" \
     -d '{"request_id":"<prompt_id>","execution_generation":3,"choice":"deny"}'
#    200 {"run_id":"run_…","status":"resolved","prompt_id":"<prompt_id>"}
```

## Claim 4 — Idempotency-Key and Last-Event-ID

Idempotency (confirmed):
- `api_server_runs.py:786-790`: header `Idempotency-Key`, "must be 1-255 visible ASCII characters" else 400 `invalid_idempotency_key`.
- Fingerprint = SHA-256 of `{"body": <whole JSON body>, "gateway_session_key": …}` with sorted keys (`:708-715`). Same key and same body -> 202 with the original `run_id`, `"replayed": true`, header `Idempotency-Replayed: true`; same key, different body -> 409 `idempotency_key_conflict` (`:559-568`).
- Store: SQLite `runs_idempotency.db` in the Hermes home, `RETENTION_SECONDS = 24 * 60 * 60` (`api_server_run_idempotency.py:63`, `:71-75`); keys are scoped to profile + API key.
- A retry must repeat the body exactly. A client that puts a timestamp or a fresh id into the body turns its own retry into a 409.

Resume (exists, with limits the claim does not mention):
- `api_server_runs.py:1277-1282`: `raw_last_seq = request.headers.get("Last-Event-ID") or request.query.get("last_seq")`; events with `seq > last_seq` are replayed; frames carry `id: <seq>`.
- The backlog is process memory: `deque(maxlen=1000)` per run (`_RunStream.BACKLOG_LIMIT`, `:168`). Token deltas count, so a long answer overruns it; the server then sends one `replay.truncated` frame `{oldest_retained_seq, requested_seq}` without an `id:` line (`:1307-1315`).
- Expiry: `_RUN_STREAM_TTL = 300` (`api_server.py:3809`). The sweep runs every 60 s and drops a stream that is older than 300 s since creation and has no subscriber at that moment, even while the run is still executing (`api_server_runs.py:1554-1566`). After that the events URL waits one second and answers 404 `run_not_found` (`:1270-1275`); only `GET /v1/runs/{id}` still works.
- A gateway restart loses every stream.
- A subscriber slower than 256 queued events is disconnected (`SUBSCRIBER_QUEUE_LIMIT`, `:169`, `:188-194`); a write that blocks 5 s closes the connection (`_RUN_STREAM_WRITE_TIMEOUT`).
- The final text is always in `run.completed.output` and in `GET /v1/runs/{id}` `output`.

## Claim 5 — instructions and model override

- `/v1/runs`: `instructions = body.get("instructions")` (`api_server_runs.py:499`) -> `ephemeral_system_prompt=instructions` (`:873`); it is one of the admitted settings (`session_api_turn.py:25` `_SETTING_KEYS`) and the turn runner uses it (`run_turn_runner.py:1273-1275`).
- Session chat: `system_prompt = body.get("system_message") or body.get("instructions")`, must be a string (`api_server.py:2821-2823`).
- Model: `_request_agent_overrides(body, virtual_model=self._model_name)` (`api_server.py:284-306`); on `/v1/runs` and session chat a bare `model` is honoured (`allow_bare_model` defaults to true; docs `api-server.md:393-437`). Fields: `model`, `provider`, `model_options` (`reasoning_effort`, `service_tier`). A `provider` that contradicts a configured `model_routes` alias -> 400.
- What the code does not give: a guarantee. The instruction is layered on top of the core prompt ("layers it on top", `api-server.md:777-779`); the model may still ignore it.
- Unclaimed and helpful: API sessions already get a platform hint (`HA4/agent/prompt_builder.py:705-713`): "You're responding through an API server. The rendering layer is unknown — assume plain text. No markdown formatting (no asterisks, bullets, headers, code fences). Treat this like a conversation, not a document. Keep responses brief and natural. …"

## Claim 6 — approvals

Confirmed pieces:
- `HA4/hermes_cli/config_defaults.py:1622-1627`: `"mode": "smart"`, `"timeout": 300`, `"cron_mode": "deny"`, `"single_query_mode": "deny"`, `"unattended_mode": "deny"`.
- `HA4/tools/approval_context.py:188`: `_UNATTENDED_APPROVAL_PLATFORMS = frozenset({"webhook", "msgraph_webhook", "api_server"})`.
- The instant deny applies only when nothing can answer (`HA4/tools/approval.py:1189-1203`):

```python
is_ask = env_var_enabled("HERMES_EXEC_ASK")
…
elif is_ask and _is_unattended_platform_approval_context() \
        and (_gateway_notify_cb(get_current_session_key()) is None
             or _get_session_platform() in approval_context._NOTIFIER_BLIND_APPROVAL_PLATFORMS):
    is_ask = False
```

  with `_NOTIFIER_BLIND_APPROVAL_PLATFORMS = frozenset({"webhook", "msgraph_webhook"})` (`approval_context.py:208`) — `api_server` is not in it — and `os.environ["HERMES_EXEC_ASK"] = "1"` set by the gateway (`run_bootstrap.py:800`).

Where the claim breaks:
- In canonical mode every turn, API turns included, runs through the turn runner, which registers a notifier unconditionally: `HA4/gateway/run_turn_runner.py:1087` `register_gateway_notify(session_key, approval_transport(self))`. With a notifier present and `api_server` not notifier-blind, `is_ask` stays true and the gate asks instead of denying.
- Evidence that this is what happens: the daemon test posts a plain `/v1/runs` request (the adapter registers no notifier of its own on that path), the fake model asks for `rm -rf`, and a pending approval appears and blocks the command until it is answered (`test_api_pending_daemon.py:78-110`).
- The turn runner does not know which HTTP door admitted the turn. By the same code the synchronous `/api/sessions/{id}/chat` parks too, and that client has no run id to answer with. The wait is `approvals.timeout`, 300 s by default, then the command is refused ("timed out without user response… Silence is not consent."). I did not find an upstream test for this door; it needs a server test.
- "Dangerous" is narrower than it sounds. Only commands matching the dangerous-pattern list reach the gate. Under the default `mode: smart` an auxiliary model approves low-risk ones silently (`security.md:59-61`); a smart DENY with a human reachable becomes a prompt limited to `once`/`deny` (`approval.py:1001-1002`). Everything else — ordinary terminal commands, file writes — runs without any prompt.
- The default API toolset is broad (`HA4/toolsets.py:191-195`): all core tools except `text_to_speech`, `clarify`, `computer_use` and kanban, so it includes `terminal`, `write_file`, `execute_code`, `delegate_task`, `cronjob_manage`, `memory`. `platform_toolsets.api_server` narrows it (used in `test_api_pending_daemon.py:66`).

## Claim 7 — audio

Confirmed:
- No audio route in the API server route table; `"audio_api": False, "realtime_voice": False` (`api_server.py:55`).
- `HA4/hermes_cli/web_routers/audio.py`: `POST /api/audio/transcribe` (`:89`), `GET /api/audio/voice-config` (`:152`), `POST /api/audio/speak` (`:298`), `POST /api/audio/tts-lease`, `POST /api/audio/stt-lease`, `WS /api/audio/transcribe-stream` (`:527`), `WS /api/audio/speak-stream` (`:610`); included into the dashboard app at `HA4/hermes_cli/web_server.py:837`.
- Dashboard default port 9119 (`HA4/hermes_cli/subcommands/dashboard.py:18`).

Correction to "only on the separate dashboard backend":
- `HA4/gateway/run_api.py:1-14`: "The gateway's local HTTP/WS listener: the native session WebSocket plus the dashboard API." The gateway process mounts the same FastAPI app (`GatewayDashboardMount`, "imports and mounts the dashboard (`hermes_cli.web_server.app`) on first use").
- `HA4/hermes_cli/web_server_surface.py:1-10`, `POLICIES`: four surfaces of "the one web-server stack" — `serve`, `dashboard`, `gateway`, `webapp`.
- Where the gateway serves it (`HA4/gateway/run_runtime.py:586-605`): without the Webapp "a private ephemeral port" on 127.0.0.1 with ticket auth for local clients; with `webapp.enabled: true` a fixed listener, default `127.0.0.1:9120` (`config_defaults.py:983`, `gateway/webapp_listener.py:34`).
- Upstream is retiring the separate backend: release notes for v0.23.4 mention "`hermes gateway migrate --backends` to move an old serve/dashboard backend onto the gateway"; `cli-commands.md:1981` describes exit code 78 for an old `hermes serve` once the gateway owns the address.
- So the audio relay is reachable for a phone through `hermes serve`/`hermes dashboard` (9119) or the gateway's Webapp listener (9120), each behind the dashboard sign-in when bound off loopback. I did not test whether a non-browser client gets through the Webapp gate.

## Claim 8 — ntfy

Confirmed:
- Bundled plugin `HA4/plugins/platforms/ntfy/` (`plugin.yaml`: `kind: platform`, requires `NTFY_TOPIC`; optional `NTFY_SERVER_URL`, `NTFY_TOKEN`, `NTFY_PUBLISH_TOPIC`, `NTFY_MARKDOWN`, `NTFY_ALLOWED_USERS`, `NTFY_ALLOW_ALL_USERS`, `NTFY_HOME_CHANNEL`, `NTFY_HOME_CHANNEL_NAME`).
- Scheduler: `adapter.py:369-370` `cron_deliver_env_var="NTFY_HOME_CHANNEL"`, `standalone_sender_fn=_standalone_send`; `HA4/cron/scheduler_delivery.py:424-427` accepts a plugin platform that declares `cron_deliver_env_var`.
- `hermes send` exists (`HA4/hermes_cli/send_cmd.py`); for a plugin platform it uses the live adapter, else the standalone sender (`HA4/gateway/outbound/send.py:498-540`), so it works without a running gateway.

Corrections:
- `send_cmd.py:210`: "hermes send: --to PLATFORM[:channel[:thread]] is required". The form is `hermes send --to ntfy "text"` (home channel) or `hermes send --to ntfy:<topic> "text"`. The example `hermes send ntfy:alerts-channel "Done!"` in `website/docs/user-guide/messaging/ntfy.md:91`, repeated in the earlier report, does not match the parser.
- The agent's tool is `cronjob_manage` (`HA4/tools/cronjob_tools.py:1132`); the ntfy doc writes `cronjob(…)`.

Details that affect the design:
- The adapter is two-way. It subscribes to `NTFY_TOPIC` and feeds messages to the agent as user turns (`adapter.py:243-271`); only `NTFY_ALLOWED_USERS` being empty keeps it from answering (`ntfy.md:138-140`).
- Outgoing messages carry header `X-Tags: hermes-agent` (`adapter.py:50`, `:75`) and are cut at 4096 characters (`MAX_MESSAGE_LENGTH`, `:45`).
- A job created from an API session cannot deliver to its origin: `_NON_PUSH_ORIGIN_PLATFORMS = frozenset({"api_server"})` (`scheduler_delivery.py:33`). The job must name `deliver="ntfy"`.

## Claim 9 — TLS

- API server: `HA4/gateway/platforms/tcp_site.py:51` `site = web.TCPSite(runner, host, port, reuse_address=False if exclusive else None)` — no `ssl_context`; no `ssl` reference in `api_server*.py`.
- Dashboard / serve: `HA4/hermes_cli/web_server.py:1087-1106` `uvicorn.Config(served_app, host=host, port=port, …)` with no `ssl_keyfile`/`ssl_certfile`; comment "Gated mode runs behind a TLS terminator and needs X-Forwarded-Proto for cookie Secure flags."
- Gateway-hosted listener: `HA4/hermes_cli/web_server_lifecycle.py:613-631`, same.
- `grep -rn "ssl_keyfile\|ssl_certfile\|ssl_context\|ssl=" hermes_cli tui_gateway gateway` finds only outbound client code.
- The API server still sends `Strict-Transport-Security` on plain HTTP (`api_server.py:653`); irrelevant for a native client.

## Surprises

1. The canonical session authority is always on in a real v0.23.4 gateway. It changes the approval body, the approval event field name, the event list and the stop response.
2. `API_SERVER_ENABLED` has no effect at this tag; the key is the switch.
3. In canonical mode a dropped HTTP connection does not stop the turn: `observe_api_turn` awaits `asyncio.shield(waiter)` ("Shielding keeps the canonical turn alive past a cancelled request", `session_api_turn.py:349`, `:358`), and the disconnect handler of the session stream finds no agent in `_active_run_agents` (set only in the legacy branch, `api_server.py:3705`). Stopping needs `POST /v1/runs/{id}/stop`. Needs a server test.
4. A 503 can mean "do not resend": `admission_queued` with `x-should-retry: false`.
5. Reply text can contain megabytes of base64 (`![image](data:…)`) on the session-chat door, and raw `MEDIA:/abs/path` tags on the runs door (platform hint: "the runs endpoint intercepts nothing").
6. The event stream of a run older than five minutes disappears within a minute of the last subscriber leaving.
7. API sessions are their own sessions: a `session_id` that belongs to a Telegram, CLI or Desktop conversation is refused with 409 `permission_denied` (`session_api_turn.py:180-181`).
8. PyPI is stale (0.19.0); the release is distributed by installer, `hermes update` and Docker.
9. `main` is 1098 commits ahead of the tag four days after the release.

## Open — needs a test against a real v0.23.4 server

- Synchronous `/api/sessions/{id}/chat` with a command that escalates to an approval: does it block for `approvals.timeout`?
- Client disconnect during `/api/sessions/{id}/chat` and `/chat/stream`: does the turn continue, and is the reply then in `GET /api/sessions/{id}/messages`?
- Exact JSON of `approval.request` on `/v1/runs/{id}/events` (expected: `prompt_id`, `execution_generation`, `choices`).
- Events 404 after the 300 s sweep while the run is still running.
- Whether `message.interim` / `reasoning.available` ever appear on the runs stream in canonical mode.
- Whether the gateway Webapp listener (9120) lets a non-browser client call `/api/audio/*`.
- Which target a cron job created from an API session gets when the agent names no `deliver`.
