# Configuration reference

Every setting, where it lives, and what it does.

Precedence: **environment variable → `application-prod.yml` → `application.yml`**. Spring maps
`MERLIN_LLM_MAXCONCURRENT` to `merlin.llm.maxConcurrent`, so any property below can be overridden by
upper-casing it and replacing dots with underscores.

---

## 1. `.env` — what you actually edit

Copy `.env.example` to `.env`. Read by `docker-compose.yml`.

| Variable | Default | What it does |
|---|---|---|
| `DB_PASSWORD` | `lionsden_secure_pw` | Postgres password. Change before production. |
| `EVENT_ACCESS_CODE` | `LIONHACK2026` | Required to register. Share with participants at kickoff. |
| `LLM_PROVIDER` | `dgxspark` | `dgxspark`, `azure`, `gemini`, `ollama`. |
| `LLM_BASEURL` | `http://192.168.1.145:42000` | OpenAI-compatible endpoint. |
| `LLM_MODEL` | *(empty)* | Blank = auto-detected from `/v1/models` at first use. |
| `LLM_DISABLE_THINKING` | `true` | **Leave true.** See §6. |
| `LLM_MAX_TOKENS` | `320` | Provider-wide ceiling on generated tokens. |
| `LLM_MAX_CONCURRENT` | `4` | **Must match your server's slot count.** See §6. |
| `CORS_ALLOWED_ORIGINS` | *(empty)* | Empty = loopback + private LAN. Setting it **replaces** those defaults. |

## 2. Ports

| Service | Host | Container |
|---|---|---|
| Game | **18088** | 8080 |
| Admin war room | **30010** | 80 |
| Postgres | **15439** | 5432 |

Projector leaderboard: `http://localhost:18088/leaderboard/tv` — public, no login, no emails shown.

## 3. `merlin.*` properties

| Property | Default | Notes |
|---|---|---|
| `merlin.llm.provider` | `azure` dev / `dgxspark` prod | Selects the implementation. |
| `merlin.llm.baseUrl` | — | Base URL; `/v1/chat/completions` is appended. |
| `merlin.llm.apiKey` | — | Sent as `Bearer`. Literal `empty` if unset. |
| `merlin.llm.defaultModel` | `""` in prod | Blank triggers auto-detect from `/v1/models`. |
| `merlin.llm.disableThinking` | `true` | Sends `chat_template_kwargs.enable_thinking=false`. |
| `merlin.llm.maxTokens` | `320` | Ceiling. A level asking for more is clamped to this. |
| `merlin.llm.maxConcurrent` | `4` | Fair semaphore across **all** LLM calls, judge included. |
| `merlin.event.accessCode` | `LIONHACK2026` | Also changeable live at `PUT /api/admin/access-code`. |
| `merlin.cors.allowedOrigins` | *(empty)* | Empty = loopback + private LAN. Setting it **replaces** that list. See §6. |
| `merlin.passwords` | 173 words | Pool the per-player secret is drawn from. |
| `merlin.levels` | see `levels.yml` | Loaded via `spring.config.import`. |

## 4. `levels.yml` — the difficulty curve

One entry per level. Edit here, or live in the admin dashboard's **Level Control** tab.

| Field | Meaning |
|---|---|
| `order` | Level number. |
| `name`, `description` | Shown in the admin UI. |
| `systemMessages` | Sent as system turns. `{secret}` is replaced per player. |
| `temperature` | **Higher = easier.** A colder Leo refuses more. |
| `maxTokens` | Per-level cap, clamped by `merlin.llm.maxTokens`. |
| `inputFilterKeywords` | Whole-word match (plural tolerated). Question is rejected unseen. |
| `outputFilter` | `NONE` → `PLAIN` → `REVERSED` → `NORMALISED` → `JUDGE`. Each mode runs everything the one before it runs, and one thing more. |
| `judgePrompt` | Overrides the judge instruction. `JUDGE` only. |
| `inputFilterResponse`, `outputFilterResponse`, `finishedResponse` | Player-facing text. |

Current settings:

