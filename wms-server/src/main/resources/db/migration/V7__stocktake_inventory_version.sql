alter table inventory add column version bigint not null default 0;
alter table stocktake_lines add column snapshot_version bigint not null default -1;
