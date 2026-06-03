alter table review_tasks
    add column removed_at timestamptz;

create index idx_review_tasks_user_date_removed
    on review_tasks (user_id, task_date, removed_at);
