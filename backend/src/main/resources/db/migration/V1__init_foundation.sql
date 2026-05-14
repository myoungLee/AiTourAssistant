-- @author myoung
create table users (
    id bigint primary key,
    username varchar(64) not null,
    password_hash varchar(255) not null,
    nickname varchar(64),
    avatar_url varchar(512),
    phone varchar(32),
    email varchar(128),
    status varchar(32) not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint uk_users_username unique (username)
);

create table user_profile (
    id bigint primary key,
    user_id bigint not null,
    gender varchar(32),
    age_range varchar(32),
    travel_style varchar(64),
    default_budget_level varchar(32),
    preferred_transport varchar(64),
    preferences_json text,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint uk_user_profile_user_id unique (user_id)
);

create index idx_user_profile_user_id on user_profile (user_id);
