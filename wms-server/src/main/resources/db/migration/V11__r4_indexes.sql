-- R4-21：第四轮审核补齐的高频查询索引
-- market_order.user_id：商城买家订单列表 search(userId,...)；warehouse_id：管理端仓库 scope 过滤
CREATE INDEX idx_market_order_user ON market_order (user_account_id);
CREATE INDEX idx_market_order_warehouse ON market_order (warehouse_id);
-- auth_sessions.username：revokeByUsername 全量撤销
CREATE INDEX idx_auth_sessions_username ON auth_sessions (username);
-- idempotent_requests.created_at：48h 保留期清理
CREATE INDEX idx_idempotent_requests_created_at ON idempotent_requests (created_at);
