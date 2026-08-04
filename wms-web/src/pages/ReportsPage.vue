<script setup>
import { h, onMounted, ref } from 'vue'
import { Card, Col, Row, Segmented, Statistic, Table, Tag, Typography, message } from 'ant-design-vue'
import { WarningOutlined } from '@ant-design/icons-vue'
import { api } from '../api/wms'
import { dateTime, money, number } from '../utils/format'

const severityColor = { HIGH: 'red', MEDIUM: 'orange', LOW: 'blue' }
const severityLabel = { HIGH: '高', MEDIUM: '中', LOW: '低' }
const priorityColor = { HIGH: 'red', MEDIUM: 'orange', LOW: 'blue' }
const priorityLabel = { HIGH: '高优先级', MEDIUM: '中优先级', LOW: '低优先级' }
const anomalyLabel = { CONTINUOUS_DECLINE: '连续出库下降', MISSING_BATCH: '缺少批次号', ABNORMAL_OUTBOUND: '出库异常' }

const tab = ref('profit')
const alerts = ref([])
const profit = ref([])
const anomalies = ref([])
const loading = ref(true)

onMounted(() => {
  loading.value = true
  Promise.all([api.alerts(), api.profit(), api.anomalies()])
    .then(([a, p, an]) => { alerts.value = a; profit.value = p; anomalies.value = an })
    .catch((e) => message.error(e.message))
    .finally(() => { loading.value = false })
})

const totalSale = () => profit.value.reduce((s, x) => s + Number(x.saleAmount || 0), 0)
const totalProfit = () => profit.value.reduce((s, x) => s + Number(x.profit || 0), 0)

const alertCols = [
  { title: '物品编码', dataIndex: 'itemCode' },
  { title: '物品名称', dataIndex: 'itemName' },
  { title: '安全库存', dataIndex: 'safetyStock', render: (v) => number(v) },
  { title: '当前库存', dataIndex: 'currentStock', render: (v) => h('b', { class: 'negative' }, number(v)) },
  { title: '缺货数量', dataIndex: 'shortage', render: (v) => h(Tag, { color: 'red' }, number(v)) },
  { title: '优先级', dataIndex: 'priority', render: (v) => h(Tag, { color: priorityColor[v] }, priorityLabel[v] || v) },
  { title: '日均出库', dataIndex: 'dailyAvgOut', render: (v) => number(v) },
  { title: '建议补货', dataIndex: 'suggestedOrder', render: (v) => h('b', number(v)) },
]

const anomalyCols = [
  { title: '类型', dataIndex: 'type', render: (v) => anomalyLabel[v] || v },
  { title: '严重程度', dataIndex: 'severity', render: (v) => h(Tag, { color: severityColor[v] }, severityLabel[v]) },
  { title: '物品编码', dataIndex: 'itemCode' },
  { title: '物品名称', dataIndex: 'itemName' },
  { title: '描述', dataIndex: 'description' },
]

const profitCols = [
  { title: '单据编号', dataIndex: 'referenceNo' },
  { title: '物品', render: (_, r) => h('span', [h('b', r.itemName), h('br'), h(Typography.Text, { type: 'secondary' }, r.itemCode)]) },
  { title: '数量', dataIndex: 'quantity', render: (v) => number(Math.abs(Number(v))) },
  { title: '成本金额', dataIndex: 'totalCostAmount', render: (v) => money(v) },
  { title: '销售金额', dataIndex: 'saleAmount', render: (v) => money(v) },
  { title: '利润', dataIndex: 'profit', render: (v) => h('b', { class: Number(v) >= 0 ? 'positive' : 'negative' }, money(v)) },
  { title: '时间', dataIndex: 'transactionAt', render: (v) => dateTime(v) },
]
</script>

<template>
  <div class="page-heading">
    <div>
      <Typography.Title :level="3" class="page-title">报表中心</Typography.Title>
      <Typography.Text class="page-subtitle" type="secondary">销售利润分析、库存预警与异常检测</Typography.Text>
    </div>
  </div>

  <Row :gutter="18">
    <Col :span="8"><Card class="metric-card"><Statistic title="销售出库笔数" :value="profit.length" /></Card></Col>
    <Col :span="8"><Card class="metric-card"><Statistic title="销售金额" :value="totalSale()" :precision="2" prefix="¥" /></Card></Col>
    <Col :span="8">
      <Card class="metric-card">
        <Statistic title="销售利润" :value="totalProfit()" :precision="2" prefix="¥" :value-style="{ color: totalProfit() >= 0 ? '#16a34a' : '#dc2626' }" />
      </Card>
    </Col>
  </Row>

  <Card title="库存预警（智能优先级）" class="table-card" style="margin-top: 18px;">
    <a-table row-key="itemId" :loading="loading" :data-source="alerts" :columns="alertCols" />
  </Card>

  <Card class="table-card" style="margin-top: 18px;">
    <template #title><WarningOutlined /> 库存异常检测</template>
    <a-table v-if="anomalies.length" :row-key="(r, i) => r.type + '-' + i" :loading="loading" :data-source="anomalies" :columns="anomalyCols" />
    <Typography.Text v-else type="secondary">暂无异常检测结果</Typography.Text>
  </Card>

  <Card title="销售利润明细" class="table-card" style="margin-top: 18px;">
    <a-table row-key="id" :loading="loading" :data-source="profit" :columns="profitCols" />
  </Card>
</template>