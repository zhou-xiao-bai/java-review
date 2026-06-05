alter table user_settings
    add column reviewed_point_scheduling_policy varchar(32) not null default 'follow_scope';

alter table user_settings
    add constraint chk_user_settings_reviewed_point_scheduling_policy
    check (reviewed_point_scheduling_policy in ('follow_scope', 'keep_reviewed'));
