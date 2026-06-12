delete from review_weakness_events;
delete from review_attempts;
delete from review_turns;
delete from review_sessions;
delete from today_review_actions;
delete from user_review_unit_states;
delete from question_variants;

with old_points as (
    delete from review_points
    returning
        topic_id,
        title,
        importance,
        difficulty,
        interview_frequency,
        auto_plan_tier
),
topic_units as (
    insert into review_points (
        id,
        topic_id,
        title,
        importance,
        difficulty,
        interview_frequency,
        auto_plan_tier,
        mastery,
        status,
        review_count,
        wrong_count,
        weak_points,
        next_probe,
        recent_question_types,
        created_at,
        updated_at
    )
    select
        gen_random_uuid(),
        topic.id,
        topic.title,
        max(old_points.importance),
        max(old_points.difficulty),
        max(old_points.interview_frequency),
        case
            when count(*) filter (where old_points.auto_plan_tier = 'CORE') > 0 then 'CORE'
            when count(*) filter (where old_points.auto_plan_tier = 'EXPAND') > 0 then 'EXPAND'
            else 'OPTIONAL'
        end,
        0,
        'UNCOVERED',
        0,
        0,
        '[]'::jsonb,
        '围绕「' || topic.title || '」追问机制、边界和生产排查。',
        '[]'::jsonb,
        now(),
        now()
    from old_points
    join topics topic on topic.id = old_points.topic_id
    group by topic.id, topic.title
    returning id, topic_id, title
)
insert into question_variants (
    id,
    review_unit_id,
    title,
    prompt,
    focus,
    difficulty,
    variant_type,
    enabled,
    created_at,
    updated_at
)
select
    gen_random_uuid(),
    topic_units.id,
    old_points.title,
    '请围绕「' || topic_units.title || '」考察「' || old_points.title || '」。要求回答核心机制、关键边界和生产证据。',
    old_points.title,
    old_points.difficulty,
    case
        when old_points.title like '%生产%' or old_points.title like '%排查%' or old_points.title like '%故障%' or old_points.title like '%慢 SQL%' then 'TROUBLESHOOTING'
        when old_points.title like '%对比%' or old_points.title like '%取舍%' or old_points.title like '%反例%' then 'COMPARISON'
        when old_points.title like '%边界%' or old_points.title like '%失效%' or old_points.title like '%可靠性%' or old_points.title like '%场景%' then 'SCENARIO'
        when old_points.title like '%表达%' then 'EXPANSION'
        else 'CORE_DIAGNOSTIC'
    end,
    true,
    now(),
    now()
from old_points
join topic_units on topic_units.topic_id = old_points.topic_id
on conflict (review_unit_id, title) do nothing;
