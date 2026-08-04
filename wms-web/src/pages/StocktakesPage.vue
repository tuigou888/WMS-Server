<script setup>
import { h, onMounted, ref, watch } from 'vue'
import { Button, Card, Drawer, InputNumber, Popconfirm, Select, Space, Table, Tag, Typography, message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { api } from '../api/wms'
import { dateTime, number } from '../utils/format'
import { useAuthStore } from '../stores/auth'
import { hasPerm } from '../utils/permission'

const label = { DRAFT: ['草稿', 'default'], APPROVED: ['已审核', 'blue'], REJECTED: ['已驳回', 'red'], COMPLETED: ['已完成', 'green'] }

const auth = useAuthStore()
const rows = ref([])
const warehouses = ref([])
const warehouseId = ref()
const detail = ref(null)
const drawerOpen = ref(false)
watch(drawerOpen, (v) => { if (!v) detail.value = null })
watch(detail, (v) => { drawerOpen.value = !!v })
const counts = ref({})

const load = () => api.stocktakes().then((x) => { rows.value = x }).catch((e) => message.error(e.message))

onMounted(() => {
  load()
  api.warehouses().then((x) => { warehouses.value = x; warehouseId.value = x[0]?.id }).catch((e) => message.error(e.message))
})

const open = (r) => {
  detail.value = r
  const obj = {}
  r.lines.forEach((x) => { obj[x.id] = x.actualQuantity })
  counts.value = obj
}

const action = async (f) => {
  try {
    await f()
    message.success('操作成功')
    load()
    if (detail.value) {
      const latest = (await api.stocktakes()).find((x) => x.id === detail.value.id)
      if (latest) open(latest)
    }
  } catch (e) {
    message.error(e.message)
  }
}

const saveCounts = () => action(() => api.countStocktake(detail.value.id, {
  warehouseId: detail.value.warehouseId,
  lines: detail.value.lines.filter((x) => counts.value[x.id] !== undefined && counts.value[x.id] !== null).map((x) => ({
    itemCode: x.itemCode, locationCode: x.locationCode, batchNo: x.batchNo, actualQuantity: counts.value[x.id],
  })),
}))

const columns = [
  { title: '盘点单号', dataIndex: 'stocktakeNo' },
  { title: '仓库', dataIndex: 'warehouseName' },
  { title: '状态', dataIndex: 'status', render: (v) => h(Tag, { color: label[v]?.[1] }, label[v]?.[0]) },
  { title: '创建时间', dataIndex: 'createdAt', render: (v) => dateTime(v) },
  {
    title: '操作',
    render: (_, r) => h(Space, [
      h(Button, { type: 'link', onClick: () => open(r) }, '盘点明细'),
      r.status === 'DRAFT' && hasPerm(auth.user, 'stocktake:review') ? h('span', [
        h(Button, { type: 'link', onClick: () => action(() => api.reviewStocktake(r.id, { action: 'APPROVE' })) }, '审核通过'),
        h(Button, { type: 'link', danger: true, onClick: () => action(() => api.reviewStocktake(r.id, { action: 'REJECT' })) }, '驳回'),
      ]) : null,
      r.status === 'APPROVED' ? h(Popconfirm, { title: '确认执行盘点差异调整？', onConfirm: () => action(() => api.completeStocktake(r.id)) }, { default: () => h(Button, { type: 'primary' }, '执行调整') }) : null,
    ]),
  },
]

const detailColumns = [
  { title: '物品', render: (_, r) => `${r.itemCode} · ${r.itemName}` },
  { title: '库位', dataIndex: 'locationCode' },
  { title: '批次', dataIndex: 'batchNo', render: (v) => v || '-' },
  { title: '账面数量', dataIndex: 'bookQuantity', render: (v) => number(v) },
  {
    title: '实盘数量',
    render: (_, r) => h(InputNumber, {
      value: counts.value[r.id], min: 0, disabled: detail.value?.status !== 'DRAFT',
      'onUpdate:value': (v) => { counts.value = { ...counts.value, [r.id]: v } },
    }),
  },
  {
    title: '差异',
    render: (_, r) => counts.value[r.id] === undefined || counts.value[r.id] === null ? '-' : h('span', { class: Number(counts.value[r.id]) - Number(r.bookQuantity) >= 0 ? 'positive' : 'negative' }, number(Number(counts.value[r.id]) - Number(r.bookQuantity))),
  },
]
</script>

<template>
  <div class="page-heading">
    <div>
      <Typography.Title :level="3" class="page-title">库存盘点</Typography.Title>
      <Typography.Text type="secondary">自动生成账面库存，录入实盘数量后审核并执行盈亏调整</Typography.Text>
    </div>
    <Space>
      <a-select v-model:value="warehouseId" style="width: 180px;" :options="warehouses.map((x) => ({ value: x.id, label: x.name }))" />
      <Button type="primary" :icon="h(PlusOutlined)" :disabled="!warehouseId" @click="action(() => api.createStocktake({ warehouseId }))">发起盘点</Button>
    </Space>
  </div>

  <Card class="table-card">
    <a-table row-key="id" :data-source="rows" :columns="columns" />
  </Card>

  <a-drawer v-model:open="drawerOpen" :title="detail?.stocktakeNo" width="760">
    <template v-if="detail">
      <Typography.Paragraph>账面数量与实盘数量的差额将在审核并执行后写入库存流水。</Typography.Paragraph>
      <a-table row-key="id" :pagination="false" :data-source="detail.lines" :columns="detailColumns" />
      <Button v-if="detail.status === 'DRAFT'" type="primary" style="margin-top: 16px;" @click="saveCounts">保存实盘数量</Button>
    </template>
  </a-drawer>
</template>