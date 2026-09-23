<script setup>
import { computed, h, onMounted, ref } from 'vue'
import { AlertOutlined, ArrowDownOutlined, DatabaseOutlined, WalletOutlined } from '@ant-design/icons-vue'
import { Card, Tag, Typography, message } from 'ant-design-vue'
import { api } from '../api/wms'
import { dateTime, money, number } from '../utils/format'
import { normalizeColumns } from '../utils/table'
import { trendOption, pieOption, profitOption, valueOption } from '../utils/charts'
import BaseChart from '../components/BaseChart.vue'

const CHART_COLORS = ['#0b6b53', '#2563a6', '#b7791f', '#7c5ac7', '#3f7f71', '#9a6a24', '#62748a', '#b42318']

const data = ref()

const metricStyle = [
  { icon: DatabaseOutlined, color: '#e4f3ed', iconColor: '#0b6b53' },
  { icon: WalletOutlined, color: '#fff6db', iconColor: '#b7791f' },
  { icon: ArrowDownOutlined, color: '#e9f2fb', iconColor: '#2563a6' },
  { icon: AlertOutlined, color: '#fce9e7', iconColor: '#b42318' },
]

const metrics = computed(() => [
  ['库存品种', number(data.value?.stockItemCount), '当前有库存的物品', ''],
  ['库存总金额', money(data.value?.totalAmount), '按移动加权成本计价', ''],
  ['今日销售额', money(data.value?.todaySalesAmount), '仅统计销售出库金额', ''],
  ['库存预警', number(data.value?.alertCount), '低于安全库存的物品', ''],
])

const pieData = computed(() => data.value?.categoryDistribution?.length
  ? data.value.categoryDistribution.map((d) => ({ ...d, value: Number(d.value) }))
  : [{ name: '暂无数据', value: 1 }])

const trendChartOption = computed(() => trendOption(data.value?.dailyTrend || []))
const pieChartOption = computed(() => pieOption(pieData.value, CHART_COLORS))
const profitChartOption = computed(() => profitOption(data.value?.monthlyProfit || []))
const valueChartOption = computed(() => valueOption(data.value?.valueByCategory || [], CHART_COLORS))

const topColumns = [
  { title: '物品编码', dataIndex: 'itemCode', render: (v) => h('span', { class: 'mono' }, v) },
  { title: '物品名称', dataIndex: 'itemName' },
  { title: '单位', dataIndex: 'unit', width: 60 },
  { title: '库存数量', dataIndex: 'quantity', render: (v) => number(v) },
  { title: '库存金额', dataIndex: 'value', render: (v) => h('b', money(v)) },
]

const txColumns = [
  { title: '单据编号', dataIndex: 'referenceNo', ellipsis: true, render: (v) => h('span', { class: 'mono' }, v) },
  { title: '类型', dataIndex: 'transactionType', width: 60, render: (v) => h(Tag, { color: v === 'in' ? 'green' : 'volcano' }, v === 'in' ? '入库' : '出库') },
  { title: '金额', render: (_, r) => r.saleAmount ? money(r.saleAmount) : money(r.totalCostAmount) },
]

onMounted(() => {
  api.dashboard().then((x) => { data.value = x }).catch((e) => message.error(e.message))
})
</script>

<template>
  <div v-if="!data"><a-skeleton active :paragraph="{ rows: 10 }" /></div>
  <template v-else>
    <div class="page-heading">
      <div>
        <Typography.Title :level="3" class="page-title">仓储运营概览</Typography.Title>
        <Typography.Text class="page-subtitle" type="secondary">以库存资产、流转效率和异常风险为核心的今日工作面板</Typography.Text>
      </div>
      <div class="dashboard-status" aria-label="数据状态">
        <span class="dashboard-status-dot" aria-hidden="true" />
        <span>数据实时同步</span>
      </div>
    </div>

    <div class="dashboard-metrics">
      <Card v-for="(m, index) in metrics" :key="m[0]" class="metric-card">
        <div class="metric-content">
          <div>
            <Typography.Text class="metric-label">{{ m[0] }}</Typography.Text>
            <div class="metric-value amount">{{ m[1] }}</div>
            <Typography.Text class="metric-note">{{ m[2] }}</Typography.Text>
          </div>
          <div class="metric-icon" :style="{ background: metricStyle[index].color, color: metricStyle[index].iconColor }" aria-hidden="true">
            <component :is="metricStyle[index].icon" />
          </div>
        </div>
      </Card>
    </div>

    <div class="dashboard-main-grid">
      <Card title="出入库趋势 · 近 14 天" class="table-card">
        <BaseChart v-if="data.dailyTrend?.length" :option="trendChartOption" height="280px" />
        <a-empty v-else image="PRESENTED_IMAGE_SIMPLE" description="暂无趋势数据" />
      </Card>
      <Card title="库存分类分布 · 数量" class="table-card">
        <BaseChart v-if="data.categoryDistribution?.length" :option="pieChartOption" height="280px" />
        <a-empty v-else image="PRESENTED_IMAGE_SIMPLE" description="暂无分类数据" />
      </Card>
    </div>

    <div class="dashboard-secondary-grid">
      <Card title="月度利润趋势" class="table-card">
        <BaseChart v-if="data.monthlyProfit?.length" :option="profitChartOption" height="280px" />
        <a-empty v-else image="PRESENTED_IMAGE_SIMPLE" description="暂无利润数据" />
      </Card>
      <Card title="库存金额分布 · 分类" class="table-card">
        <BaseChart v-if="data.valueByCategory?.length" :option="valueChartOption" height="280px" />
        <a-empty v-else image="PRESENTED_IMAGE_SIMPLE" description="暂无金额数据" />
      </Card>
    </div>

    <div class="dashboard-bottom-grid">
      <Card title="库存金额排名" class="table-card">
        <a-table row-key="itemCode" :pagination="false" :data-source="data.topItemsByValue" :columns="normalizeColumns(topColumns)" />
      </Card>
      <div class="dashboard-stack">
        <Card title="近期库存流水" class="table-card">
          <a-table row-key="id" :pagination="false" size="small" :data-source="data.recentTransactions" :columns="normalizeColumns(txColumns)" />
        </Card>
        <Card title="库存预警" class="table-card">
          <template v-if="data.alerts?.length">
            <div v-for="a in data.alerts" :key="a.itemId" class="dashboard-alert-row">
              <div>
                <b>{{ a.itemName }}</b><br />
                <Typography.Text type="secondary">{{ a.itemCode }}</Typography.Text>
              </div>
              <div style="text-align: right;">
                <Typography.Text class="negative">缺少 {{ number(a.shortage) }} {{ a.unit }}</Typography.Text><br />
                <Typography.Text type="secondary">安全库存 {{ number(a.safetyStock) }}</Typography.Text>
              </div>
            </div>
          </template>
          <a-empty v-else image="PRESENTED_IMAGE_SIMPLE" description="暂无库存预警" />
        </Card>
      </div>
    </div>
  </template>
</template>
