create table review_tasks (
    id uuid primary key,
    user_id uuid not null references users (id),
    review_point_id uuid references review_points (id) on delete set null,
    manual_prompt text,
    task_date date not null,
    type varchar(32) not null,
    status varchar(32) not null,
    priority_score numeric(6, 2) not null,
    estimated_minutes integer not null check (estimated_minutes > 0),
    created_at timestamptz not null,
    completed_at timestamptz,
    constraint chk_review_tasks_content check (
        review_point_id is not null
        or manual_prompt is not null
    )
);

create index idx_review_tasks_user_date on review_tasks (user_id, task_date);
create index idx_review_tasks_user_status on review_tasks (user_id, status);
create index idx_review_tasks_review_point on review_tasks (review_point_id);

create unique index uq_review_tasks_daily_point
    on review_tasks (user_id, task_date, review_point_id)
    where review_point_id is not null;
