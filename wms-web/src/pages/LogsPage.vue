<script setup>
import { h, ref, watch } from 'vue'
import { Button, Card, Input, Select, Space, Table, Tag, Typography, message } from 'ant-design-vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import { api } from '../api/wms'
import { dateTime } from '../utils/format'

const statusColor = { SUCCESS: 'green', ERROR: 'red' }

const data = ref([])
const loading = ref(false)
const username = ref()
const action = ref()
const result = ref()

const load = () => {
  loading.value = true
  api.operationLogs({ username: username.value, action: action.value, result: result.value }).then((x) => { data.value = x }).catch((e) => message.error(e.message)).finally(() => { loading.value = false })
}

watch([username, action, result], () => { load() }, { immediate: true })

const columns = [
  { title: '时间', dataIndex: 'operationAt', render: (v) => dateTime(v) },
  { title: '用户', dataIndex: 'username' },
  { title: '动作', dataIndex: 'action', render: (v) => h(Tag, null, v) },
  { title: '请求', render: (_, r) => h('span', [h('b', r.method), ' ', h(Typography.Text, { type: 'secondary' }, r.path)]) },
  { title: '结果', dataIndex: 'result', render: (v) => h(Tag, { color: statusColor[v] }, v === 'SUCCESS' ? '成功' : '失败') },
  { title: '消息', dataIndex: 'message', ellipsis: true },
]
</script>

<template>
  <div class="page-heading">
    <div>
      <Typography.Title :level="3" class="page-title">操作日志</Typography.Title>
      <Typography.Text type="secondary">审计追踪所有登录、单据操作、库存变动记录</Typography.Text>
    </div>
  </div>
  <Card class="table-card">
    <Space style="margin-bottom: 16px;">
      <a-input v-model:value="username" placeholder="用户" style="width: 150px;" />
      <a-input v-model:value="action" placeholder="动作" style="width: 150px;" />
      <a-select v-model:value="result" placeholder="结果" allow-clear style="width: 120px;" :options="[{ value: 'SUCCESS', label: '成功' }, { value: 'ERROR', label: '失败' }]" />
      <Button type="primary" :icon="h(ReloadOutlined)" @click="load">查询</Button>
    </Space>
    <a-table row-key="id" :loading="loading" :data-source="data" :columns="columns" :pagination="{ pageSize: 20 }" />
  </Card>
</template>