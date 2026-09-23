-- MySQL 8+ read-only inventory reconciliation.
-- Run before and after V2/V3 deployment, export every result set, and retain it
-- with the release record. This script deliberately makes no historical data
-- correction: unmatched rows must be investigated and approved first.

-- 1) Every legacy generic reversal should be gone after V3.
select id, reference_no, transaction_at, item_id, warehouse_id, location_id, batch_no, quantity, total_cost_amount
from inventory_transactions
where lower(transaction_type) = 'reverse'
order by id;

-- 2) The signed transaction ledger must equal the materialized inventory row.
with transaction_balance as (
  select item_id, warehouse_id, location_id, batch_no,
         round(sum(quantity), 4) as ledger_quantity,
         round(sum(case when quantity >= 0 then total_cost_amount else -total_cost_amount end), 2) as ledger_amount
  from inventory_transactions
  group by item_id, warehouse_id, location_id, batch_no
), reconciled as (
  select i.id as inventory_id, i.item_id, i.warehouse_id, i.location_id, i.batch_no,
         i.quantity as inventory_quantity, i.total_amount as inventory_amount,
         coalesce(t.ledger_quantity, 0) as ledger_quantity, coalesce(t.ledger_amount, 0) as ledger_amount
  from inventory i
  left join transaction_balance t on t.item_id = i.item_id and t.warehouse_id = i.warehouse_id
    and t.location_id <=> i.location_id and t.batch_no <=> i.batch_no
  union all
  select null, t.item_id, t.warehouse_id, t.location_id, t.batch_no,
         0, 0, t.ledger_quantity, t.ledger_amount
  from transaction_balance t
  left join inventory i on i.item_id = t.item_id and i.warehouse_id = t.warehouse_id
    and i.location_id <=> t.location_id and i.batch_no <=> t.batch_no
  where i.id is null
)
select *, round(inventory_quantity - ledger_quantity, 4) as quantity_difference,
       round(inventory_amount - ledger_amount, 2) as amount_difference
from reconciled
where round(inventory_quantity - ledger_quantity, 4) <> 0 or round(inventory_amount - ledger_amount, 2) <> 0
order by item_id, warehouse_id, location_id, batch_no;

-- 3) A reversal must have a source pointer after new V2 operations. Legacy
-- rows are reported for manual linkage only; do not invent a source relation.
select id, reference_no, transaction_type, transaction_at, item_id, quantity
from inventory_transactions
where transaction_type in ('reverse_in', 'reverse_out') and reversal_of_transaction_id is null
order by transaction_at, id;
