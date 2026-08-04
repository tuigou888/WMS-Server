<script setup>
import { h, onMounted, ref } from 'vue'
import { Button, Card, Table, Tag, Typography, message } from 'ant-design-vue'
import { api } from '../api/wms'
import { useAuthStore } from '../stores/auth'
import { hasPerm } from '../utils/permission'

const statusMap = { DRAFT: ['草稿', 'default'], APPROVED: ['已批准', 'success'], REJECTED: ['已驳回', 'error'], CANCELLED: ['已取消', 'warning'] }

const auth = useAuthStore()
const rows = ref([])

const load = () => api.purchaseRequests().then((x) => { rows.value = x }).catch((e) => message.error(e.message || '加载失败'))

onMounted(load)

const review = (id, action) => api.reviewPurchaseRequest(id, { action }).then(() => { message.success('操作成功'); load() }).catch((e) => message.error(e.message || '操作失败'))

const columns = [
  { title: '申请单号', dataIndex: 'requestNo' },
  { title: '供应商', dataIndex: 'supplierName', render: (x) => x || '待指定' },
  { title: '仓库', dataIndex: 'warehouseName' },
  { title: '来源', dataIndex: 'source', render: (x) => x === 'STOCK_ALERT' ? '库存预警' : '手工申请' },
  { title: '状态', dataIndex: 'status', render: (x) => h(Tag, { color: statusMap[x]?.[1] }, statusMap[x]?.[0] || x) },
  { title: '申请人', dataIndex: 'applicant' },
  {
    title: '操作',
    render: (_, r) => r.status === 'DRAFT' && hasPerm(auth.user, 'purchase-request:review') ? h('span', [
      h(Button, { type: 'link', onClick: () => review(r.id, 'APPROVE') }, '批准'),
      h(Button, { type: 'link', danger: true, onClick: () => review(r.id, 'REJECT') }, '驳回'),
    ]) : null,
  },
]

const innerCols = [
  { title: '物品', dataIndex: 'itemName' },
  { title: '编码', dataIndex: 'itemCode' },
  { title: '当前库存', dataIndex: 'currentStock' },
  { title: '建议数量', dataIndex: 'suggestedQuantity' },
  { title: '申请数量', dataIndex: 'quantity' },
  { title: '预计单价', dataIndex: 'unitPrice' },
]
</script>

<template>
  <Card title="采购申请">
    <template #extra><Typography.Text type="secondary">库存预警建议可作为申请数量依据</Typography.Text></template>
    <a-table row-key="id" :data-source="rows" :columns="columns" :expandable="{ expandedRowRender: (r) => h(Table, { size: 'small', pagination: false, rowKey: 'itemCode', dataSource: r.lines, columns: innerCols }) }" />
  </Card>
</template>