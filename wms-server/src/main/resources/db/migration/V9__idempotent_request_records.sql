create table idempotent_requests (
    id bigint not null auto_increment,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    username varchar(50) not null,
    scope varchar(160) not null,
    request_key varchar(128) not null,
    request_hash varchar(64) not null,
    response_json longtext,
    primary key (id),
    constraint uk_idempotent_request unique (username, scope, request_key)
) engine=InnoDB;
