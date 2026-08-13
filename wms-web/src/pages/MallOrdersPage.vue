<script setup>
import { h, onMounted, ref } from 'vue'
import { Button, Card, Input, Modal, Space, Table, Tag, Typography, message } from 'ant-design-vue'
import { EditOutlined, SendOutlined, ShoppingCartOutlined, CheckCircleOutlined, CloseCircleOutlined } from '@ant-design/icons-vue'
import { api } from '../api/wms'
import { money, number, dateTime } from '../utils/format'

const data = ref([])
const page = ref(1)
const total = ref(0)
const loading = ref(false)
const keyword = ref('')
const statusFilter = ref()
const detailOpen = ref(false)
const currentOrder = ref(null)
const orderLogs = ref([])
const shipModal = ref(false)
const shipForm = ref({ logisticsCompany: '', logisticsNumber: '' })

const load = async () => {
  loading.value = true
  try {
    const res = await api.marketOrders({ page: page.value, pageSize: 10, keyword: keyword.value || undefined, status: statusFilter.value })
    data.value = res.records
    total.value = res.total
    page.value = res.page
  } catch (e) {
    message.error(e.message)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  load()
})

const openDetail = async (r) => {
  currentOrder.value = r
  detailOpen.value = true
  try {
    const res = await api.marketOrder(r.id)
    currentOrder.value = res
    orderLogs.value = res.logs || []
  } catch (e) {
    message.error(e.message)
  }
}

const auditOrder = async (approve) => {
  try {
    await api.auditMarketOrder(currentOrder.value.id, { approve, remark: '' })
    message.success(approve ? '已审核通过' : '已驳回')
    detailOpen.value = false
    load()
  } catch (e) {
    message.error(e.message)
  }
}

const shipOrder = async () => {
  if (!shipForm.value.logisticsCompany || !shipForm.value.logisticsNumber) return message.error('请填写物流公司与单号')
  try {
    await api.shipMarketOrder(currentOrder.value.id, shipForm.value)
    message.success('已发货')
    shipModal.value = false
    detailOpen.value = false
    load()
  } catch (e) {
    message.error(e.message)
  }
}

const completeOrder = async () => {
  try {
    await api.completeMarketOrder(currentOrder.value.id)
    message.success('已确认收货')
    detailOpen.value = false
    load()
  } catch (e) {
    message.error(e.message)
  }
}

const cancelOrder = async (reason) => {
  try {
    await api.cancelMarketOrder(currentOrder.value.id, reason)
    message.success('已取消')
    detailOpen.value = false
    load()
  } catch (e) {
    message.error(e.message)
  }
}

const statusColor = {
  PENDING: 'orange', APPROVED: 'blue', SHIPPED: 'cyan', COMPLETED: 'green',
  CANCELLED: 'red', REJECTED: 'red', RETURNED: 'purple', REFUSED: 'purple',
}

const statusLabel = {
  PENDING: '待审核', APPROVED: '已审核', SHIPPED: '已发货', COMPLETED: '已完成',
  CANCELLED: '已取消', REJECTED: '已驳回', RETURNED: '已退货', REFUSED: '已拒单',
}

const columns = [
  { title: '订单号', dataIndex: 'orderNo', width: 150 },
  {
    title: '状态', dataIndex: 'orderStatus', width: 90,
    render: (v) => h(Tag, { color: statusColor[v] }, statusLabel[v] || v),
  },
  { title: '支付方式', dataIndex: 'payType', width: 100, render: (v) => v === 'WECHAT' ? '微信' : v === 'ALIPAY' ? '支付宝' : v === 'CASH' ? '现金' : v },
  { title: '收货人', dataIndex: 'receiverName', width: 100 },
  { title: '收货电话', dataIndex: 'receiverPhone', width: 120 },
  { title: '物流公司', dataIndex: 'logisticsCompany', width: 110 },
  { title: '物流单号', dataIndex: 'logisticsNumber', width: 130 },
  { title: '金额', dataIndex: 'totalAmount', width: 100, render: (v) => money(v) },
  { title: '创建时间', dataIndex: 'createdAt', width: 150, render: (v) => dateTime(v) },
  {
    title: '操作', width: 140,
    render: (_, r) => h(Space, [
      h(Button, { type: 'link', onClick: () => openDetail(r) }, '详情'),
      r.orderStatus === 'PENDING' ? h(Button, { type: 'link', icon: h(CheckCircleOutlined), style: { color: '#52c41a' }, onClick: () => auditOrder(true) }, '审核') : '',
      r.orderStatus === 'APPROVED' ? h(Button, { type: 'link', icon: h(SendOutlined), style: { color: '#1890ff' }, onClick: () => { shipForm.value = { logisticsCompany: '', logisticsNumber: '' }; shipModal.value = true } }, '发货') : '',
      r.orderStatus === 'SHIPPED' ? h(Button, { type: 'link', icon: h(CheckCircleOutlined), style: { color: '#52c41a' }, onClick: () => completeOrder() }, '完成') : '',
      r.orderStatus !== 'COMPLETED' && r.orderStatus !== 'CANCELLED' ? h(Button, { type: 'link', danger: true, icon: h(CloseCircleOutlined), onClick: () => cancelOrder('管理员取消') }, '取消') : '',
    ].filter(Boolean)),
  },
]
</script>

