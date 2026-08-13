<script setup>
import { h, onMounted, ref } from 'vue'
import { Button, Card, Input, Modal, Space, Table, Tag, Typography, message } from 'ant-design-vue'
import { DeleteOutlined, EditOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { api } from '../api/wms'

const data = ref([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const keyword = ref('')
const modalOpen = ref(false)
const editing = ref(null)
const formState = ref({ name: '', phone: '', address: '', defaultFlag: false, remark: '' })

const load = async () => {
  loading.value = true
  try {
    const res = await api.marketCustomers({ page: page.value, pageSize: 10, keyword: keyword.value || undefined })
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

const open = (record) => {
  editing.value = record || null
  formState.value = { name: '', phone: '', address: '', defaultFlag: false, remark: '', ...(record || {}) }
  modalOpen.value = true
}

const save = async () => {
  const values = { ...formState.value }
  try {
    if (editing.value) await api.updateMarketCustomer(editing.value.id, values)
    else await api.createMarketCustomer(values)
    message.success(editing.value ? '客户已更新' : '客户已新增')
    modalOpen.value = false
    load()
  } catch (e) {
    message.error(e.message)
  }
}

const columns = [
  { title: '姓名', dataIndex: 'name', width: 100 },
  { title: '电话', dataIndex: 'phone', width: 130 },
  { title: '地址', dataIndex: 'address', width: 200 },
  {
    title: '默认', dataIndex: 'defaultFlag', width: 80,
    render: (v) => v ? h(Tag, { color: 'green' }, '是') : '-',
  },
  {
    title: '操作', width: 140,
    render: (_, r) => h(Space, [
      h(Button, { type: 'link', icon: h(EditOutlined), onClick: () => open(r) }, '编辑'),
      h(Button, { type: 'link', danger: true, icon: h(DeleteOutlined), onClick: () => {
        if (confirm('确认删除该客户？')) {
          api.deleteMarketCustomer(r.id).then(() => { message.success('已删除'); load() }).catch((e) => message.error(e.message))
        }
      } }, '删除'),
    ]),
  },
]
</script>

<template>
  <div class="page-heading">
    <div>
      <Typography.Title :level="3" class="page-title">商城客户</Typography.Title>
      <Typography.Text class="page-subtitle" type="secondary">管理小程序商城客户收货信息</Typography.Text>
    </div>
    <Button type="primary" :icon="h(PlusOutlined)" @click="open()">新增客户</Button>
  </div>

  <Card class="table-card">
    <Space style="margin-bottom: 16px;">
      <a-input allow-clear placeholder="搜索姓名或电话" :prefix="h(SearchOutlined)" v-model:value="keyword" style="width: 250px;" @press-enter="load" />
      <Button @click="load">查询</Button>
    </Space>
    <a-table row-key="id" :loading="loading" :data-source="data" :columns="columns"
      :pagination="{ current: page, total, pageSize: 10, onChange: (p) => { page = p; load() }, showTotal: (t) => `共 ${t} 条` }" />
  </Card>

  <a-modal v-model:open="modalOpen" :title="editing ? '编辑客户' : '新增客户'" :destroy-on-close="true" width="520" ok-text="保存" @ok="save">
    <a-form layout="vertical" :model="formState">
      <a-form-item label="姓名" required><a-input v-model:value="formState.name" /></a-form-item>
      <a-form-item label="电话" required><a-input v-model:value="formState.phone" /></a-form-item>
      <a-form-item label="地址"><a-input v-model:value="formState.address" /></a-form-item>
      <a-form-item label="是否默认"><a-switch v-model:checked="formState.defaultFlag" /></a-form-item>
      <a-form-item label="备注"><a-input v-model:value="formState.remark" /></a-form-item>
    </a-form>
  </a-modal>
</template>

<style scoped>
.page-heading { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 16px; }
.page-title { margin: 0 !important; margin-bottom: 4px !important; }
.page-subtitle { font-size: 13px; }
.table-card { border-radius: 12px; }
</style>
