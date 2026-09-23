-- Legacy versions used the generic `reverse` type. Its signed quantity is
-- already authoritative, so this metadata-only update is idempotent and does
-- not change stock, cost, balance or profit values.
update inventory_transactions
set transaction_type = case when quantity >= 0 then 'reverse_in' else 'reverse_out' end
where lower(transaction_type) = 'reverse';
