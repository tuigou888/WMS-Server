-- Protect marketplace inventory before payment and preserve exact stock reversals.
create table inventory_reservations (
    quantity decimal(18,4) not null,
    consumed_at datetime(6),
    created_at datetime(6) not null,
    expires_at datetime(6) not null,
    id bigint not null auto_increment,
    item_id bigint not null,
    order_id bigint not null,
    released_at datetime(6),
    updated_at datetime(6) not null,
    warehouse_id bigint not null,
    status enum ('CONSUMED','EXPIRED','HELD','RELEASED') not null,
    primary key (id),
    constraint uk_inventory_reservations_order_item unique (order_id, item_id),
    constraint fk_inventory_reservations_item foreign key (item_id) references items (id),
    constraint fk_inventory_reservations_order foreign key (order_id) references market_order (id),
    constraint fk_inventory_reservations_warehouse foreign key (warehouse_id) references warehouses (id)
) engine=InnoDB;
create index idx_reservation_available on inventory_reservations (item_id, warehouse_id, status, expires_at);
create index idx_reservation_order on inventory_reservations (order_id);

alter table inventory_transactions add column reversal_of_transaction_id bigint;
alter table inventory_transactions add column source_document_id bigint;
alter table inventory_transactions add column source_line_id bigint;
alter table inventory_transactions add column operator varchar(100);
alter table stock_documents add column reversal_of_document_id bigint;
