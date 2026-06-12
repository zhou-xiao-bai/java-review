create table question_variants (
    id uuid primary key,
    review_unit_id uuid not null references review_points (id) on delete cascade,
    title varchar(180) not null,
    prompt text not null,
    focus varchar(240),
    difficulty integer not null,
    variant_type varchar(32) not null,
    enabled boolean not null default true,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint chk_question_variants_type check (
        variant_type in ('CORE_DIAGNOSTIC', 'SCENARIO', 'COMPARISON', 'TROUBLESHOOTING', 'EXPANSION')
    ),
    constraint uq_question_variants_unit_title unique (review_unit_id, title)
);

create index idx_question_variants_unit_enabled
    on question_variants (review_unit_id, enabled);

alter table review_sessions
    add column question_variant_id uuid references question_variants (id) on delete set null;

create index idx_review_sessions_question_variant
    on review_sessions (question_variant_id);

alter table review_attempts
    add column question_variant_id uuid references question_variants (id) on delete set null;

create index idx_review_attempts_question_variant
    on review_attempts (question_variant_id);

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
    point.id,
    '核心诊断',
    '请用两分钟说明「' || topic.title || ' / ' || point.title || '」的核心机制、关键边界和生产排查证据。',
    '核心机制、关键边界和生产排查证据',
    point.difficulty,
    'CORE_DIAGNOSTIC',
    true,
    now(),
    now()
from review_points point
join topics topic on topic.id = point.topic_id
on conflict (review_unit_id, title) do nothing;
