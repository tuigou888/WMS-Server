<script setup>
import { h, onMounted, ref } from 'vue'
import { Button, Card, Input, Modal, Space, Table, Tag, Typography, message } from 'ant-design-vue'
import { EditOutlined, SendOutlined, ShoppingCartOutlined, CheckCircleOutlined, CloseCircleOutlined, SearchOutlined, RollbackOutlined } from '@ant-design/icons-vue'
import { api } from '../api/wms'
import { money, number, dateTime } from '../utils/format'
import { actionLabel } from '../utils/labels'
import { normalizeColumns } from '../utils/table'

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
const auditModal = ref(false)
const auditForm = ref({ approve: true, remark: '' })
const cancelModal = ref(false)
const cancelReason = ref('')
const refundModal = ref(false)
const refundReason = ref('')
// 操作目标订单 id，与 currentOrder（详情展示）解耦，避免异步竞态污染
const actionTargetId = ref(null)

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
  orderLogs.value = []
  detailOpen.value = true
  const targetId = r.id
  try {
    const res = await api.marketOrder(targetId)
    // 守卫：若期间用户已切到别的订单操作，丢弃本次回调
    if (currentOrder.value?.id !== targetId) return
    currentOrder.value = res
    orderLogs.value = res.logs || []
  } catch (e) {
    message.error(e.message)
  }
}

const auditOrder = async (approve) => {
  if (!actionTargetId.value) return
  try {
    await api.auditMarketOrder(actionTargetId.value, { approve, remark: auditForm.value.remark })
    message.success(approve ? '已审核通过' : '已驳回')
    auditModal.value = false
    auditForm.value = { approve: true, remark: '' }
    detailOpen.value = false
    load()
  } catch (e) {
    message.error(e.message)
  }
}

const openAudit = (approve) => {
  auditForm.value = { approve, remark: '' }
  auditModal.value = true
}

const shipOrder = async () => {
  if (!actionTargetId.value) return
  if (!shipForm.value.logisticsCompany || !shipForm.value.logisticsNumber) return message.error('请填写物流公司与单号')
  try {
    await api.shipMarketOrder(actionTargetId.value, shipForm.value)
    message.success('已发货')
    shipModal.value = false
    detailOpen.value = false
    load()
  } catch (e) {
    message.error(e.message)
  }
}

const openShip = () => {
  shipForm.value = { logisticsCompany: '', logisticsNumber: '' }
  shipModal.value = true
}

const completeOrder = async () => {
  if (!actionTargetId.value) return
  try {
    await api.completeMarketOrder(actionTargetId.value)
    message.success('已确认收货')
    detailOpen.value = false
    load()
  } catch (e) {
    message.error(e.message)
  }
}

const cancelOrder = async () => {
  if (!actionTargetId.value) return
  try {
    await api.cancelMarketOrder(actionTargetId.value, cancelReason.value || '管理员取消')
    message.success('已取消')
    cancelModal.value = false
    cancelReason.value = ''
    detailOpen.value = false
    load()
  } catch (e) {
    message.error(e.message)
  }
}

const openCancel = () => {
  cancelReason.value = ''
  cancelModal.value = true
}

const openRefund = () => { refundReason.value = ''; refundModal.value = true }
const refundOrder = async () => {
  if (!actionTargetId.value) return
  try {
    const order = await api.refundMarketOrder(actionTargetId.value, refundReason.value || '管理员退款')
    message.success(order.payStatus === 'REFUNDING' ? '退款处理中，等待支付渠道确认' : '退款已完成')
    refundModal.value = false
    detailOpen.value = false
    load()
  } catch (e) { message.error(e.message) }
}

const confirmComplete = () => {
  Modal.confirm({
    title: '确认完成',
    content: '确认将该订单标记为已完成？此操作不可撤销。',
    okText: '确认',
    cancelText: '取消',
    onOk: () => completeOrder(),
  })
}

const statusColor = {
  PENDING: 'orange', AUDITED: 'blue', SHIPPED: 'cyan', COMPLETED: 'green',
  CANCELLED: 'red', REJECTED: 'red',
}

