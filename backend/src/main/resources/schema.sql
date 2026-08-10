create table IF NOT EXISTS leaderboard
(
    id          serial primary key,
    session     char(36)  not null,
    name        char(200) not null,
    started_at  timestamp not null,
    finished_at timestamp default CURRENT_TIMESTAMP
);

create table IF NOT EXISTS logs
(
    id          serial primary key,
    session     char(36)  not null,
    level       int not null,
    prompt      text not null,
    response    text not null
);

ALTER TABLE users ADD COLUMN IF NOT EXISTS is_admin boolean DEFAULT false;