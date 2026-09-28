-- ShedLock 调度锁表（上线整改 A7）：定时任务加 @SchedulerLock 后按 name 行级互斥，
-- 多实例部署时同一任务同一时刻只有一个实例执行，审计归档不再要求"只在一个实例上开启"。
-- DDL 取自 ShedLock 官方 MySQL 版（net.javacrumbs.shedlock 5.10.0），TIMESTAMP(3) 与
-- usingDbTime() 的微秒级时间函数配套；MySQL 8 与 H2 MODE=MySQL 均可执行。
create table shedlock (
    name varchar(64) not null,
    lock_until timestamp(3) null,
    locked_at timestamp(3) null,
    locked_by varchar(255) null,
    primary key (name)
) engine=InnoDB;
