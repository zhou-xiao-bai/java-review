alter table review_points
    add column auto_plan_tier varchar(32) not null default 'CORE';

alter table topics
    add column new_expansion_limit integer not null default 2 check (new_expansion_limit between 0 and 20);

alter table user_settings
    add column daily_new_expansion_limit integer not null default 3 check (daily_new_expansion_limit between 0 and 20);

update topics
set new_expansion_limit = case
    when interview_value >= 5 then 4
    when interview_value >= 4 then 3
    when interview_value >= 3 then 2
    else 1
end;

update review_points
set auto_plan_tier = 'OPTIONAL'
where title like '%面试表达闭环%'
   or title like '%两分钟表达结构%';