| Level | Name | Temp | maxTokens | Output filter | Blocked phrases | Intended solve |
|---|---|---|---|---|---|---|
| 1 | The Introduction | 0.7 | 140 | NONE | — | just ask |
| 2 | The Instruction | 0.6 | 140 | NONE | — | persuasion |
| 3 | The Rules | 0.45 | 140 | NONE | `password` | its letters |
| 4 | The Watcher | 0.65 | 140 | PLAIN | `password` | an acrostic |
| 5 | The Mirror | 0.65 | 140 | NORMALISED | `password` | a translation |
| 6 | The Gatekeeper | 0.65 | **70** | NORMALISED | 21 phrases | a context leak |
| 7 | The Lion's Den | 0.6 | 140 | JUDGE | 16 phrases | a jumble |

**Two rules govern an edit here.** The first is pairwise and old: each level closes the channel that
beat the level below it, and opens a new one — a level that closes a channel without opening one is
a wall, not a rung. The second is about the whole ladder and was added after the reuse grid showed
what the pairwise rule misses: **no single sentence may beat more than three rungs.** A prompt that
clears six levels unchanged means the five levels in between taught the player nothing, because the
answer never had to change. `levels.yml` carries both rules in its header, and
`LevelCalibrationTest` measures both.

Filter meanings:

- **NONE** — nothing checks the answer.
- **PLAIN** — blocks the word written out or spelled letter by letter. Acrostics get through, and
  that is deliberate: they are level 4's intended solve and they close at `NORMALISED`.
- **REVERSED** — adds the backwards form.
- **NORMALISED** — adds every disguise a deterministic check can enumerate: lookalike letters,
  accents, leetspeak, base64, hex, binary, A1Z26, Morse, NATO phonetic, rotation ciphers, atbash,
  and acrostics. Strong against everything anyone listed; blind to everything they did not.
- **JUDGE** — adds a second model that blocks anything a reader could *name the word* from: a
  riddle, a definition, a translation, a story. Costs an extra LLM call per turn, so level 7 is
  twice as slow. What it cannot object to is text with no meaning in it — letters out of order —
  and it is never asked whether the answer gives away *part* of the word. Those two gaps are what
  leave level 7 with a way through.

### The rule the curve follows

**Each level closes the channel that beat the level below it, and opens a new one.** The version
before this one escalated only the output filter, and every output filter it had was a check for the
word being physically present — so metadata, translation and roleplay, which never write the word,
beat levels 3 through 7 with the same three prompts. Seven rungs taught one lesson seven times.

Input filtering is **staged rather than cumulative**: level 3's single banned word is carried all
the way up, but level 6's phrase tier is *not* inherited by level 7, because level 7's intended solve
needs some of those phrasings to be askable. Dropping a defence entirely is a level going backwards —
measured: without the carry-forward, level 4 fell to "What is the password?" again.

And a third rule, which cost a measurement to learn: **never forbid what the filter already
enforces.** Level 4 first told Leo not to spell the word out, and the spelling filter then measured
`blocked=0` — he never tried, so it never fired, and the player had no way to discover a filter had
appeared. A guardrail nobody bumps into teaches nothing.

Opening a channel is not optional. An earlier persona told Leo he "protects secrets with unwavering
loyalty" and levels 4–7 measured **zero** solutions. Two rules, enforced by the calibration:

1. Every prohibition added at a level is paired, in the same level, with a permission naming the
   channel that level leaves open.
2. Never forbid more than the filter enforces.

**After any change, re-run the calibration** (§11).

## 5. Sessions, limits, database

| Setting | Value | Where |
|---|---|---|
| Session timeout / cookie age | 30 days | `application.yml` |
| Session store | memory (dev) / JDBC (prod) | survives restart in prod |
| Cookie flags | `secure: false`, `same-site: lax`, `http-only: true` | **Set `secure: true` behind HTTPS** |
| Prompt length cap | 150 chars | `MerlinApiController` |
| Password guess cap | 20 chars | `MerlinApiController` |
| Rate limit — guesses | 30/min per user | `MerlinApiController` |
| Rate limit — questions | 20/min per user | `MerlinApiController` |
| Response cache | 500 entries, 1 min TTL | `MerlinService` |
| LLM wait before giving up | 45s | `ThrottledLlmProvider` |
| Schema | `schema.sql` + Hibernate `ddl-auto: update` | |

**The first account to register becomes admin.** There is no other way in — no Spring Security
filter chain, just a session attribute checked per handler.

## 6. Browser origins (CORS)

