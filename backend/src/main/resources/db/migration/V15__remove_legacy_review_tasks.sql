delete from review_attempts;
delete from review_turns;
delete from review_sessions;
delete from today_review_actions;
delete from user_review_unit_states;

alter table review_sessions drop constraint if exists review_sessions_task_id_fkey;
drop index if exists idx_review_sessions_task;
alter table review_sessions drop column if exists task_id;
alter table review_sessions add column review_unit_state_id uuid not null references user_review_unit_states (id) on delete cascade;
create index idx_review_sessions_unit_state on review_sessions (review_unit_state_id);

drop table if exists review_tasks;
