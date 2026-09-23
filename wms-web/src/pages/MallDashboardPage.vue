<script setup>
import { h, onMounted, ref } from 'vue'
import { Card, Col, Row, Skeleton, Table, Tag, Typography, message } from 'ant-design-vue'
import { ShoppingOutlined, ShoppingCartOutlined, DollarOutlined, TeamOutlined } from '@ant-design/icons-vue'
import { api } from '../api/wms'
import { money, number } from '../utils/format'
import { normalizeColumns } from '../utils/table'

const data = ref(null)

const statusLabels = {
  PENDING: '待付款', AUDITED: '待发货', SHIPPED: '已发货',
  COMPLETED: '已完成', CANCELLED: '已取消', REJECTED: '已驳回',
}
const statusColors = {
  PENDING: 'orange', AUDITED: 'blue', SHIPPED: 'cyan',
  COMPLETED: 'green', CANCELLED: 'red', REJECTED: 'red',
}

const topColumns = [
  { title: '排名', width: 60, render: (_, __, idx) => idx + 1 },
  { title: '商品编码', dataIndex: 'code', width: 130 },
  { title: '商品名称', dataIndex: 'name' },
  { title: '销量', dataIndex: 'quantity', width: 100, render: (v) => number(v) },
  { title: '销售额', dataIndex: 'amount', width: 130, render: (v) => h('b', money(v)) },
]

onMounted(() => {
  api.marketStats().then((res) => { data.value = res }).catch((e) => message.error(e.message))
})
</script>

<template>
  <div v-if="!data"><Skeleton active :paragraph="{ rows: 8 }" /></div>
  <template v-else>
    <div class="page-heading">
      <div>
        <Typography.Title :level="3" class="page-title">商城概览</Typography.Title>
        <Typography.Text class="page-subtitle" type="secondary">销售汇总、订单状态分布与热销商品</Typography.Text>
      </div>
    </div>

    <!-- 核心指标卡片 -->
    <Row :gutter="16" style="margin-bottom: 16px;">
      <Col :span="6">
        <Card class="metric-card">
          <div class="metric-icon" style="background:#e6f4ff;color:#1677ff;"><DollarOutlined /></div>
          <div class="metric-body">
            <div class="metric-label">累计销售额</div>
            <div class="metric-value">¥{{ money(data.totalSales) }}</div>
            <div class="metric-sub">已完成订单合计</div>
          </div>
        </Card>
      </Col>
      <Col :span="6">
        <Card class="metric-card">
          <div class="metric-icon" style="background:#f6ffed;color:#52c41a;"><ShoppingCartOutlined /></div>
          <div class="metric-body">
            <div class="metric-label">今日订单</div>
            <div class="metric-value">{{ number(data.todayOrders) }}</div>
            <div class="metric-sub">今日销售额 ¥{{ money(data.todaySales) }}</div>
          </div>
        </Card>
      </Col>
      <Col :span="6">
        <Card class="metric-card">
          <div class="metric-icon" style="background:#fff7e6;color:#fa8c16;"><ShoppingOutlined /></div>
          <div class="metric-body">
            <div class="metric-label">在售商品</div>
            <div class="metric-value">{{ number(data.totalProducts) }}</div>
            <div class="metric-sub">已上架商品数</div>
          </div>
        </Card>
      </Col>
      <Col :span="6">
        <Card class="metric-card">
          <div class="metric-icon" style="background:#f9f0ff;color:#722ed1;"><TeamOutlined /></div>
          <div class="metric-body">
            <div class="metric-label">客户数</div>
            <div class="metric-value">{{ number(data.totalCustomers) }}</div>
            <div class="metric-sub">收货人档案总数</div>
          </div>
        </Card>
      </Col>
    </Row>

    <Row :gutter="16">
      <!-- 订单状态分布 -->
      <Col :span="10">
        <Card class="table-card" title="订单状态分布">
          <div v-for="(cnt, status) in data.statusCounts" :key="status" class="status-row">
            <Tag :color="statusColors[status] || 'default'">{{ statusLabels[status] || status }}</Tag>
            <span class="status-count">{{ number(cnt) }} 单</span>
            <div class="status-bar-bg">
              <div class="status-bar" :style="{ width: Math.min(100, Number(cnt) * 5) + '%', background: statusColors[status] || '#999' }"></div>
            </div>
          </div>
        </Card>
      </Col>

      <!-- 热销商品前 10 名 -->
      <Col :span="14">
        <Card class="table-card" title="热销商品前 10 名（按已完成订单销量）">
          <Table :data-source="data.topProducts" :columns="normalizeColumns(topColumns)" size="small" :pagination="false" row-key="code" />
        </Card>
      </Col>
    </Row>
  </template>
</template>

<style scoped>
.page-heading { margin-bottom: 16px; }
.page-title { margin: 0 !important; margin-bottom: 4px !important; }
.page-subtitle { font-size: 13px; }
.metric-card { border-radius: 12px; display: flex; align-items: center; }
.metric-card :deep(.ant-card-body) { display: flex; align-items: center; gap: 16px; width: 100%; }
.metric-icon { width: 56px; height: 56px; border-radius: 12px; display: flex; align-items: center; justify-content: center; font-size: 28px; flex-shrink: 0; }
.metric-label { font-size: 13px; color: #999; }
.metric-value { font-size: 28px; font-weight: 700; color: #333; margin: 4px 0; }
.metric-sub { font-size: 12px; color: #bbb; }
.table-card { border-radius: 12px; }
.status-row { display: flex; align-items: center; padding: 12rpx 0; margin-bottom: 16px; }
.status-count { font-size: 14px; color: #333; font-weight: 600; margin: 0 12px; min-width: 60px; }
.status-bar-bg { flex: 1; height: 8px; background: #f0f0f0; border-radius: 4px; overflow: hidden; }
.status-bar { height: 100%; border-radius: 4px; transition: width 0.3s; }
</style>
