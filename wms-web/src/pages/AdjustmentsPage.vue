<script setup>
import { h, onMounted, ref, watch } from 'vue'
import { Button, Card, Descriptions, Drawer, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Table, Tag, Typography, message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { api } from '../api/wms'
import { dateTime, number } from '../utils/format'
import { useAuthStore } from '../stores/auth'
import { hasPerm } from '../utils/permission'

const statuses = { DRAFT: ['草稿', 'default'], APPROVED: ['已审核', 'blue'], REJECTED: ['已驳回', 'red'], COMPLETED: ['已执行', 'green'] }

const auth = useAuthStore()
const data = ref([])
const items = ref([])
const warehouses = ref([])
const open = ref(false)
const detail = ref(null)
const drawerOpen = ref(false)
watch(drawerOpen, (v) => { if (!v) detail.value = null })
watch(detail, (v) => { drawerOpen.value = !!v })
const formState = ref({ action: 'LOSS', lines: [{ quantity: 1 }] })
const formRef = ref()

const load = () => api.adjustments().then((x) => { data.value = x }).catch((e) => message.error(e.message))

onMounted(() => {
  load()
  Promise.all([api.items({ pageSize: 1000 }), api.warehouses()])
    .then(([i, w]) => { items.value = i.records; warehouses.value = w })
    .catch((e) => message.error(e.message))
})

const save = async () => {
  try {
    await formRef.value.validateFields()
    await api.createAdjustment({ ...formState.value, lines: formState.value.lines.map((l) => ({ ...l })) })
    message.success('报损报溢草稿已创建')
    open.value = false
    load()
  } catch (e) {
    if (e?.errorFields) return
    message.error(e.message)
  }
}

const act = async (fn) => {
  try { await fn(); message.success('操作成功'); load(); detail.value = null } catch (e) { message.error(e.message) }
}
const review = (r, actionType) => act(() => api.reviewAdjustment(r.id, { action: actionType }))

const addLine = () => { formState.value.lines.push({ quantity: 1 }) }
const removeLine = (idx) => { formState.value.lines.splice(idx, 1) }

const columns = [
  { title: '单据号', dataIndex: 'adjustmentNo' },
  { title: '类型', dataIndex: 'action', render: (v) => h(Tag, { color: v === 'LOSS' ? 'volcano' : 'green' }, v === 'LOSS' ? '报损' : '报溢') },
  { title: '仓库', dataIndex: 'warehouseName' },
  { title: '原因', dataIndex: 'reason' },
  { title: '状态', dataIndex: 'status', render: (v) => h(Tag, { color: statuses[v]?.[1] }, statuses[v]?.[0] || v) },
  { title: '审核人', dataIndex: 'reviewer' },
  { title: '创建时间', dataIndex: 'createdAt', render: (v) => dateTime(v) },
  {
    title: '操作',
    render: (_, r) => h(Space, [
      h(Button, { type: 'link', onClick: () => { detail.value = r } }, '详情'),
      r.status === 'DRAFT' && hasPerm(auth.user, 'adjustment:review') ? h('span', [
        h(Button, { type: 'link', onClick: () => review(r, 'APPROVE') }, '审核通过'),
        h(Button, { type: 'link', danger: true, onClick: () => review(r, 'REJECT') }, '驳回'),
      ]) : null,
      r.status === 'APPROVED' ? h(Popconfirm, { title: '执行后将调整库存，确认继续？', onConfirm: () => act(() => api.completeAdjustment(r.id)) }, { default: () => h(Button, { type: 'primary' }, '执行') }) : null,
    ]),
  },
]

const detailCols = [
  { title: '物品', render: (_, r) => `${r.itemCode} · ${r.itemName}` },
  { title: '库位', dataIndex: 'locationCode' },
  { title: '批次', dataIndex: 'batchNo', render: (v) => v || '-' },
  { title: '数量', dataIndex: 'quantity', render: (v) => number(v) },
]
</script>

<template>
  <div class="page-heading">
    <div>
      <Typography.Title :level="3" class="page-title">报损 / 报溢</Typography.Title>
      <Typography.Text type="secondary">用于货物破损、过期、丢失、盘盈等非采购/销售性质库存调整</Typography.Text>
    </div>
    <Button type="primary" :icon="h(PlusOutlined)" @click="formState = { action: 'LOSS', lines: [{ quantity: 1 }] }; open = true">新建报损报溢</Button>
  </div>

  <Card class="table-card">
    <a-table row-key="id" :data-source="data" :columns="columns" />
  </Card>

  <a-modal v-model:open="open" title="新建报损 / 报溢草稿" width="850" :destroy-on-close="true" @ok="save">
    <a-form ref="formRef" layout="vertical" :model="formState">
      <a-space-compact style="width: 100%;">
        <a-form-item name="action" label="类型" :rules="[{ required: true }]" style="width: 25%;">
          <a-select v-model:value="formState.action" :options="[{ value: 'LOSS', label: '报损（减库存）' }, { value: 'GAIN', label: '报溢（加库存）' }]" />
        </a-form-item>
        <a-form-item name="warehouseId" label="仓库" :rules="[{ required: true }]" style="width: 40%;">
          <a-select v-model:value="formState.warehouseId" :options="warehouses.map((x) => ({ value: x.id, label: x.name }))" />
        </a-form-item>
        <a-form-item name="reason" label="原因" style="width: 35%;">
          <a-input v-model:value="formState.reason" placeholder="破损 / 过期 / 盘盈 / 丢失" />
        </a-form-item>
      </a-space-compact>
      <template v-for="(line, index) in formState.lines" :key="index">
        <Card size="small" :title="`明细 ${index + 1}`" style="margin-bottom: 10px;">
          <template v-if="formState.lines.length > 1" #extra>
            <Button type="link" danger @click="removeLine(index)">删除</Button>
          </template>
          <a-space-compact style="width: 100%;">
            <a-form-item :name="['lines', index, 'itemCode']" :rules="[{ required: true }]" style="width: 50%;">
              <a-select v-model:value="line.itemCode" show-search placeholder="物品" :options="items.map((x) => ({ value: x.code, label: `${x.code} · ${x.name}` }))" />
            </a-form-item>
            <a-form-item :name="['lines', index, 'locationCode']" :rules="[{ required: true }]" style="width: 30%;">
              <a-input v-model:value="line.locationCode" placeholder="库位" />
            </a-form-item>
            <a-form-item :name="['lines', index, 'quantity']" :rules="[{ required: true }]" style="width: 20%;">
              <a-input-number v-model:value="line.quantity" :min="0.0001" style="width: 100%;" addon-before="数量" />
            </a-form-item>
          </a-space-compact>
        </Card>
      </template>
      <Button block @click="addLine">增加明细</Button>
      <a-form-item name="remark" label="备注">
        <a-textarea v-model:value="formState.remark" />
      </a-form-item>
    </a-form>
  </a-modal>

  <a-drawer v-model:open="drawerOpen" :title="detail?.adjustmentNo" width="620">
    <template v-if="detail">
      <Descriptions bordered size="small" :column="1">
        <Descriptions.Item label="类型">{{ detail.action === 'LOSS' ? '报损' : '报溢' }}</Descriptions.Item>
        <Descriptions.Item label="状态">{{ statuses[detail.status]?.[0] }}</Descriptions.Item>
        <Descriptions.Item label="仓库">{{ detail.warehouseName }}</Descriptions.Item>
        <Descriptions.Item label="原因">{{ detail.reason || '-' }}</Descriptions.Item>
        <Descriptions.Item label="备注">{{ detail.remark || '-' }}</Descriptions.Item>
      </Descriptions>
      <a-table style="margin-top: 16px;" :pagination="false" row-key="id" :data-source="detail.lines" :columns="detailCols" />
    </template>
  </a-drawer>
</template>