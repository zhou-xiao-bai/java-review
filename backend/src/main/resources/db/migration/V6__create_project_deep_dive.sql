create table project_cases (
    id uuid primary key,
    user_id uuid not null references users (id) on delete cascade,
    name varchar(160) not null,
    background text,
    responsibility text,
    tech_stack jsonb not null default '[]'::jsonb,
    highlights jsonb not null default '[]'::jsonb,
    weak_points jsonb not null default '[]'::jsonb,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table project_sessions (
    id uuid primary key,
    user_id uuid not null references users (id) on delete cascade,
    project_case_id uuid not null references project_cases (id) on delete cascade,
    status varchar(32) not null,
    started_at timestamptz not null,
    ended_at timestamptz,
    final_score numeric(3, 2),
    evaluation jsonb,
    suggested_topics jsonb not null default '[]'::jsonb
);

create table project_turns (
    id uuid primary key,
    session_id uuid not null references project_sessions (id) on delete cascade,
    role varchar(32) not null,
    content text not null,
    created_at timestamptz not null
);

create index idx_project_cases_user on project_cases (user_id, created_at desc);
create index idx_project_sessions_case on project_sessions (project_case_id, started_at desc);
create index idx_project_turns_session on project_turns (session_id, created_at);
