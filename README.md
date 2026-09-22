# The Lion's Den

A browser game that teaches how AI guardrails fail. Players try to talk **Leo**, a guardian lion,
into revealing a password he has been told to protect — across seven levels.

Each level **closes the trick that beat the level below it, and opens a new one**, so the ladder
teaches a taxonomy rather than one lesson seven times:

| Level | The guardrail | Why it fails |
|---|---|---|
| 1 | none | a secret in a prompt is not a secret |
| 2 | a polite request | politeness is not a control |
| 3 | a hard rule, one banned word | the rule names a unit; pick a different one |
| 4 | a filter reading the answer | information can live in the shape, not the content |
| 5 | that filter, told about every disguise | enumeration is a losing game |
| 6 | a filter reading the question | it matches phrasings, not intent |
| 7 | a second model reading for meaning | it asks about *the word*, not about *enough of it* |

Built on the open-source [HackMerlin](https://hackmerlin.io) engine.

## Quick start

```bash
cp .env.example .env          # set DB_PASSWORD, LLM_STATION_BASEURL, EVENT_ACCESS_CODE
docker compose up -d --build
```

| Service | URL |
|---|---|
| Game | http://localhost:18088 |
| Admin war room | http://localhost:30010 |
| Projector leaderboard | http://localhost:18088/leaderboard/tv |
| Postgres | localhost:15439 |

The first account to register becomes the admin. Registration requires the event access code
(`EVENT_ACCESS_CODE`, default `LIONHACK2026`), which you share with participants at the start.

The war room has a **🛟 Help Desk** tab with hint ladders and verified solutions for every level —
hand that to whoever is helping people who get stuck. **👥 Players** shows every prompt each person
has tried, and handles password resets, progress resets and account removal.

## Before you run an event

Three things are easy to miss and each one breaks the game in a way that is hard to diagnose from
the UI.

**1. Give the inference server more than one slot.** llama.cpp defaults to `--parallel 1`, which
serialises every player behind every other player — four concurrent requests were measured
returning at 7s / 14s / 21s / 28s. Start it with:

```bash
llama-server -m <model> --parallel 4 -c 32768
```

and set `LLM_MAX_CONCURRENT` to the same number.

**2. Leave `LLM_DISABLE_THINKING=true` unless you know the model does not reason.** Reasoning
models (Qwen3, DeepSeek-R1) return an empty `content` and put everything in `reasoning_content`,
which reaches players as a blank reply from Leo.

**3. Add your public hostname to `merlin.cors.allowedOrigins`** before exposing the game through a
tunnel.

## Checking the difficulty curve

Levels live in `backend/src/main/resources/levels.yml` and can also be edited from the admin
dashboard while the event is running. Three tests guard them, in increasing order of cost:

```bash
./gradlew :backend:test                                          # seconds, no model
CALIBRATE=true ./gradlew :backend:test --tests '*JudgeGapTest*'  # ~1 min
CALIBRATE=true ./gradlew :backend:test --tests '*LevelCalib*'    # ~25 min, the whole ladder
```

The first checks the filters catch every disguise they enumerate, miss every fragment, and never
fire on an innocent answer. The second checks the top level still has several ways through. The
third fires jailbreak prompts at every level through the real filters and the real model, and fails
if a level stops closing the level below it, stops being beatable the way it was designed to be, or
if Leo starts refusing rather than defending.

That last one matters most. **A level nobody can beat is a worse bug than a level that is too easy**
— and in production it is invisible, because a stuck player looks exactly like a player who is
thinking. Add `CALIBRATE_EMIT=$(pwd)` to regenerate the facilitator answer key from the same run.

### Is it a staircase, or the same trick seven times?

Those checks are pairwise — each level against the one below it — and **a ladder can pass all of
them while one sentence beats every rung on it.** So the calibration also measures reuse across the
whole climb, and prints a grid of which route beat which level:

```
family            1  2  3  4  5  6  7
acrostic          X  X  X  X  X  X  X
context-leak      X  X  X  X  X  X  .
spelling          X  X  X  X  .  .  .
transposition     X  X  X  X  X  X  X
```

A row of `X`s is a rung the player skipped. The build fails if any single prompt beats more than
three levels, or if a family that beat level 1 still beats the last one. Two routes are exempt
because nothing here can close them — **a jumble and a fragment**, level 7's own two solutions,
which are open from level 1 and would need new detectors to shut. The same grid is written into the
facilitator's answer key, where it answers the question a stuck player's helper actually has: *has
this person learned anything since level 2, or have they been doing the same thing all along?*

## Documentation

| Document | What it covers |
|---|---|
| [docs/CONFIGURATION.md](docs/CONFIGURATION.md) | **Every setting** — env vars, level tuning, ports, admin API, limits |
| [docs/DGX_SPARK_TUNING.md](docs/DGX_SPARK_TUNING.md) | **For the infra team** — measured throughput and how to scale the LLM box |
| [docs/SOLUTIONS.md](docs/SOLUTIONS.md) | **Facilitator's answer key** — how to beat each level, several ways, with a hint ladder |
| [docs/IMPLEMENTATION_NOTES.md](docs/IMPLEMENTATION_NOTES.md) | What was rebuilt and why, with the measurements behind each decision |
| [docs/DEPLOY_WITH_AUTH.md](docs/DEPLOY_WITH_AUTH.md) | Deployment |
| [docs/DOCKER_GUIDE.md](docs/DOCKER_GUIDE.md) | Docker detail |

Older documents in `docs/` predate the rebuild and carry a warning banner where they are known to
be inaccurate.

## Stack

**Backend** — Java 21, Spring Boot 3.4, Spring Data JPA, Spring Session JDBC, Postgres.
Authentication is hand-rolled (BCrypt via `spring-security-crypto`); there is no Spring Security
filter chain, so authorization is an explicit check inside each handler.

**Frontend** — React 19, TypeScript, Vite, Mantine 9, TanStack Query. Built by Gradle and packaged
into the backend JAR under `static/`, so one container serves both.

**Admin dashboard** — a separate React 18 + Vite app served by nginx on port 30010.

**LLM** — several backends can be declared under `merlin.llm.backends`, each with its own kind
(`dgxspark` for any OpenAI-compatible endpoint, plus `azure`, `gemini`, `ollama`), endpoint, model
and concurrency limit. The admin dashboard switches between them live, with no restart, and the
choice is stored in Postgres so it survives one. Each backend carries its own throttle, because the
limit is a property of the server: llama.cpp serves a fixed slot count, vLLM batches continuously.
A calibration or stress run pins the backend it started on, so its results belong to exactly one box.

## Local development

```bash
./gradlew bootRun                      # backend on :8080
cd frontend && npm run start           # Vite on :3000, proxies /api to :8080
cd admin-dashboard && npm run dev      # admin on :3001
```

## License

Internal use. Not for distribution.