<template>
  <div class="page-heading">
    <div>
      <Typography.Title :level="3" class="page-title">商城订单</Typography.Title>
      <Typography.Text class="page-subtitle" type="secondary">管理小程序商城订单、审核、发货与完成</Typography.Text>
    </div>
  </div>

  <Card class="table-card">
    <Space style="margin-bottom: 16px;">
      <a-input allow-clear placeholder="搜索订单号或收货人" :prefix="h(SearchOutlined)" v-model:value="keyword" style="width: 250px;" @press-enter="load" />
      <a-select v-model:value="statusFilter" allow-clear placeholder="订单状态" style="width: 140px;" :options="Object.keys(statusLabel).map((k) => ({ value: k, label: statusLabel[k] }))" @change="load" />
      <Button @click="load">查询</Button>
    </Space>
      <a-table row-key="id" :loading="loading" :data-source="data" :columns="columns"
        :pagination="{ current: page, total, pageSize: 10, onChange: (p) => { page = p; load() }, showTotal: (t) => `共 ${t} 条` }" />
  </Card>

  <a-modal v-model:open="detailOpen" title="订单详情" :destroy-on-close="true" width="760" :footer="null">
    <template v-if="currentOrder">
      <a-descriptions bordered column="2">
        <a-descriptions-item label="订单号">{{ currentOrder.orderNo }}</a-descriptions-item>
        <a-descriptions-item label="状态">{{ statusLabel[currentOrder.orderStatus] || currentOrder.orderStatus }}</a-descriptions-item>
        <a-descriptions-item label="收货人">{{ currentOrder.receiverName }}</a-descriptions-item>
        <a-descriptions-item label="电话">{{ currentOrder.receiverPhone }}</a-descriptions-item>
        <a-descriptions-item label="地址" :span="2">{{ currentOrder.receiverAddress }}</a-descriptions-item>
        <a-descriptions-item label="物流公司">{{ currentOrder.logisticsCompany || '-' }}</a-descriptions-item>
        <a-descriptions-item label="物流单号">{{ currentOrder.logisticsNumber || '-' }}</a-descriptions-item>
        <a-descriptions-item label="总金额">{{ money(currentOrder.totalAmount) }}</a-descriptions-item>
        <a-descriptions-item label="支付方式">{{ currentOrder.payType === 'WECHAT' ? '微信支付' : currentOrder.payType === 'ALIPAY' ? '支付宝' : currentOrder.payType === 'CASH' ? '现金' : currentOrder.payType }}</a-descriptions-item>
        <a-descriptions-item label="创建时间">{{ dateTime(currentOrder.createdAt) }}</a-descriptions-item>
        <a-descriptions-item label="备注">{{ currentOrder.remark || '-' }}</a-descriptions-item>
      </a-descriptions>
      <div style="margin-top: 16px; font-weight: 600;">商品明细</div>
      <a-table :data-source="currentOrder.items" size="small" :pagination="false" row-key="id">
        <template #columns>
          <a-table-column title="物品编码" data-index="itemCode" width="110" />
          <a-table-column title="物品名称" data-index="itemName" />
          <a-table-column title="单价" data-index="salePrice" width="90" :custom-render="{ render: (v) => money(v) }" />
          <a-table-column title="数量" data-index="quantity" width="70" :custom-render="{ render: (v) => number(v) }" />
          <a-table-column title="小计" data-index="subtotal" width="90" :custom-render="{ render: (v) => money(v) }" />
        </template>
      </a-table>
      <div style="margin-top: 16px; font-weight: 600;">操作日志</div>
      <a-timeline>
        <a-timeline-item v-for="(log, idx) in orderLogs" :key="log.id" :color="idx === orderLogs.length - 1 ? 'blue' : 'gray'">
          <div style="font-size: 13px;"><strong>{{ log.action }}</strong> — {{ log.operator }} {{ dateTime(log.createdAt) }}</div>
          <div style="font-size: 12px; color: #999;">{{ log.remark }}</div>
        </a-timeline-item>
      </a-timeline>
    </template>
  </a-modal>

  <a-modal v-model:open="shipModal" title="发货" width="480" ok-text="发货" @ok="shipOrder">
    <a-form layout="vertical">
      <a-form-item label="物流公司" required><a-input v-model:value="shipForm.logisticsCompany" placeholder="如：顺丰" /></a-form-item>
      <a-form-item label="物流单号" required><a-input v-model:value="shipForm.logisticsNumber" placeholder="如：SF123456789" /></a-form-item>
    </a-form>
  </a-modal>
</template>

<style scoped>
.page-heading { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 16px; }
.page-title { margin: 0 !important; margin-bottom: 4px !important; }
.page-subtitle { font-size: 13px; }
.table-card { border-radius: 12px; }
</style>