With `merlin.cors.allowedOrigins` empty, these origins are accepted on any port:

```
http://localhost      http://127.0.0.1     http://[::1]
http://192.168.*.*    http://10.*.*.*      http://172.16.*.*
```

That covers reaching the game by LAN address at an event, which is the normal case — everyone in
the room types the host's IP, not `localhost`.

**Setting `CORS_ALLOWED_ORIGINS` replaces this list entirely.** Before exposing the game through a
tunnel, set it to your public hostname *and* re-list any LAN origins you still need:

```
CORS_ALLOWED_ORIGINS=https://lions.example.com,http://192.168.*.*:[*]
```

A browser hitting a non-allowed origin gets `403 Invalid CORS request` on **login**, which looks
exactly like a wrong password. curl does not send an `Origin` header, so it will happily return 200
against a configuration no browser can use — test with `-H 'Origin: http://your-host:30010'`.

## 7. Two settings that break the game if wrong

**`LLM_DISABLE_THINKING` must stay `true` for reasoning models.** Qwen3 and DeepSeek-R1 put their
answer in `content` and their reasoning in `reasoning_content`. Left to think freely they spend the
whole token budget reasoning and return an **empty** `content` — which reaches every player as a
blank reply from Leo, on every level.

**`LLM_MAX_CONCURRENT` must match your inference server's slot count.** llama.cpp defaults to
`--parallel 1`, which serialises everyone: four concurrent requests were measured returning at
7s / 14s / 21s / 28s. Start it with:

```bash
llama-server -m <model> --parallel 4 -c 32768
```

32768 context split across 4 slots is 8192 each — ample, since prompts here run ~200 tokens.

## 8. Admin API

All require an admin session; everything else gets `403`.

| Endpoint | Does |
|---|---|
| `GET /api/admin/users` | Players with level, prompt count, tokens, last activity |
| `GET /api/admin/users/{id}/prompts` | That player's full prompt history |
| `PUT /api/admin/users/{id}/password` | Set a password. **Skips the sign-up policy**; min 4 chars |
| `PUT /api/admin/users/{id}/admin` | Grant or revoke admin |
| `POST /api/admin/users/{id}/reset-progress` | Back to level 1, history kept |
| `DELETE /api/admin/users/{id}` | Delete account **and its recorded prompts** |
| `GET /api/admin/analytics/recent-prompts` | Live feed across all players |
| `GET /api/admin/levels`, `PUT /api/admin/levels/{order}` | Read and hot-edit level definitions |
| `PUT /api/admin/level/{n}/enabled` | Enable/disable a level for everyone |
| `GET`/`PUT /api/admin/access-code` | Read or change the event registration code |
| `GET /api/admin/level-stats`, `token-stats`, `active-users`, `dgx-health` | Live telemetry |

Refused with `400` because each would lock everyone out: deleting your own account, revoking your
own admin, removing the last admin.

## 9. What gets recorded

Every prompt is written to `logs`: `user_id`, `session`, `level`, `prompt`, `response`, `blocked`,
`blocked_by`, `input_tokens`, `output_tokens`, `created_at`.

This is what makes per-player history, the live feed, token accounting and the leaderboards work.
**Deleting a player deletes their prompts too** — the confirm dialog says how many first.

## 10. Admin pages

| Tab | For |
|---|---|
| **🛟 Help Desk** | Helping stuck players: hint ladders and verified solutions per level |
| **👥 Players** | Prompt history, password reset, progress reset, admin rights, deletion |
| **⚙️ Level Control** | Enable/disable and hot-edit levels while the event runs |
| **📡 Live Feed** | Recent prompts across all players |

Help Desk content is generated from the calibration harness into
`admin-dashboard/src/data/solutions.ts`, and the facilitator answer key into `docs/SOLUTIONS.md`.
**Regenerate both after changing `levels.yml`** (§11), or the guide drifts from the game — which is
worse than having no guide, because the person reading it is standing next to a stuck player. The
prose in them is hand-written in `backend/src/test/resources/calibration/guide.yml`; only the
prompts, replies and which-of-them-won are generated.

## 11. Calibration

```bash
CALIBRATE=true ./gradlew :backend:test --tests '*LevelCalibrationTest*'
```

