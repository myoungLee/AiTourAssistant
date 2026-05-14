-- @author myoung
create table llm_call_log (
    id bigint primary key,
    user_id bigint,
    plan_id bigint,
    provider varchar(64) not null,
    model varchar(128) not null,
    prompt_summary text,
    response_summary text,
    token_usage_json text,
    latency_ms bigint,
    success boolean not null,
    error_message text,
    created_at timestamp not null
);

create table tool_call_log (
    id bigint primary key,
    user_id bigint,
    plan_id bigint,
    tool_name varchar(128) not null,
    request_json text,
    response_summary text,
    latency_ms bigint,
    success boolean not null,
    error_message text,
    created_at timestamp not null
);

create index idx_llm_call_log_user_id on llm_call_log (user_id);
create index idx_llm_call_log_plan_id on llm_call_log (plan_id);
create index idx_tool_call_log_user_id on tool_call_log (user_id);
create index idx_tool_call_log_plan_id on tool_call_log (plan_id);
