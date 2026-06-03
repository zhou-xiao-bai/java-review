alter table user_settings
    add column active_llm_config_id varchar(80),
    add column llm_configs jsonb not null default '[]'::jsonb;

update user_settings
set active_llm_config_id = 'default',
    llm_configs = jsonb_build_array(jsonb_build_object(
        'id', 'default',
        'name', '默认中转站',
        'provider', llm_provider,
        'baseUrl', llm_base_url,
        'apiKey', llm_api_key,
        'model', llm_model
    ))
where llm_configs = '[]'::jsonb;
