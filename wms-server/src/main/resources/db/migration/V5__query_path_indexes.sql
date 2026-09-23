-- 报表/查询路径索引：补齐 V1 基线只建 unique 约束与 FK、非 FK 过滤列全部裸奔的情况。
-- 生产是 ddl-auto: validate（application-prod.yml），实体上的 @Index 不会建表，只能走迁移。
-- 语法保持 MySQL 8 与 H2 MODE=MySQL 双兼容（与 V2 的 insertIfAbsent 同一约束）。
-- 代价：这三张写入频繁的表各自多一棵 B 树需要维护，换取读路径不再全表扫 + filesort。

-- operation_logs：每个 Controller 调用写一行，且仓库里没有任何清理逻辑，是唯一零索引又只增不减的表。
-- 查询见 OperationLogRepository.searchPage（username/action/result 过滤 + operation_at 排序）。
create index idx_operation_logs_username_at on operation_logs (username, operation_at);
create index idx_operation_logs_action on operation_logs (action);
create index idx_operation_logs_at on operation_logs (operation_at);

-- inventory_transactions：reference_no 供反审/红冲/商城退款回滚定位原流水（findByReferenceNoAndType、
-- findUnreversedByReferenceNo），transaction_type/transaction_at 供利润与各类报表的时间范围过滤。
create index idx_tx_reference_type on inventory_transactions (reference_no, transaction_type);
create index idx_tx_type_at on inventory_transactions (transaction_type, transaction_at);
create index idx_tx_at on inventory_transactions (transaction_at);

-- market_order：refund_no 被 findByRefundNoForUpdate 用于回调幂等加锁，无索引时 InnoDB 在 RR 下
-- 会对扫描到的行逐个加 next-key 锁，接近锁全表；(order_status, created_at) 覆盖订单列表筛选排序
-- 与定时任务 findCancelledPaidOnlineOrderIds 的 order_status 前缀。
create index idx_market_order_refund_no on market_order (refund_no);
create index idx_market_order_status_created on market_order (order_status, created_at);

-- inventory_reservations：V2 的 idx_reservation_available 前导列是 item_id，
-- 而 60 秒一次的 findExpiredHeld 只按 status + expires_at 过滤，用不上该索引。
create index idx_reservation_status_expires on inventory_reservations (status, expires_at);

-- stock_documents：V2 追加的 reversal_of_document_id 无索引，红冲前的 existsByReversalOfDocumentId 每次全表扫。
create index idx_stock_document_reversal on stock_documents (reversal_of_document_id);

-- market_product：小程序首页/分类页恒定 status='SHELF_ON' 过滤并按 sort_no, sales_count 排序。
create index idx_market_product_status_sort on market_product (status, sort_no);
