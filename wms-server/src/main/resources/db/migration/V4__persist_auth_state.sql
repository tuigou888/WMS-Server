create table auth_sessions (
    id bigint not null auto_increment,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    expires_at datetime(6) not null,
    token_hash varchar(64) not null,
    username varchar(50) not null,
    primary key (id),
    constraint uk_auth_sessions_token_hash unique (token_hash)
) engine=InnoDB;
create index idx_auth_sessions_expires_at on auth_sessions (expires_at);

create table auth_login_attempts (
    id bigint not null auto_increment,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    rate_key varchar(255) not null,
    attempt_count integer not null,
    window_start datetime(6) not null,
    primary key (id),
    constraint uk_auth_login_attempts_rate_key unique (rate_key)
) engine=InnoDB;

create table wechat_bind_tickets (
    id bigint not null auto_increment,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    ticket_hash varchar(64) not null,
    openid varchar(64) not null,
    expires_at datetime(6) not null,
    primary key (id),
    constraint uk_wechat_bind_tickets_ticket_hash unique (ticket_hash)
) engine=InnoDB;
create index idx_wechat_bind_tickets_expires_at on wechat_bind_tickets (expires_at);
