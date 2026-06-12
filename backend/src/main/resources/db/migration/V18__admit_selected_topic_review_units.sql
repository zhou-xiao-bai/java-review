insert into user_review_unit_states (
    id,
    user_id,
    review_unit_id,
    status,
    admitted_at,
    consecutive_success_count,
    consecutive_failure_count,
    created_at,
    updated_at
)
select
    gen_random_uuid(),
    app_user.id,
    point.id,
    'PENDING_FIRST_REVIEW',
    now(),
    0,
    0,
    now(),
    now()
from users app_user
cross join review_points point
join topics topic on topic.id = point.topic_id
where topic.selected = true
on conflict (user_id, review_unit_id) do nothing;
