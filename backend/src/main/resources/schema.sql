CREATE TABLE IF NOT EXISTS users
(
    id            varchar(255) primary key,
    email         varchar(255) not null unique,
    display_name  varchar(255) not null,
    password_hash varchar(255) not null,
    is_admin      boolean default false,
    created_at    timestamp,
    last_login_at timestamp
);

create table IF NOT EXISTS leaderboard
(
    id          serial primary key,
    session     char(36)  not null,
    name        char(200) not null,
    started_at  timestamp not null,
    finished_at timestamp default CURRENT_TIMESTAMP
);
ALTER TABLE leaderboard ADD COLUMN IF NOT EXISTS user_id varchar(255);
CREATE INDEX IF NOT EXISTS idx_leaderboard_user ON leaderboard (user_id);

create table IF NOT EXISTS logs
(
    id          serial primary key,
    session     char(36)  not null,
    level       int not null,
    prompt      text not null,
    response    text not null
);

-- Attribution: the table originally keyed on session id alone, so no attempt could be traced back
-- to an account. Everything the admin dashboard reports about players depends on user_id.
ALTER TABLE logs ADD COLUMN IF NOT EXISTS user_id       varchar(255);
ALTER TABLE logs ADD COLUMN IF NOT EXISTS created_at    timestamp default CURRENT_TIMESTAMP;
ALTER TABLE logs ADD COLUMN IF NOT EXISTS blocked       boolean default false;
ALTER TABLE logs ADD COLUMN IF NOT EXISTS blocked_by    varchar(32);
ALTER TABLE logs ADD COLUMN IF NOT EXISTS input_tokens  int default 0;
ALTER TABLE logs ADD COLUMN IF NOT EXISTS output_tokens int default 0;
CREATE INDEX IF NOT EXISTS idx_logs_user_level ON logs (user_id, level);
CREATE INDEX IF NOT EXISTS idx_logs_created_at ON logs (created_at);

ALTER TABLE users ADD COLUMN IF NOT EXISTS is_admin boolean DEFAULT false;
ALTER TABLE users ADD COLUMN IF NOT EXISTS current_level integer DEFAULT 1;

-- Per-level timeline. `users.current_level` records only where a player is now, so neither the
-- leaderboard nor the projector could ever say when they got there, or how a run was paced.
-- One row per (player, level), written as the player advances.
create table IF NOT EXISTS level_progress
(
    id         serial primary key,
    user_id    varchar(255) not null,
    level      int          not null,
    reached_at timestamp    not null default CURRENT_TIMESTAMP,
    game_session_id varchar(36),
    -- A replay or a double-submit must not add a second "reached level 3": the timeline is a set
    -- of firsts, not an event log. Declared inline rather than as CREATE UNIQUE INDEX because
    -- spring.sql.init has no continue-on-error here, and HSQLDB (the dev database) is uneven
    -- about IF NOT EXISTS on index DDL.
    --
    -- Per game session, not per player: a second run of the event is a second first-arrival at
    -- every level, and the two-column form would reject it - silently, because recordLevelReached
    -- swallows its own exception, so the whole of run 2's timeline would simply not appear.
    -- A database that predates this already has the two-column form under the old name, and
    -- CREATE TABLE IF NOT EXISTS cannot reach it, so GameSessionBootstrap does that swap in Java.
    -- The name differs from the old one so both halves of the swap no-op once it has happened.
    constraint uq_level_progress_user_level_session unique (user_id, level, game_session_id)
);

-- Calibration and stress test run history. Stores the full JSON result so the admin dashboard can
-- show historical comparisons without re-running the tests. Both calibration (are levels solvable?)
-- and stress tests (how many concurrent users?) use the same table, distinguished by `type`.
CREATE TABLE IF NOT EXISTS calibration_results
(
    id                 serial primary key,
    run_id             varchar(36) not null unique,
    type               varchar(20) not null,          -- 'CALIBRATION' or 'STRESS_TEST'
    status             varchar(20) not null,           -- 'RUNNING','COMPLETED','FAILED','CANCELLED'
    started_at         timestamp   not null,
    completed_at       timestamp,
    invariants_passed  integer,
    invariants_total   integer,
    estimated_capacity integer,
    details_json       text                            -- Full JSON of the run results
);
CREATE INDEX IF NOT EXISTS idx_calibration_type ON calibration_results (type, started_at);

