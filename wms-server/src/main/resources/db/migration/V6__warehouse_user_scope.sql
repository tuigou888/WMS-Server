create table user_warehouse_access (
    id bigint not null auto_increment,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    user_id bigint not null,
    warehouse_id bigint not null,
    primary key (id),
    constraint uk_user_warehouse_access unique (user_id, warehouse_id),
    constraint fk_user_warehouse_access_user foreign key (user_id) references user_accounts (id),
    constraint fk_user_warehouse_access_warehouse foreign key (warehouse_id) references warehouses (id)
) engine=InnoDB;
