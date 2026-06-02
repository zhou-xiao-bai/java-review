create table users (
    id uuid primary key,
    username varchar(64) not null unique,
    email varchar(255) unique,
    password_hash varchar(255) not null,
    display_name varchar(120) not null,
    role varchar(32) not null,
    enabled boolean not null default true,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index idx_users_username on users (username);
create index idx_users_email on users (email);
