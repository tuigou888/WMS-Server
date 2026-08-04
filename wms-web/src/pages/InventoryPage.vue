<script setup>
import { h, onMounted, ref, watch } from 'vue'
import { Card, Segmented, Table, Tag, Typography, message } from 'ant-design-vue'
import { api } from '../api/wms'
import { dateTime, money, number } from '../utils/format'

const tab = ref('stock')
const stock = ref([])
const transactions = ref([])
const stockLoading = ref(false)
const txLoading = ref(false)
const stockLoaded = ref(false)
const txLoaded = ref(false)

const loadStock = async () => {
  if (stockLoaded.value) return
  stockLoading.value = true
  try {
    stock.value = await api.inventory()
  } catch (e) {
    message.error(e.message)
  } finally {
    stockLoading.value = false
    stockLoaded.value = true
  }
}

const loadTx = async () => {
  if (txLoaded.value) return
  txLoading.value = true
  try {
    transactions.value = await api.transactions()
  } catch (e) {
    message.error(e.message)
  } finally {
    txLoading.value = false
    txLoaded.value = true
  }
}

onMounted(() => { api.inventory().catch((e) => message.error(e.message)) })

watch(tab, (t) => { if (t === 'stock') loadStock(); else loadTx() }, { immediate: true })

const stockCols = [
  { title: '物品编码', dataIndex: 'itemCode' },
  { title: '物品名称', dataIndex: 'itemName', render: (v, r) => h('span', [h('b', v), h('br'), h(Typography.Text, { type: 'secondary' }, r.unit)]) },
  { title: '仓库 / 库位', render: (_, r) => h('span', [r.warehouseName, h('br'), h(Typography.Text, { type: 'secondary' }, r.locationCode || '未指定库位')]) },
  { title: '批次号', dataIndex: 'batchNo', render: (v) => v ? h(Tag, { color: 'blue' }, v) : '-' },
  { title: '库存数量', dataIndex: 'quantity', render: (v, r) => h('b', `${number(v)} ${r.unit}`) },
  { title: '平均成本', dataIndex: 'avgCost', render: (v) => money(v) },
  { title: '库存金额', dataIndex: 'totalAmount', render: (v) => money(v) },
  { title: '更新时间', dataIndex: 'updatedAt', render: (v) => dateTime(v) },
]

const txCols = [
  { title: '单据编号', dataIndex: 'referenceNo' },
  { title: '类型', dataIndex: 'transactionType', render: (v) => h(Tag, { color: v === 'in' ? 'green' : 'volcano' }, v === 'in' ? '入库' : '出库') },
  { title: '物品', render: (_, r) => h('span', [r.itemName, h('br'), h(Typography.Text, { type: 'secondary' }, r.itemCode)]) },
  { title: '库位', dataIndex: 'locationCode' },
  { title: '变动数量', dataIndex: 'quantity', render: (v) => h('span', { class: Number(v) < 0 ? 'negative' : 'positive' }, number(v)) },
  { title: '单价', dataIndex: 'unitCost', render: (v) => money(v) },
  { title: '余额数量', dataIndex: 'balanceQuantity', render: (v) => number(v) },
  { title: '时间', dataIndex: 'transactionAt', render: (v) => dateTime(v) },
]
</script>

<template>
  <div class="page-heading">
    <div>
      <Typography.Title :level="3" class="page-title">库存管理</Typography.Title>
      <Typography.Text class="page-subtitle" type="secondary">查看库存分布、库存金额及完整出入库流水</Typography.Text>
    </div>
  </div>

  <Card class="table-card">
    <template #title>
      <Segmented v-model:value="tab" :options="[{ label: '库存查询', value: 'stock' }, { label: '库存流水', value: 'transactions' }]" />
    </template>
    <a-table v-if="tab === 'stock'" row-key="id" :loading="stockLoading" :data-source="stock" :columns="stockCols" />
    <a-table v-else row-key="id" :loading="txLoading" :data-source="transactions" :columns="txCols" />
  </Card>
</template>