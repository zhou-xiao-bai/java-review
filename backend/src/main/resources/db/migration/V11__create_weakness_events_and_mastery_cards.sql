alter table review_points
    add column mastery_card jsonb;

create table review_weakness_events (
    id uuid primary key,
    review_point_id uuid not null references review_points (id) on delete cascade,
    session_id uuid not null references review_sessions (id) on delete cascade,
    turn_id uuid references review_turns (id) on delete set null,
    category varchar(80) not null,
    label text not null,
    evidence text,
    severity integer not null check (severity between 1 and 5),
    status varchar(32) not null,
    created_at timestamptz not null,
    resolved_at timestamptz
);

create index idx_review_weakness_events_point_status
    on review_weakness_events (review_point_id, status);

create index idx_review_weakness_events_session
    on review_weakness_events (session_id);
