<script setup>
import { h, onMounted, ref } from 'vue'
import { Button, Card, Table, Tag, Typography, message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { api } from '../api/wms'
import { useAuthStore } from '../stores/auth'
import { hasPerm } from '../utils/permission'
import { normalizeColumns } from '../utils/table'

const statusMap = { DRAFT: ['草稿', 'default'], APPROVED: ['已批准', 'success'], REJECTED: ['已驳回', 'error'], CANCELLED: ['已取消', 'warning'] }

const auth = useAuthStore()
const rows = ref([])
const createOpen = ref(false)
const warehouses = ref([])
const suppliers = ref([])
const items = ref([])
const createForm = ref({ warehouseId: undefined, supplierId: undefined, requiredDate: '', remark: '', lines: [] })

const load = () => api.purchaseRequests().then((x) => { rows.value = x }).catch((e) => message.error(e.message || '加载失败'))
const loadReferences = async () => {
  try {
    const [warehouseRows, supplierRows, itemPage] = await Promise.all([api.warehouses(), api.partners('SUPPLIER'), api.items({ page: 1, pageSize: 100 })])
    warehouses.value = warehouseRows.map((x) => ({ value: x.id, label: x.name }))
    suppliers.value = supplierRows.map((x) => ({ value: x.id, label: `${x.code} · ${x.name}` }))
    items.value = (itemPage.records || []).map((x) => ({ value: x.code, label: `${x.code} · ${x.name}` }))
  } catch (e) { message.error(e.message || '基础资料加载失败') }
}

onMounted(() => { load(); loadReferences() })

const review = (id, action) => api.reviewPurchaseRequest(id, { action }).then(() => { message.success('操作成功'); load() }).catch((e) => message.error(e.message || '操作失败'))
const cancel = (id) => api.cancelPurchaseRequest(id).then(() => { message.success('申请已取消'); load() }).catch((e) => message.error(e.message || '操作失败'))
const newLine = () => ({ itemCode: undefined, quantity: '', suggestedQuantity: '', currentStock: '0', unitPrice: '', remark: '' })
const openCreate = () => { createForm.value = { warehouseId: undefined, supplierId: undefined, requiredDate: '', remark: '', lines: [newLine()] }; createOpen.value = true }
const removeLine = (index) => { if (createForm.value.lines.length > 1) createForm.value.lines.splice(index, 1) }
const submit = async () => {
  const form = createForm.value
  if (!form.warehouseId || !form.lines.length || form.lines.some((x) => !x.itemCode || !x.quantity || Number(x.quantity) <= 0)) return message.error('请选择仓库，并填写每行物品和正数申请数量')
  try {
    await api.createPurchaseRequest({ ...form, supplierId: form.supplierId || null, requiredDate: form.requiredDate || null, lines: form.lines.map((x) => ({ ...x, currentStock: x.currentStock || '0', suggestedQuantity: x.suggestedQuantity || x.quantity, unitPrice: x.unitPrice || null })) })
    message.success('采购申请已创建')
    createOpen.value = false
    load()
  } catch (e) { message.error(e.message || '创建失败') }
}

const columns = [
  { title: '申请单号', dataIndex: 'requestNo' },
  { title: '供应商', dataIndex: 'supplierName', render: (x) => x || '待指定' },
  { title: '仓库', dataIndex: 'warehouseName' },
  { title: '来源', dataIndex: 'source', render: (x) => x === 'STOCK_ALERT' ? '库存预警' : '手工申请' },
  { title: '状态', dataIndex: 'status', render: (x) => h(Tag, { color: statusMap[x]?.[1] }, statusMap[x]?.[0] || x) },
  { title: '申请人', dataIndex: 'applicant' },
  {
    title: '操作',
    render: (_, r) => h('span', [
      r.status === 'DRAFT' && hasPerm(auth.user, 'purchase-request:review') ? h(Button, { type: 'link', onClick: () => review(r.id, 'APPROVE') }, '批准') : null,
      r.status === 'DRAFT' && hasPerm(auth.user, 'purchase-request:review') ? h(Button, { type: 'link', danger: true, onClick: () => review(r.id, 'REJECT') }, '驳回') : null,
      ['DRAFT', 'REJECTED'].includes(r.status) && hasPerm(auth.user, 'purchase-request:write') ? h(Button, { type: 'link', danger: true, onClick: () => cancel(r.id) }, '取消') : null,
    ].filter(Boolean)),
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
    <template #extra>
      <a-space><Typography.Text type="secondary">库存预警建议可作为申请数量依据</Typography.Text><Button v-if="hasPerm(auth.user, 'purchase-request:write')" type="primary" :icon="h(PlusOutlined)" @click="openCreate">新建申请</Button></a-space>
    </template>
    <a-table row-key="id" :data-source="rows" :columns="normalizeColumns(columns)" :expandable="{ expandedRowRender: (r) => h(Table, { size: 'small', pagination: false, rowKey: 'itemCode', dataSource: r.lines, columns: normalizeColumns(innerCols) }) }" />
  </Card>

  <a-modal v-model:open="createOpen" title="新建采购申请" width="860" ok-text="提交申请" @ok="submit">
    <a-form layout="vertical" :model="createForm">
      <a-row :gutter="16"><a-col :span="12"><a-form-item label="目标仓库" required><a-select v-model:value="createForm.warehouseId" :options="warehouses" placeholder="请选择仓库" /></a-form-item></a-col><a-col :span="12"><a-form-item label="供应商"><a-select v-model:value="createForm.supplierId" :options="suppliers" allow-clear placeholder="可稍后指定" /></a-form-item></a-col></a-row>
      <a-row :gutter="16"><a-col :span="12"><a-form-item label="期望到货日"><a-input v-model:value="createForm.requiredDate" placeholder="YYYY-MM-DD（可选）" /></a-form-item></a-col><a-col :span="12"><a-form-item label="备注"><a-input v-model:value="createForm.remark" maxlength="255" /></a-form-item></a-col></a-row>
      <a-divider orientation="left">申请明细</a-divider>
      <a-space v-for="(line, index) in createForm.lines" :key="index" align="start" style="display: flex; margin-bottom: 8px;">
        <a-select v-model:value="line.itemCode" :options="items" show-search option-filter-prop="label" placeholder="物品" style="width: 245px;" />
        <a-input v-model:value="line.quantity" placeholder="申请数量*" style="width: 110px;" />
        <a-input v-model:value="line.suggestedQuantity" placeholder="建议数量" style="width: 110px;" />
        <a-input v-model:value="line.currentStock" placeholder="当前库存" style="width: 110px;" />
        <a-input v-model:value="line.unitPrice" placeholder="预计单价" style="width: 110px;" />
        <Button danger type="text" :disabled="createForm.lines.length === 1" @click="removeLine(index)">删除</Button>
      </a-space>
      <Button type="dashed" block @click="createForm.lines.push(newLine())">添加明细</Button>
    </a-form>
  </a-modal>
</template>
