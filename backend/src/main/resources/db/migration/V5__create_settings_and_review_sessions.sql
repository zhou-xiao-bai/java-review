create table user_settings (
    user_id uuid primary key references users (id) on delete cascade,
    llm_provider varchar(40) not null,
    llm_base_url varchar(500),
    llm_api_key text,
    llm_model varchar(120),
    request_timeout_seconds integer not null check (request_timeout_seconds between 1 and 300),
    daily_review_minutes integer not null check (daily_review_minutes between 10 and 240),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table review_sessions (
    id uuid primary key,
    user_id uuid not null references users (id) on delete cascade,
    task_id uuid not null references review_tasks (id) on delete cascade,
    status varchar(32) not null,
    started_at timestamptz not null,
    ended_at timestamptz,
    final_score numeric(3, 2),
    summary text,
    evaluation jsonb,
    created_at timestamptz not null
);

create table review_turns (
    id uuid primary key,
    session_id uuid not null references review_sessions (id) on delete cascade,
    role varchar(32) not null,
    turn_type varchar(32) not null,
    content text not null,
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null
);

create index idx_review_sessions_user_status on review_sessions (user_id, status);
create index idx_review_sessions_task on review_sessions (task_id);
create index idx_review_turns_session_created on review_turns (session_id, created_at);
