<script setup>
import { computed, h, onMounted, ref } from 'vue'
import { Button, Card, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Table, Tag, Typography, message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { api } from '../api/wms'
import { dateTime, number } from '../utils/format'
import { useAuthStore } from '../stores/auth'
import { hasPerm } from '../utils/permission'

const statusLabels = { DRAFT: ['草稿', 'default'], APPROVED: ['已审核', 'blue'], REJECTED: ['已驳回', 'red'], COMPLETED: ['已执行', 'green'] }

const auth = useAuthStore()
const rows = ref([])
const warehouses = ref([])
const items = ref([])
const transferOpen = ref(false)
const warehouseOpen = ref(false)
const formState = ref({ lines: [{ quantity: 1, sourceLocationCode: 'A-01-01', targetLocationCode: 'A-01-01' }] })
const warehouseFormState = ref({})

const loadTransfers = () => api.transfers().then((x) => { rows.value = x }).catch((e) => message.error(e.message))
const loadWarehouses = () => api.warehouses().then((x) => { warehouses.value = x }).catch((e) => message.error(e.message))

onMounted(() => {
  loadTransfers()
  loadWarehouses()
  api.items({ pageSize: 1000 }).then((p) => { items.value = p.records }).catch((e) => message.error(e.message))
})

const openTransfer = () => {
  formState.value = { lines: [{ quantity: 1, sourceLocationCode: 'A-01-01', targetLocationCode: 'A-01-01' }] }
  transferOpen.value = true
}

const createTransfer = async () => {
  try {
    await api.createTransfer({ ...formState.value, lines: formState.value.lines.map((l) => ({ ...l })) })
    message.success('调拨草稿已创建')
    transferOpen.value = false
    loadTransfers()
  } catch (e) {
    if (e?.errorFields) return
    message.error(e.message)
  }
}

const createWarehouse = async () => {
  try {
    const data = await api.createWarehouse({ ...warehouseFormState.value, status: true })
    message.success('仓库创建成功，可立即用于调拨')
    warehouseFormState.value = {}
    warehouseOpen.value = false
    await loadWarehouses()
    formState.value.targetWarehouseId = data.id
  } catch (e) {
    if (e?.errorFields) return
    message.error(e.message)
  }
}

const act = async (request) => {
  try { await request(); message.success('操作成功'); loadTransfers() } catch (e) { message.error(e.message) }
}

const warehouseOptions = computed(() => warehouses.value.map((w) => ({ value: w.id, label: `${w.code} · ${w.name}` })))

const addLine = () => { formState.value.lines.push({ quantity: 1, sourceLocationCode: 'A-01-01', targetLocationCode: 'A-01-01' }) }
const removeLine = (idx) => { formState.value.lines.splice(idx, 1) }

const columns = [
  { title: '调拨单号', dataIndex: 'transferNo' },
  { title: '调出仓', dataIndex: 'sourceWarehouseName' },
  { title: '调入仓', dataIndex: 'targetWarehouseName' },
  { title: '明细', render: (_, r) => r.lines.map((l) => `${l.itemCode}${l.batchNo ? ` [${l.batchNo}]` : ''} × ${number(l.quantity)}`).join('；') },
  { title: '状态', dataIndex: 'status', render: (v) => h(Tag, { color: statusLabels[v]?.[1] }, statusLabels[v]?.[0]) },
  { title: '创建时间', dataIndex: 'createdAt', render: (v) => dateTime(v) },
  {
    title: '操作',
    render: (_, r) => h(Space, [
      r.status === 'DRAFT' && hasPerm(auth.user, 'transfer:review') ? h('span', [
        h(Button, { type: 'link', onClick: () => act(() => api.reviewTransfer(r.id, { action: 'APPROVE' })) }, '审核通过'),
        h(Button, { type: 'link', danger: true, onClick: () => act(() => api.reviewTransfer(r.id, { action: 'REJECT' })) }, '驳回'),
      ]) : null,
      r.status === 'APPROVED' ? h(Popconfirm, { title: '确认执行调拨？', onConfirm: () => act(() => api.completeTransfer(r.id)) }, { default: () => h(Button, { type: 'primary' }, '执行') }) : null,
    ]),
  },
]
</script>

<template>
  <div class="page-heading">
    <div>
      <Typography.Title :level="3" class="page-title">库存调拨</Typography.Title>
      <Typography.Text type="secondary">跨仓库、跨库位调拨，审核通过后才会同步扣减与转入库存</Typography.Text>
    </div>
    <Space>
      <Button v-if="hasPerm(auth.user, 'warehouse:manage')" @click="warehouseFormState = {}; warehouseOpen = true">新增仓库</Button>
      <Button type="primary" :icon="h(PlusOutlined)" :disabled="warehouses.length < 2" @click="openTransfer">新建调拨</Button>
    </Space>
  </div>

  <Typography.Paragraph v-if="warehouses.length < 2" type="warning">请先新增至少一个启用仓库，才能发起跨仓库调拨。</Typography.Paragraph>

  <Card class="table-card">
    <a-table row-key="id" :data-source="rows" :columns="columns" />
  </Card>

  <a-modal v-model:open="transferOpen" title="新建调拨草稿" width="840" :destroy-on-close="true" @ok="createTransfer">
    <a-form layout="vertical" :model="formState">
      <a-space-compact style="width: 100%;">
        <a-form-item name="sourceWarehouseId" label="调出仓库" :rules="[{ required: true, message: '请选择调出仓库' }]" style="width: 50%;">
          <a-select v-model:value="formState.sourceWarehouseId" :options="warehouseOptions" placeholder="选择调出仓库" />
        </a-form-item>
        <a-form-item name="targetWarehouseId" label="调入仓库" :rules="[{ required: true, message: '请选择调入仓库' }]" style="width: 50%;">
          <a-select v-model:value="formState.targetWarehouseId" :options="warehouseOptions" placeholder="选择调入仓库" />
        </a-form-item>
      </a-space-compact>
      <template v-for="(line, index) in formState.lines" :key="index">
        <Space style="display: flex; margin-bottom: 8px;" align="baseline">
          <a-form-item :name="['lines', index, 'itemCode']" :rules="[{ required: true, message: '请选择物品' }]">
            <a-select style="width: 250px;" v-model:value="line.itemCode" placeholder="物品" :options="items.map((it) => ({ value: it.code, label: `${it.code} · ${it.name}` }))" />
          </a-form-item>
          <a-form-item :name="['lines', index, 'sourceLocationCode']" :rules="[{ required: true, message: '请输入调出库位' }]">
            <a-input v-model:value="line.sourceLocationCode" placeholder="调出库位" />
          </a-form-item>
          <a-form-item :name="['lines', index, 'targetLocationCode']" :rules="[{ required: true, message: '请输入调入库位' }]">
            <a-input v-model:value="line.targetLocationCode" placeholder="调入库位" />
          </a-form-item>
          <a-form-item :name="['lines', index, 'batchNo']">
            <a-input v-model:value="line.batchNo" placeholder="批次号（可选）" />
          </a-form-item>
          <a-form-item :name="['lines', index, 'quantity']" :rules="[{ required: true, message: '请输入数量' }]">
            <a-input-number v-model:value="line.quantity" :min="0.0001" addon-before="数量" />
          </a-form-item>
          <Button v-if="formState.lines.length > 1" danger type="link" @click="removeLine(index)">删除</Button>
        </Space>
      </template>
      <Button block @click="addLine">增加明细</Button>
      <a-form-item name="remark" label="备注">
        <a-textarea v-model:value="formState.remark" />
      </a-form-item>
    </a-form>
  </a-modal>

  <a-modal v-model:open="warehouseOpen" title="新增仓库" :destroy-on-close="true" @ok="createWarehouse">
    <a-form layout="vertical" :model="warehouseFormState">
      <a-form-item name="code" label="仓库编码" :rules="[{ required: true, message: '请输入仓库编码' }]">
        <a-input v-model:value="warehouseFormState.code" placeholder="例如 WH-002" />
      </a-form-item>
      <a-form-item name="name" label="仓库名称" :rules="[{ required: true, message: '请输入仓库名称' }]">
        <a-input v-model:value="warehouseFormState.name" placeholder="例如 备件仓" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>