-- Small runtime settings the admin changes from the dashboard and that must outlive a container
-- restart. Key/value rather than a column per setting: the event access code and the level gates
-- are in-memory-only today for want of somewhere to put them, and can move here without another
-- migration. "key" and "value" are reserved words in HSQLDB, hence the prefixes.
CREATE TABLE IF NOT EXISTS app_settings
(
    setting_key   varchar(64) primary key,
    setting_value varchar(255) not null,
    updated_at    timestamp default CURRENT_TIMESTAMP
);

-- Which box produced a calibration or stress run, and what the run cost. Without this a Spark
-- result and a Station result are two rows that look identical and are not comparable - and the
-- difficulty curve is strongly model-specific, so that comparison is the whole point of keeping
-- them. Player-facing tables are deliberately left untagged.
ALTER TABLE calibration_results ADD COLUMN IF NOT EXISTS backend_id     varchar(64);
ALTER TABLE calibration_results ADD COLUMN IF NOT EXISTS backend_label  varchar(120);
ALTER TABLE calibration_results ADD COLUMN IF NOT EXISTS model          varchar(200);
-- Two runs only compare if the generation budget and the concurrency matched.
ALTER TABLE calibration_results ADD COLUMN IF NOT EXISTS max_tokens     integer;
ALTER TABLE calibration_results ADD COLUMN IF NOT EXISTS max_concurrent integer;
ALTER TABLE calibration_results ADD COLUMN IF NOT EXISTS total_tokens   bigint;
ALTER TABLE calibration_results ADD COLUMN IF NOT EXISTS llm_calls      integer;
ALTER TABLE calibration_results ADD COLUMN IF NOT EXISTS failed_calls   integer;
CREATE INDEX IF NOT EXISTS idx_calibration_backend ON calibration_results (backend_id, started_at);

-- A run of the event: one hackathon, one round, one demo. Distinct from `logs.session` and
-- `leaderboard.session`, which hold the HTTP session id and identify a browser rather than a round.
--
-- Starting a new run used to mean DELETE FROM logs, so a previous event's results existed only
-- until the next one began. Now each row is tagged and nothing is thrown away.
CREATE TABLE IF NOT EXISTS game_sessions
(
    id           varchar(36) primary key,
    label        varchar(120) not null,
    started_at   timestamp    not null default CURRENT_TIMESTAMP,
    ended_at     timestamp,
    -- Whether this run began by sending every player back to level 1, or let them carry on.
    reset_levels boolean default false,
    created_by   varchar(255)
);
CREATE INDEX IF NOT EXISTS idx_game_sessions_started ON game_sessions (started_at);

-- varchar rather than char: Postgres blank-pads char, and `logs.session char(36)` is the wart that
-- taught us. Nullable and unconstrained to match leaderboard.user_id - an ALTER-added foreign key
-- has no portable IF NOT EXISTS, and this file re-runs on every boot.
ALTER TABLE logs           ADD COLUMN IF NOT EXISTS game_session_id varchar(36);
ALTER TABLE level_progress ADD COLUMN IF NOT EXISTS game_session_id varchar(36);
ALTER TABLE leaderboard    ADD COLUMN IF NOT EXISTS game_session_id varchar(36);
CREATE INDEX IF NOT EXISTS idx_logs_game_session           ON logs (game_session_id, created_at);
CREATE INDEX IF NOT EXISTS idx_level_progress_game_session ON level_progress (game_session_id, user_id);

-- The Help Desk's worked solutions, as last measured.
--
-- The bundled answer key (admin-dashboard/src/data/solutions.ts) is written by an offline emitter
-- and frozen into the dashboard bundle, so it is only as current as the last time someone ran the
-- generator and rebuilt. A calibration started from the war room measures exactly the same thing
-- and used to throw the result away. This is where it lands instead.
--
-- One row per level per family - the answer key's own shape, one worked example each. Levels a run
-- did not cover keep the rows they had, so a single-level run tops up one section rather than
-- emptying the other six.
CREATE TABLE IF NOT EXISTS answer_key
(
    level         int          not null,
    family        varchar(40)  not null,
    prompt        text         not null,
    reply         text,
    how           varchar(80),
    -- Of the repeats this run made, how many this prompt won. A facilitator about to read a prompt
    -- aloud to a stuck player wants to know whether it lands once in three.
    wins          int          not null default 1,
    runs          int          not null default 1,
    run_id        varchar(36),
    backend_label varchar(120),
    model         varchar(200),
    measured_at   timestamp    not null default CURRENT_TIMESTAMP,
    primary key (level, family)
);
