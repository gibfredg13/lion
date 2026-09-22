# DGX Spark — tuning notes for the infrastructure team

Everything below was measured against the box as currently configured
(`192.168.1.145:42000`, llama.cpp serving `unsloth/Qwen3.6-27B-MTP-GGUF:UD-Q4_K_XL`).

## What we measured

| | Result |
|---|---|
| Single request (61-token prompt, ~35 tokens out) | **3.2s** |
| Generation rate | **10–13 tok/s** |
| Prompt processing | **~230 tok/s** |
| Configured slots (`/slots`) | **1** |
| Context | 32768 (model trained for 262144) |

Aggregate throughput against concurrency:

| Concurrent | Wall time | Last request waits | Aggregate |
|---|---|---|---|
| 1 | 3.2s | 3.2s | 10.2 tok/s |
| 2 | 5.1s | 5.1s | 12.3 tok/s |
| 4 | 10.1s | 10.1s | 11.3 tok/s |
| 8 | 18.7s | 18.7s | 11.8 tok/s |

**Aggregate throughput is flat at ~11 tok/s no matter how many clients arrive.** That is the
signature of a single slot: requests queue, they do not batch. Wall time is simply N × 3.2s, and
the last person in the queue absorbs all of it.

At our event size that means the 20th player waits **~64 seconds** for one reply — and level 7 costs
two model calls per turn, so ~128 seconds there.

## Why single-request latency cannot be improved much

Generation is memory-bandwidth-bound. The DGX Spark's unified memory runs at roughly 273 GB/s, and
the model weighs 17.9 GB, so an upper bound for one stream is about **15 tok/s**. We measure 10–13.
The box is already running at 70–85% of what the hardware can do for a single sequence.

**So there is no configuration change that makes one answer arrive much faster.** The wins are all
in serving more answers at once, which on bandwidth-bound hardware is close to free: a batch reads
the weights once and serves every sequence in it.

---

## Recommendations, in order of impact

### 1. Run more slots — `--parallel 8`

The single highest-value change, and it costs nothing.

```bash
llama-server -m <model> --parallel 8 -c 32768 --cont-batching
```

Context is divided across slots: 32768 / 8 = 4096 per slot. Our prompts are ~250 tokens and replies
are capped at 140, so 4096 is generous — `--parallel 16` at 2048 each would also be safe.

Expect aggregate throughput to rise several-fold and the 20th player's wait to fall from ~64s to
roughly 10–15s. Please confirm `--cont-batching` is on (default in recent builds).

**Tell us the number you choose.** Our `LLM_MAX_CONCURRENT` must match it; we queue fairly in front
of the server, and if our limit exceeds your slot count the queue just moves rather than shrinking.

### 2. Consider a smaller model

27B at Q4 is 17.9 GB, and time-per-token scales with that. Qwen3 **8B** or **14B** at Q4 is roughly
3–5 GB, giving ~3× the generation rate *and* more room for slots — likely a 5–10× improvement in
players served per minute, combined with #1.

The game needs personality and instruction-following, not deep reasoning. **We would need to
re-run our difficulty calibration** before switching, because level design turned out to be strongly
model-specific — but the switch is worth evaluating.

### 3. Speculative decoding

The loaded model is an **MTP** (multi-token prediction) build. If this llama.cpp build supports the
MTP heads, enabling them typically gives 1.5–2× on generation. Failing that, a draft model works:

```bash
--model-draft qwen3-0.6b-q4.gguf --draft-max 8
```

Our outputs are short and formulaic, which is the case speculative decoding handles best.

### 4. Flash attention

`-fa` if the build supports it on this hardware — lower memory pressure per slot, which makes higher
`--parallel` values comfortable.

### 5. Prompt cache reuse — needs a change on both sides

Every player at the same level sends a **nearly identical system prompt**; only the secret word
differs. Prompt processing is ~1s of our 3.2s, so caching the shared prefix is worth roughly 30%.

```bash
--cache-reuse 256
```

This only pays off if the shared text comes *first*. Right now our secret sits in the second system
message, which breaks the common prefix almost immediately. **We can reorder our prompts to put the
secret last** — tell us if you enable `--cache-reuse` and we will.

### 6. Metrics during the event

```bash
--metrics
```

Exposes Prometheus counters at `/metrics`, including queue depth and tokens/s. Worth watching live —
queue depth climbing is the first sign the room has outgrown the slot count, and it shows up well
before anyone complains.

---

## The one thing that must not change

Our client sends `chat_template_kwargs: {"enable_thinking": false}`.

Qwen3 is a reasoning model: left to think, it spends the entire token budget on `reasoning_content`
and returns an **empty** `content`. That reaches every player as a blank reply — the game is
completely unplayable. If the model or template is swapped, please confirm thinking can still be
suppressed, or tell us so we can read `reasoning_content` instead.

## Summary of asks

1. `--parallel 8 --cont-batching` — tell us the slot count you settle on
2. Evaluate Qwen3 8B/14B as a swap (we re-calibrate)
3. Enable MTP or a draft model for speculative decoding
4. `-fa` if supported
5. `--cache-reuse 256`, and tell us so we can reorder our prompts
6. `--metrics` for the event
7. Keep `enable_thinking: false` working

---

## Update: the DGX Station (vLLM), and what changed with it

The game now runs against a DGX Station serving `nvidia/Qwen3.6-35B-A3B-NVFP4` under vLLM at
`http://192.168.1.129:8000`. Both boxes are declared in `application-prod.yml` under
`merlin.llm.backends` and the admin dashboard switches between them live.

**Measured, Station vs Spark:**

| | DGX Spark (llama.cpp) | DGX Station (vLLM) |
|---|---|---|
| Single reply | ~3.2s | **0.27s** |
| 16 concurrent | queued linearly, tens of seconds | **0.48s wall, all 16** |
| Concurrency model | fixed `--parallel` slots | continuous batching |
| `maxConcurrent` | 4, matching `--parallel` | **32** |

Continuous batching is why the concurrency limit moved. On the Spark the limit had to match the
slot count or requests queued invisibly; on the Station concurrency is nearly free, and leaving the
limit at 4 costs roughly eight times the throughput while reporting nothing anywhere.

### Two things that bit us moving over, both invisible as configuration problems

1. **One system message only.** This model's chat template rejects a second system message with
   `HTTP 400 — "System message must be at the beginning."` Every level here sends between two and
   five. The provider now joins consecutive system messages with a blank line
   (`mergeSystemMessages`, on for the Station and off for the Spark, which accepts the list form
   and whose existing calibration numbers are worth keeping comparable).

2. **HTTP/2.** Java's `HttpClient` defaults to HTTP/2, which over cleartext means an h2c upgrade
   attempt. vLLM behind uvicorn answers that by **dropping the request body**, then rejecting the
   request for having no body — indistinguishable from a malformed payload. The client is pinned to
   HTTP/1.1. llama.cpp tolerated the upgrade, which is why this never surfaced before.

### Still true

The warning further up this document that the difficulty curve is strongly model-specific holds,
and Qwen3.6 is a stronger model than what the Spark ran. Calibration results are now stored in
Postgres tagged with the backend and model that produced them, so the two can be compared directly
rather than from memory.
