-- @author myoung
create table trip_request (
    id bigint primary key,
    user_id bigint not null,
    user_input text,
    destination varchar(128) not null,
    start_date date not null,
    days int not null,
    budget decimal(12, 2),
    people_count int not null,
    preferences_json text,
    created_at timestamp not null
);

create table trip_plan (
    id bigint primary key,
    user_id bigint not null,
    request_id bigint not null,
    title varchar(255) not null,
    summary text,
    status varchar(32) not null,
    total_budget decimal(12, 2),
    raw_ai_result_json text,
    created_at timestamp not null,
    updated_at timestamp not null
);

create table trip_day (
    id bigint primary key,
    plan_id bigint not null,
    day_index int not null,
    date date not null,
    city varchar(128) not null,
    weather_summary varchar(512),
    daily_budget decimal(12, 2)
);

create table trip_item (
    id bigint primary key,
    day_id bigint not null,
    time_slot varchar(32) not null,
    place_name varchar(255) not null,
    place_type varchar(64),
    address varchar(512),
    duration_minutes int,
    transport_suggestion varchar(512),
    estimated_cost decimal(12, 2),
    reason varchar(1024)
);

create table budget_breakdown (
    id bigint primary key,
    plan_id bigint not null,
    hotel_cost decimal(12, 2),
    food_cost decimal(12, 2),
    transport_cost decimal(12, 2),
    ticket_cost decimal(12, 2),
    other_cost decimal(12, 2),
    detail_json text
);

create index idx_trip_request_user_id on trip_request (user_id);
create index idx_trip_plan_user_id on trip_plan (user_id);
create index idx_trip_plan_request_id on trip_plan (request_id);
create index idx_trip_day_plan_id on trip_day (plan_id);
create index idx_trip_item_day_id on trip_item (day_id);
create index idx_budget_breakdown_plan_id on budget_breakdown (plan_id);