const statusLabel = {
  PENDING: '待审核', AUDITED: '待发货', SHIPPED: '已发货', COMPLETED: '已完成',
  CANCELLED: '已取消', REJECTED: '已驳回',
}
const payStatus = { UNPAID: ['未支付', 'default'], PAID: ['已支付', 'green'], REFUNDING: ['退款中', 'orange'], REFUNDED: ['已退款', 'purple'] }

const columns = [
  { title: '订单号', dataIndex: 'orderNo', width: 150 },
  {
    title: '状态', dataIndex: 'orderStatus', width: 90,
    render: (v) => h(Tag, { color: statusColor[v] }, statusLabel[v] || v),
  },
  { title: '支付状态', dataIndex: 'payStatus', width: 95, render: (v) => h(Tag, { color: payStatus[v]?.[1] }, payStatus[v]?.[0] || v) },
  { title: '支付方式', dataIndex: 'payType', width: 110, render: (v) => v === 'PAY_ONLINE' ? '在线支付' : v === 'CASH_ON_DELIVERY' ? '货到付款' : v === 'CREDIT' ? '挂账' : v },
  { title: '收货人', dataIndex: 'receiverName', width: 100 },
  { title: '收货电话', dataIndex: 'receiverPhone', width: 120 },
  { title: '物流公司', dataIndex: 'logisticsCompany', width: 110 },
  { title: '物流单号', dataIndex: 'logisticsNumber', width: 130 },
  { title: '金额', dataIndex: 'totalAmount', width: 100, render: (v) => money(v) },
  { title: '创建时间', dataIndex: 'createdAt', width: 150, customRender: ({ text }) => dateTime(text) },
  {
    title: '操作', width: 240,
    render: (_, r) => h(Space, [
      h(Button, { type: 'link', onClick: () => openDetail(r) }, '详情'),
      r.orderStatus === 'PENDING' ? h(Button, { type: 'link', icon: h(CheckCircleOutlined), style: { color: '#52c41a' }, onClick: () => { actionTargetId.value = r.id; openAudit(true) } }, '通过') : '',
      r.orderStatus === 'PENDING' ? h(Button, { type: 'link', danger: true, icon: h(CloseCircleOutlined), onClick: () => { actionTargetId.value = r.id; openAudit(false) } }, '驳回') : '',
      r.orderStatus === 'AUDITED' ? h(Button, { type: 'link', icon: h(SendOutlined), style: { color: '#1890ff' }, onClick: () => { actionTargetId.value = r.id; openShip() } }, '发货') : '',
      r.orderStatus === 'SHIPPED' ? h(Button, { type: 'link', icon: h(CheckCircleOutlined), style: { color: '#52c41a' }, onClick: () => { actionTargetId.value = r.id; confirmComplete() } }, '完成') : '',
      r.payStatus === 'PAID' ? h(Button, { type: 'link', icon: h(RollbackOutlined), style: { color: '#722ed1' }, onClick: () => { actionTargetId.value = r.id; openRefund() } }, '退款') : '',
      r.orderStatus !== 'COMPLETED' && r.orderStatus !== 'CANCELLED' && r.orderStatus !== 'REJECTED' ? h(Button, { type: 'link', danger: true, onClick: () => { actionTargetId.value = r.id; openCancel() } }, '取消') : '',
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
      <a-table row-key="id" :loading="loading" :data-source="data" :columns="normalizeColumns(columns)"
        :pagination="{ current: page, total, pageSize: 10, onChange: (p) => { page = p; load() }, showTotal: (t) => `共 ${t} 条` }" />
  </Card>

  <a-modal v-model:open="detailOpen" title="订单详情" :destroy-on-close="true" width="760" :footer="null">
    <template v-if="currentOrder">
      <a-descriptions bordered column="2">
        <a-descriptions-item label="订单号">{{ currentOrder.orderNo }}</a-descriptions-item>
        <a-descriptions-item label="状态">{{ statusLabel[currentOrder.orderStatus] || currentOrder.orderStatus }}</a-descriptions-item>
        <a-descriptions-item label="支付状态">{{ payStatus[currentOrder.payStatus]?.[0] || currentOrder.payStatus }}</a-descriptions-item>
        <a-descriptions-item label="退款单号">{{ currentOrder.refundNo || '-' }}</a-descriptions-item>
        <a-descriptions-item label="收货人">{{ currentOrder.receiverName }}</a-descriptions-item>
        <a-descriptions-item label="电话">{{ currentOrder.receiverPhone }}</a-descriptions-item>
        <a-descriptions-item label="地址" :span="2">{{ currentOrder.receiverAddress }}</a-descriptions-item>
        <a-descriptions-item label="物流公司">{{ currentOrder.logisticsCompany || '-' }}</a-descriptions-item>
        <a-descriptions-item label="物流单号">{{ currentOrder.logisticsNumber || '-' }}</a-descriptions-item>
        <a-descriptions-item label="总金额">{{ money(currentOrder.totalAmount) }}</a-descriptions-item>
        <a-descriptions-item label="支付方式">{{ currentOrder.payType === 'PAY_ONLINE' ? '在线支付' : currentOrder.payType === 'CASH_ON_DELIVERY' ? '货到付款' : currentOrder.payType === 'CREDIT' ? '挂账' : currentOrder.payType }}</a-descriptions-item>
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
          <div style="font-size: 13px;"><strong>{{ actionLabel(log.action) }}</strong> — {{ log.operator }} {{ dateTime(log.createdAt) }}</div>
          <div style="font-size: 12px; color: #999;">{{ log.remark }}</div>
        </a-timeline-item>
      </a-timeline>
      <div style="margin-top: 24px; text-align: right;">
        <Space>
          <Button v-if="currentOrder.orderStatus === 'PENDING'" type="primary" @click="actionTargetId = currentOrder.id; openAudit(true)">审核通过</Button>
          <Button v-if="currentOrder.orderStatus === 'PENDING'" danger @click="actionTargetId = currentOrder.id; openAudit(false)">驳回</Button>
          <Button v-if="currentOrder.orderStatus === 'AUDITED'" type="primary" @click="actionTargetId = currentOrder.id; openShip">发货</Button>
          <Button v-if="currentOrder.orderStatus === 'SHIPPED'" type="primary" @click="actionTargetId = currentOrder.id; confirmComplete">确认完成</Button>
          <Button v-if="currentOrder.orderStatus !== 'COMPLETED' && currentOrder.orderStatus !== 'CANCELLED' && currentOrder.orderStatus !== 'REJECTED'" danger @click="actionTargetId = currentOrder.id; openCancel">取消订单</Button>
        </Space>
      </div>
    </template>
  </a-modal>

  <a-modal v-model:open="auditModal" :title="auditForm.approve ? '审核通过' : '驳回订单'" width="480" :ok-text="auditForm.approve ? '确认通过' : '确认驳回'" @ok="auditOrder(auditForm.approve)">
    <a-form layout="vertical">
      <a-form-item :label="auditForm.approve ? '审核备注' : '驳回原因'">
        <a-textarea v-model:value="auditForm.remark" :placeholder="auditForm.approve ? '可填审核备注（选填）' : '请填写驳回原因'" :rows="3" />
      </a-form-item>
    </a-form>
  </a-modal>

  <a-modal v-model:open="cancelModal" title="取消订单" width="480" ok-text="确认取消" @ok="cancelOrder">
    <a-form layout="vertical">
      <a-form-item label="取消原因">
        <a-textarea v-model:value="cancelReason" placeholder="请填写取消原因（选填）" :rows="3" />
      </a-form-item>
    </a-form>
  </a-modal>

  <a-modal v-model:open="refundModal" title="发起退款" width="480" ok-text="确认发起" @ok="refundOrder">
    <a-alert type="warning" show-icon message="退款成功后将按当前后端规则处理库存回滚；已发货订单应先完成实物退回确认。" style="margin-bottom: 16px;" />
    <a-form layout="vertical"><a-form-item label="退款原因"><a-textarea v-model:value="refundReason" placeholder="请填写退款原因" :rows="3" /></a-form-item></a-form>
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
