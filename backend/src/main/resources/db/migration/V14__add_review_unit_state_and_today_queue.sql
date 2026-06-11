create extension if not exists pgcrypto;

create table user_review_unit_states (
    id uuid primary key,
    user_id uuid not null references users (id) on delete cascade,
    review_unit_id uuid not null references review_points (id) on delete cascade,
    status varchar(32) not null,
    admitted_at timestamptz not null,
    first_reviewed_at timestamptz,
    last_reviewed_at timestamptz,
    next_review_at timestamptz,
    last_result varchar(32),
    consecutive_success_count integer not null default 0,
    consecutive_failure_count integer not null default 0,
    archived_at timestamptz,
    not_for_me_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uq_user_review_unit_states_user_unit unique (user_id, review_unit_id),
    constraint chk_user_review_unit_states_status check (
        status in ('PENDING_FIRST_REVIEW', 'ACTIVE', 'ARCHIVED', 'NOT_FOR_ME')
    ),
    constraint chk_user_review_unit_states_last_result check (
        last_result is null or last_result in ('POOR', 'PARTIAL', 'GOOD', 'SELF_MASTERED')
    )
);

create index idx_user_review_unit_states_user_status
    on user_review_unit_states (user_id, status);
create index idx_user_review_unit_states_user_next_review
    on user_review_unit_states (user_id, next_review_at);
create index idx_user_review_unit_states_unit
    on user_review_unit_states (review_unit_id);

create table review_attempts (
    id uuid primary key,
    user_id uuid not null references users (id) on delete cascade,
    review_unit_id uuid not null references review_points (id) on delete cascade,
    review_session_id uuid references review_sessions (id) on delete set null,
    source varchar(32) not null,
    result varchar(32) not null,
    score numeric(3, 2),
    attempted_at timestamptz not null,
    note text,
    created_at timestamptz not null,
    constraint uq_review_attempts_session unique (review_session_id),
    constraint chk_review_attempts_source check (source in ('REVIEW_SESSION', 'SELF_ASSESS')),
    constraint chk_review_attempts_result check (result in ('POOR', 'PARTIAL', 'GOOD', 'SELF_MASTERED'))
);

create index idx_review_attempts_user_unit_attempted
    on review_attempts (user_id, review_unit_id, attempted_at desc);

create table today_review_actions (
    id uuid primary key,
    user_id uuid not null references users (id) on delete cascade,
    review_unit_id uuid not null references review_points (id) on delete cascade,
    action_date date not null,
    action_type varchar(32) not null,
    postpone_until date,
    created_at timestamptz not null,
    constraint chk_today_review_actions_type check (
        action_type in ('DISMISS_TODAY', 'MANUAL_ADD', 'POSTPONE', 'SELF_MASTERED')
    )
);

create index idx_today_review_actions_user_date
    on today_review_actions (user_id, action_date);
create index idx_today_review_actions_user_unit_date
    on today_review_actions (user_id, review_unit_id, action_date);

insert into user_review_unit_states (
    id,
    user_id,
    review_unit_id,
    status,
    admitted_at,
    first_reviewed_at,
    last_reviewed_at,
    next_review_at,
    last_result,
    consecutive_success_count,
    consecutive_failure_count,
    created_at,
    updated_at
)
select
    gen_random_uuid(),
    task_summary.user_id,
    task_summary.review_point_id,
    case
        when point.review_count > 0 or task_summary.completed_count > 0 then 'ACTIVE'
        else 'PENDING_FIRST_REVIEW'
    end,
    task_summary.first_task_created_at,
    task_summary.first_completed_at,
    coalesce(point.last_reviewed_at, task_summary.last_completed_at),
    point.next_review_at,
    case
        when point.review_count = 0 and task_summary.completed_count = 0 then null
        when point.status = 'UNSTABLE' then 'POOR'
        when point.status in ('FIRST_PASS', 'DUE') then 'PARTIAL'
        when point.status in ('STABLE', 'LONG_TERM') then 'GOOD'
        else null
    end,
    case
        when point.status in ('STABLE', 'LONG_TERM') then 1
        else 0
    end,
    case
        when point.status = 'UNSTABLE' then greatest(1, point.wrong_count)
        else 0
    end,
    now(),
    now()
from (
    select
        task.user_id,
        task.review_point_id,
        min(task.created_at) as first_task_created_at,
        min(task.completed_at) filter (where task.completed_at is not null) as first_completed_at,
        max(task.completed_at) filter (where task.completed_at is not null) as last_completed_at,
        count(*) filter (where task.status = 'COMPLETED') as completed_count
    from review_tasks task
    where task.review_point_id is not null
    group by task.user_id, task.review_point_id
) task_summary
join review_points point on point.id = task_summary.review_point_id
on conflict (user_id, review_unit_id) do nothing;

insert into review_attempts (
    id,
    user_id,
    review_unit_id,
    review_session_id,
    source,
    result,
    score,
    attempted_at,
    note,
    created_at
)
select
    gen_random_uuid(),
    session.user_id,
    task.review_point_id,
    session.id,
    'REVIEW_SESSION',
    case
        when session.final_score is null or session.final_score < 3 then 'POOR'
        when session.final_score < 4.2 then 'PARTIAL'
        else 'GOOD'
    end,
    session.final_score,
    coalesce(session.ended_at, session.started_at),
    session.summary,
    now()
from review_sessions session
join review_tasks task on task.id = session.task_id
where session.status = 'EVALUATED'
and task.review_point_id is not null
on conflict (review_session_id) do nothing;