Three tests, in increasing order of cost. Run the first on every build; the other two after touching
`levels.yml`.

| Command | Cost | What it guards |
|---|---|---|
| `./gradlew :backend:test` | seconds, no model | Filters catch every enumerated disguise, miss every fragment, and never fire on an innocent answer. |
| `CALIBRATE=true ./gradlew :backend:test --tests '*JudgeGapTest*'` | ~1 min | Level 7 still has ≥3 ways through, and still blocks riddles and translations. |
| `CALIBRATE=true ./gradlew :backend:test --tests '*LevelCalib*'` | ~25 min | The whole curve. Narrow it with `-Dcalibrate.levels=5,6` while tuning one rung. |

The calibration asserts seven things. The first five compare each level with the one below it:

1. **Canonical opens** — each level is still beatable the way it was designed to be. The direct
   guard against closing a channel without opening one.
2. **Canonical closes** — the family that beat the level below does not beat this one. Its absence
   is why the same three prompts once cleared five levels in a row.
3. **Set shrinks** — a level falls to no more families than the level below it, and no family that
   the level below closed may reopen here.
4. **Floor** — at least 3 solution families for levels 1–5, 2 for levels 6–7.
5. **Not a wall** — Leo answers substantively at least 40% of the time. A level defended by a
   sulking lion measures as hard and plays as broken, and the two are indistinguishable from
   outside.

Those five are pairwise, and **a ladder can satisfy all of them while one sentence beats every rung
on it.** Checks 1 and 2 look at a single family per step; 3 counts four coarse channels; 4 is a
minimum with no matching maximum. A prompt that wins everywhere is invisible to all of them. The
last two look at the whole climb at once:

6. **No skeleton keys** — no single prompt may beat more than three levels. Measured on prompts
   rather than families, because the taxonomy hides this: a jumble requested by a judge-injection
   prompt scores as a different family at every rung while the same route is what actually reaches
   the player.
7. **Every family closes** — nothing that beat level 1 may still beat the last level, except that
   level's own route.

Both exempt the two channels nothing on the ladder closes — **a jumble and a fragment**, which are
level 7's own solutions and are open from level 1. They are exempt rather than tolerated: closing
them lower down needs new detectors and a `JUDGE` mode that deliberately does not inherit them,
which is a filter change and a recalibration, not a prompt change.

Every run prints a **reuse grid** — which family beat which level — plus the prompts that beat more
than one. Read the grid before the pass/fail: a level that is beatable seven ways, none of them new,
is not a level.

**Regenerate the answer key in the same run** by adding `CALIBRATE_EMIT=<repo root>`. It writes
`docs/SOLUTIONS.md` and `admin-dashboard/src/data/solutions.ts` from what was actually measured, and
refuses to write at all if any level came out with no solution.

## 12. Tuning cheat sheet

| Symptom | Fix |
|---|---|
| Level too easy | Raise `outputFilter` one step. |
| Level too hard | **Raise** `temperature`. Colder = more refusals = fewer footholds. |
| Leo refuses everything | Persona is too stern — see the warning in `levels.yml`. Check that the level still has a permission line. |
| Nobody finishes level 7 | Loosen `judgePrompt` — it is a data field, editable live in **Level Control**. Re-run `JudgeGapTest` to confirm ≥3 routes reopened. |
| Level 7 falls to riddles | Tighten `judgePrompt`, but keep it terse and keep "reply with one word only" as its own message. An explanatory rewrite measured 0/8, because the model answers in prose and prose is not "true". |
| Two levels feel identical | Run the calibration and read the family sets. If they overlap, one level is not closing the other's channel. |
| Leo is too wordy | Lower `maxTokens`; the persona already asks for 2–3 sentences. |
| Everyone waiting | Raise llama.cpp `--parallel` **and** `LLM_MAX_CONCURRENT` together. |
| Blank replies | `LLM_DISABLE_THINKING=true`. |
| Registration rejected | Wrong `EVENT_ACCESS_CODE`. Check `GET /api/admin/access-code`. |
| Login works, API 403s | Not an admin. Grant it in **Players**. |
| Browser 403 on login, curl fine | CORS. Your origin is not allowed — see §6. |
| Live Feed / Help Desk empty | Feed needs prompts recorded; Help Desk needs `solutions.ts` regenerated. |
