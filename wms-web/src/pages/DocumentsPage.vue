<script setup>
import { computed, h, onMounted, ref, watch } from 'vue'
import dayjs from 'dayjs'
import { Button, Card, DatePicker, Descriptions, Drawer, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Table, Tag, Typography, message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { api } from '../api/wms'
import { dateTime, money, number } from '../utils/format'
import { useAuthStore } from '../stores/auth'
import { hasPerm } from '../utils/permission'

const statuses = { DRAFT: ['草稿', 'default'], APPROVED: ['已审核', 'blue'], REJECTED: ['已驳回', 'red'], COMPLETED: ['已执行', 'green'], CANCELLED: ['已取消', 'default'] }
const typeLabels = { IN: ['采购入库', 'green'], OUT: ['销售出库', 'volcano'], RETURN_IN: ['退货入库', 'cyan'], RETURN_OUT: ['退货出库', 'orange'] }

const auth = useAuthStore()

const data = ref([])
const items = ref([])
const warehouses = ref([])
const partners = ref([])
const open = ref(false)
const detail = ref(null)
const drawerOpen = ref(false)
watch(drawerOpen, (v) => { if (!v) detail.value = null })
watch(detail, (v) => { drawerOpen.value = !!v })
const formState = ref({ type: 'IN', businessDate: dayjs(), lines: [{ quantity: 1, unitPrice: 0, locationCode: 'A-01-01' }] })
const formRef = ref()

const documentType = computed(() => formState.value.type)
const isOut = computed(() => ['OUT', 'RETURN_OUT'].includes(documentType.value))
const availablePartners = computed(() => partners.value.filter((x) => x.enabled && (x.type === 'BOTH' || x.type === (isOut.value ? 'CUSTOMER' : 'SUPPLIER'))))

const itemOptions = computed(() => items.value.map((x) => ({ value: x.code, label: `${x.code} · ${x.name}` })))

const load = () => api.documents().then((x) => { data.value = x }).catch((e) => message.error(e.message))

onMounted(() => {
  load()
  Promise.all([api.items({ pageSize: 1000 }), api.warehouses(), api.partners()])
    .then(([i, w, p]) => { items.value = i.records; warehouses.value = w; partners.value = p })
    .catch((e) => message.error(e.message))
})

const save = async () => {
  try {
    await formRef.value.validateFields()
    const v = { ...formState.value, lines: formState.value.lines.map((l) => ({ ...l })) }
    if (v.businessDate?.format) v.businessDate = v.businessDate.format('YYYY-MM-DD')
    await api.createDocument(v)
    message.success('草稿已创建')
    open.value = false
    load()
  } catch (e) {
    if (e?.errorFields) return
    message.error(e.message)
  }
}

const action = async (fn) => {
  try {
    await fn()
    message.success('操作成功')
    load()
    if (detail.value) detail.value = await api.document(detail.value.id)
  } catch (e) {
    message.error(e.message)
  }
}

const review = (r, actionType) => action(() => api.reviewDocument(r.id, { action: actionType }))

const addLine = () => { formState.value.lines.push({ quantity: 1, unitPrice: 0, locationCode: 'A-01-01' }) }
const removeLine = (idx) => { formState.value.lines.splice(idx, 1) }

const columns = [
  { title: '单据号', dataIndex: 'documentNo' },
  { title: '类型', dataIndex: 'type', render: (v) => h(Tag, { color: typeLabels[v]?.[1] }, typeLabels[v]?.[0] || v) },
  { title: '往来单位', dataIndex: 'partnerName' },
  { title: '仓库', dataIndex: 'warehouseName' },
  { title: '状态', dataIndex: 'status', render: (v) => h(Tag, { color: statuses[v]?.[1] }, statuses[v]?.[0] || v) },
  { title: '日期', dataIndex: 'businessDate' },
  { title: '创建时间', dataIndex: 'createdAt', render: (v) => dateTime(v) },
  {
    title: '操作',
    render: (_, r) => h(Space, [
      h(Button, { type: 'link', onClick: () => api.document(r.id).then(setDetail).catch((e) => message.error(e.message)) }, '详情'),
      r.status === 'DRAFT' && hasPerm(auth.user, 'document:review') ? h('span', [
        h(Button, { type: 'link', onClick: () => review(r, 'APPROVE') }, '审核通过'),
        h(Button, { type: 'link', danger: true, onClick: () => review(r, 'REJECT') }, '驳回'),
      ]) : null,
      r.status === 'APPROVED' ? h(Popconfirm, {
        title: '执行后将产生库存流水，确认继续？',
        onConfirm: () => action(() => api.completeDocument(r.id)),
      }, { default: () => h(Button, { type: 'primary' }, '执行') }) : null,
      r.status === 'DRAFT' ? h(Button, { type: 'link', danger: true, onClick: () => action(() => api.cancelDocument(r.id)) }, '取消') : null,
      r.status === 'COMPLETED' && hasPerm(auth.user, 'document:review') ? h('span', [
        h(Popconfirm, { title: '反审将冲销库存流水并回到已审核，确认？', onConfirm: () => action(() => api.uncompleteDocument(r.id)) }, { default: () => h(Button, { type: 'link' }, '反审') }),
        h(Popconfirm, { title: '红冲将生成反向单据并保留原单据执行记录，确认？', onConfirm: () => action(() => api.reverseDocument(r.id)) }, { default: () => h(Button, { type: 'link', danger: true }, '红冲') }),
      ]) : null,
    ]),
  },
]

const detailColumns = [
  { title: '物品', render: (_, r) => `${r.itemCode} · ${r.itemName}` },
  { title: '库位', dataIndex: 'locationCode' },
  { title: '数量', dataIndex: 'quantity', render: (v) => number(v) },
  { title: '单价', dataIndex: 'unitPrice', render: (v) => money(v) },
]

const setDetail = (d) => { detail.value = d }
</script>

<template>
  <div class="page-heading">
    <div>
      <Typography.Title :level="3" class="page-title">入库 / 出库单</Typography.Title>
      <Typography.Text type="secondary">先建草稿，管理员审核后再执行库存变动；已执行可反审 / 红冲</Typography.Text>
    </div>
    <Button type="primary" :icon="h(PlusOutlined)" @click="formState = { type: 'IN', businessDate: dayjs(), lines: [{ quantity: 1, unitPrice: 0, locationCode: 'A-01-01' }] }; open = true">新建单据</Button>
  </div>

  <Card class="table-card">
    <a-table row-key="id" :data-source="data" :columns="columns" />
  </Card>

  <a-modal v-model:open="open" title="新建入/出/退货草稿单" width="850" :destroy-on-close="true" @ok="save">
    <a-form ref="formRef" layout="vertical" :model="formState">
      <a-space-compact style="width: 100%;">
        <a-form-item name="type" label="单据类型" :rules="[{ required: true }]" style="width: 25%;">
          <a-select v-model:value="formState.type" @change="formState.partnerId = undefined" :options="[
            { value: 'IN', label: '采购入库' },
            { value: 'OUT', label: '销售出库' },
            { value: 'RETURN_IN', label: '退货入库（客户退回）' },
            { value: 'RETURN_OUT', label: '退货出库（退回供应商）' },
          ]" />
        </a-form-item>
        <a-form-item name="warehouseId" label="仓库" :rules="[{ required: true }]" style="width: 35%;">
          <a-select v-model:value="formState.warehouseId" :options="warehouses.map((x) => ({ value: x.id, label: x.name }))" />
        </a-form-item>
        <a-form-item name="partnerId" label="往来单位" style="width: 40%;">
          <a-select v-model:value="formState.partnerId" allow-clear :placeholder="isOut ? '选择客户（可选）' : '选择供应商（可选）'" :options="availablePartners.map((x) => ({ value: x.id, label: `${x.code} · ${x.name}` }))" />
        </a-form-item>
      </a-space-compact>
      <a-form-item name="businessDate" label="业务日期">
        <a-date-picker v-model:value="formState.businessDate" style="width: 100%;" />
      </a-form-item>

      <template v-for="(line, index) in formState.lines" :key="index">
        <Card size="small" :title="`明细 ${index + 1}`" style="margin-bottom: 10px;">
          <template v-if="formState.lines.length > 1" #extra>
            <Button type="link" danger @click="removeLine(index)">删除</Button>
          </template>
          <a-space-compact style="width: 100%;">
            <a-form-item :name="['lines', index, 'itemCode']" :rules="[{ required: true }]" style="width: 32%;">
              <a-select v-model:value="line.itemCode" show-search placeholder="物品" :options="itemOptions" />
            </a-form-item>
            <a-form-item :name="['lines', index, 'locationCode']" :rules="[{ required: true }]" style="width: 22%;">
              <a-input v-model:value="line.locationCode" placeholder="库位" />
            </a-form-item>
            <a-form-item :name="['lines', index, 'quantity']" :rules="[{ required: true }]" style="width: 22%;">
              <a-input-number v-model:value="line.quantity" :min="0.0001" style="width: 100%;" addon-before="数量" />
            </a-form-item>
            <a-form-item :name="['lines', index, 'unitPrice']" :rules="[{ required: true }]" style="width: 24%;">
              <a-input-number v-model:value="line.unitPrice" :min="0" style="width: 100%;" addon-before="单价" />
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

  <a-drawer v-model:open="drawerOpen" :title="detail?.documentNo" width="620" @close="detail = null">
    <template v-if="detail">
      <Descriptions bordered size="small" :column="1">
        <Descriptions.Item label="类型">{{ typeLabels[detail.type]?.[0] || detail.type }}</Descriptions.Item>
        <Descriptions.Item label="状态">{{ statuses[detail.status]?.[0] }}</Descriptions.Item>
        <Descriptions.Item label="单位">{{ detail.partnerName || '-' }}</Descriptions.Item>
        <Descriptions.Item label="仓库">{{ detail.warehouseName }}</Descriptions.Item>
      </Descriptions>
      <a-table style="margin-top: 16px;" :pagination="false" row-key="id" :data-source="detail.lines" :columns="detailColumns" />
    </template>
  </a-drawer>
</template>