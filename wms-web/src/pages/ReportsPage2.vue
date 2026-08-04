<script setup>
import { computed, h, ref, watch } from 'vue'
import dayjs from 'dayjs'
import { Button, Card, DatePicker, Input, Segmented, Space, Table, Tag, Typography, message } from 'ant-design-vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import { api } from '../api/wms'
import { dateTime, money, number } from '../utils/format'

const bucketColor = { '>90': 'magenta', '60-90': 'volcano', '30-60': 'orange', '0-30': 'green' }

const tab = ref('age')
const age = ref([])
const summary = ref([])
const summaryLoading = ref(false)
const ageLoading = ref(false)
const periodDayjs = ref(dayjs())
const period = computed(() => periodDayjs.value.format('YYYY-MM'))
const keyword = ref('')

const loadAge = () => { ageLoading.value = true; api.inventoryAge().then((x) => { age.value = x }).catch((e) => message.error(e.message)).finally(() => { ageLoading.value = false }) }
const loadSummary = () => { summaryLoading.value = true; api.inOutSummary(period.value).then((x) => { summary.value = x }).catch((e) => message.error(e.message)).finally(() => { summaryLoading.value = false }) }

watch(tab, (t) => { if (t === 'age') loadAge(); else loadSummary() }, { immediate: true })

const filteredAge = computed(() => keyword.value ? age.value.filter((x) => x.itemCode.includes(keyword.value) || x.itemName.includes(keyword.value)) : age.value)

const ageCols = [
  { title: '物品', render: (_, r) => h('span', [h('b', r.itemName), h('br'), h(Typography.Text, { type: 'secondary' }, `${r.itemCode} · ${r.unit}`)]) },
  { title: '仓库 / 库位', render: (_, r) => h('span', [r.warehouseName, h('br'), h(Typography.Text, { type: 'secondary' }, r.locationCode || '-')]) },
  { title: '批次号', dataIndex: 'batchNo', render: (v) => v ? h(Tag, { color: 'blue' }, v) : '-' },
  { title: '数量', dataIndex: 'quantity', render: (v) => number(v) },
  { title: '金额', dataIndex: 'amount', render: (v) => money(v) },
  { title: '最早入库日期', dataIndex: 'earliestInDate', render: (v) => v || '-' },
  { title: '库龄（天）', dataIndex: 'ageDays', render: (v) => h('b', v) },
  { title: '库龄区间', dataIndex: 'bucket', render: (v) => h(Tag, { color: bucketColor[v] }, `${v} 天`) },
]

const summaryCols = [
  { title: '物品', dataIndex: 'itemName', render: (v, r) => h('span', [h('b', v), h('br'), h(Typography.Text, { type: 'secondary' }, `${r.itemCode} · ${r.unit}`)]) },
  { title: '期初数量', dataIndex: 'openingQuantity', render: (v) => number(v) },
  { title: '入库数量', dataIndex: 'inQuantity', render: (v) => number(v) },
  { title: '入库金额', dataIndex: 'inAmount', render: (v) => money(v) },
  { title: '出库数量', dataIndex: 'outQuantity', render: (v) => number(v) },
  { title: '出库金额', dataIndex: 'outAmount', render: (v) => money(v) },
  { title: '期末数量', dataIndex: 'endingQuantity', render: (v) => number(v) },
  { title: '期末金额', dataIndex: 'endingAmount', render: (v) => money(v) },
]
</script>

<template>
  <div class="page-heading">
    <div>
      <Typography.Title :level="3" class="page-title">报表中心 · 拓展</Typography.Title>
      <Typography.Text type="secondary">库龄分析（识别呆滞料）与收发存汇总（期末对账）</Typography.Text>
    </div>
  </div>
  <Card class="table-card">
    <template #title>
      <Segmented v-model:value="tab" :options="[{ label: '库龄分析', value: 'age' }, { label: '收发存汇总', value: 'summary' }]" />
    </template>
    <template v-if="tab === 'age'">
      <a-table row-key="id" :loading="ageLoading" :data-source="filteredAge" :columns="ageCols">
        <template #title>
          <Space>
            <a-input-search v-model:value="keyword" placeholder="搜索编码/名称" style="width: 220px;" />
            <Button :icon="h(ReloadOutlined)" @click="loadAge">刷新</Button>
          </Space>
        </template>
      </a-table>
    </template>
    <template v-else>
      <Space style="margin-bottom: 16px;">
        <a-date-picker v-model:value="periodDayjs" picker="month" :allow-clear="false" />
        <Button :icon="h(ReloadOutlined)" @click="loadSummary">查询</Button>
      </Space>
      <a-table row-key="itemCode" :loading="summaryLoading" :data-source="summary" :columns="summaryCols" :pagination="{ pageSize: 20 }" />
    </template>
  </Card>
</template>