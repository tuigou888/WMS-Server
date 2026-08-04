<script setup>
import { computed, h, onMounted, ref } from 'vue'
import { AlertOutlined, ArrowDownOutlined, ArrowUpOutlined, DatabaseOutlined, WalletOutlined } from '@ant-design/icons-vue'
import { Card, Col, Empty, Row, Skeleton, Table, Tag, Typography, message } from 'ant-design-vue'
import { api } from '../api/wms'
import { dateTime, money, number } from '../utils/format'
import { trendOption, pieOption, profitOption, valueOption } from '../utils/charts'
import BaseChart from '../components/BaseChart.vue'

const CHART_COLORS = ['#1677ff', '#52c41a', '#fa8c16', '#eb2f96', '#722ed1', '#13c2c2', '#f5222d', '#faad14', '#2f54eb', '#a0d911']

const data = ref()

const metricStyle = [
  { icon: DatabaseOutlined, color: '#e6f4ff', iconColor: '#1677ff' },
  { icon: WalletOutlined, color: '#f6ffed', iconColor: '#52c41a' },
  { icon: ArrowDownOutlined, color: '#fff7e6', iconColor: '#fa8c16' },
  { icon: AlertOutlined, color: '#fff1f0', iconColor: '#ff4d4f' },
]

const metrics = computed(() => [
  ['库存品种', number(data.value?.stockItemCount), '当前有库存的物品', ''],
  ['库存总金额', money(data.value?.totalAmount), '按移动加权成本计价', ''],
  ['今日销售额', money(data.value?.todayOutboundAmount), '本日销售出库金额', ''],
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
  { title: '物品编码', dataIndex: 'itemCode' },
  { title: '物品名称', dataIndex: 'itemName' },
  { title: '单位', dataIndex: 'unit', width: 60 },
  { title: '库存数量', dataIndex: 'quantity', render: (v) => number(v) },
  { title: '库存金额', dataIndex: 'value', render: (v) => h('b', money(v)) },
]

const txColumns = [
  { title: '单据编号', dataIndex: 'referenceNo', ellipsis: true },
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
        <Typography.Title :level="3" class="page-title">仪表盘</Typography.Title>
        <Typography.Text class="page-subtitle" type="secondary">实时掌握仓库库存、出入库与预警情况</Typography.Text>
      </div>
    </div>

    <Row :gutter="[18, 18]">
      <Col :span="6" v-for="(m, index) in metrics" :key="m[0]">
        <Card class="metric-card">
          <div style="display: flex; justify-content: space-between;">
            <div>
              <Typography.Text type="secondary">{{ m[0] }}</Typography.Text>
              <div style="font-size: 24px; font-weight: 650; margin: 6px 0;">{{ m[1] }}</div>
              <Typography.Text class="muted">{{ m[2] }}</Typography.Text>
            </div>
            <div class="metric-icon" :style="{ background: metricStyle[index].color, color: metricStyle[index].iconColor }">
              <component :is="metricStyle[index].icon" />
            </div>
          </div>
        </Card>
      </Col>
    </Row>

    <Row :gutter="[18, 18]" style="margin-top: 18px;">
      <Col :span="14">
        <Card title="出入库趋势（近 14 天）" class="table-card">
          <BaseChart v-if="data.dailyTrend?.length" :option="trendChartOption" height="280px" />
          <a-empty v-else image="PRESENTED_IMAGE_SIMPLE" description="暂无趋势数据" />
        </Card>
      </Col>
      <Col :span="10">
        <Card title="库存分类分布（数量）" class="table-card">
          <BaseChart v-if="data.categoryDistribution?.length" :option="pieChartOption" height="280px" />
          <a-empty v-else image="PRESENTED_IMAGE_SIMPLE" description="暂无分类数据" />
        </Card>
      </Col>
    </Row>

    <Row :gutter="[18, 18]" style="margin-top: 18px;">
      <Col :span="12">
        <Card title="月度利润趋势" class="table-card">
          <BaseChart v-if="data.monthlyProfit?.length" :option="profitChartOption" height="280px" />
          <a-empty v-else image="PRESENTED_IMAGE_SIMPLE" description="暂无利润数据" />
        </Card>
      </Col>
      <Col :span="12">
        <Card title="库存金额分布（按分类）" class="table-card">
          <BaseChart v-if="data.valueByCategory?.length" :option="valueChartOption" height="280px" />
          <a-empty v-else image="PRESENTED_IMAGE_SIMPLE" description="暂无金额数据" />
        </Card>
      </Col>
    </Row>

    <Row :gutter="[18, 18]" style="margin-top: 18px;">
      <Col :span="15">
        <Card title="库存金额 TOP 物品" class="table-card">
          <a-table row-key="itemCode" :pagination="false" :data-source="data.topItemsByValue" :columns="topColumns" />
        </Card>
      </Col>
      <Col :span="9">
        <Card title="近期库存流水" class="table-card">
          <a-table row-key="id" :pagination="false" size="small" :data-source="data.recentTransactions" :columns="txColumns" />
        </Card>
        <Card title="库存预警" class="table-card" style="margin-top: 18px;">
          <template v-if="data.alerts.length">
            <div v-for="a in data.alerts" :key="a.itemId" style="display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid #f0f0f0;">
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
      </Col>
    </Row>
  </template>
</template>
