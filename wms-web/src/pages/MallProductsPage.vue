<script setup>
import { h, onMounted, ref } from 'vue'
import { Button, Card, Input, InputNumber, Modal, Popconfirm, Select, Space, Switch, Table, Tag, Typography, message } from 'ant-design-vue'
import { DeleteOutlined, EditOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { api } from '../api/wms'
import { money, number } from '../utils/format'

const emptyForm = { itemId: undefined, title: '', subTitle: '', mainImage: '', gallery: '', salePrice: 0, marketPrice: 0, categoryId: undefined, sortNo: 0 }

const data = ref([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const keyword = ref('')
const statusFilter = ref()
const modalOpen = ref(false)
const editing = ref(null)
const formState = ref({ ...emptyForm })
const items = ref([])
const itemMap = ref({})

const load = async () => {
  loading.value = true
  try {
    const res = await api.marketProducts({ page: page.value, pageSize: 10, keyword: keyword.value || undefined, status: statusFilter.value })
    data.value = res.records
    total.value = res.total
  } catch (e) {
    message.error(e.message)
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  load()
  try {
    const itemPage = await api.items({ page: 1, pageSize: 1000 })
    items.value = itemPage.records
    itemMap.value = Object.fromEntries(itemPage.records.map((x) => [x.id, x]))
  } catch (e) {
    message.error(e.message)
  }
})

const open = (record) => {
  editing.value = record || null
  formState.value = { ...emptyForm, ...(record || {}), gallery: Array.isArray(record?.gallery) ? record.gallery.join(',') : (record?.gallery || '') }
  modalOpen.value = true
}

const save = async () => {
  const values = { ...formState.value, salePrice: Number(formState.value.salePrice), marketPrice: Number(formState.value.marketPrice), sortNo: Number(formState.value.sortNo || 0) }
  try {
    if (editing.value) await api.updateMarketProduct(editing.value.id, values)
    else await api.createMarketProduct(values)
    message.success(editing.value ? '商品已更新' : '商品已新增')
    modalOpen.value = false
    load()
  } catch (e) {
    message.error(e.message)
  }
}

const toggleShelf = async (r) => {
  const target = r.status === 'SHELF_ON' ? 'SHELF_OFF' : 'SHELF_ON'
  try {
    await api.shelfMarketProduct(r.id, target)
    message.success(target === 'SHELF_ON' ? '已上架' : '已下架')
    load()
  } catch (e) {
    message.error(e.message)
  }
}

const columns = [
  { title: '标题', dataIndex: 'title', width: 220, render: (v, r) => h('span', [h('b', v), h('br'), h(Typography.Text, { type: 'secondary' }, r.subTitle || '')]) },
  { title: '关联物品', dataIndex: 'itemCode', width: 150, render: (v, r) => `${r.itemCode} ${r.itemName || ''}` },
  { title: '售价', dataIndex: 'salePrice', width: 110, render: (v) => money(v) },
  { title: '划线价', dataIndex: 'marketPrice', width: 110, render: (v) => (v ? money(v) : '-') },
  { title: '销量', dataIndex: 'salesCount', width: 80, render: (v) => number(v) },
  {
    title: '状态', dataIndex: 'status', width: 110,
    render: (v) => h(Tag, { color: v === 'SHELF_ON' ? 'green' : 'default' }, v === 'SHELF_ON' ? '上架' : '下架'),
  },
  {
    title: '操作', width: 220,
    render: (_, r) => h(Space, [
      h(Button, { type: 'link', icon: h(EditOutlined), onClick: () => open(r) }, '编辑'),
      h(Button, { type: 'link', onClick: () => toggleShelf(r) }, r.status === 'SHELF_ON' ? '下架' : '上架'),
      h(Popconfirm, {
        title: '确认删除该商品？',
        onConfirm: () => api.deleteMarketProduct(r.id).then(() => { message.success('已删除'); load() }).catch((e) => message.error(e.message)),
      }, { default: () => h(Button, { type: 'link', danger: true, icon: h(DeleteOutlined) }, '删除') }),
    ]),
  },
]
</script>

<template>
  <div class="page-heading">
    <div>
      <Typography.Title :level="3" class="page-title">商城商品</Typography.Title>
      <Typography.Text class="page-subtitle" type="secondary">管理小程序商城商品、售价与上下架</Typography.Text>
    </div>
    <Button type="primary" :icon="h(PlusOutlined)" @click="open()">新增商品</Button>
  </div>

  <Card class="table-card">
    <Space style="margin-bottom: 16px;">
      <a-input allow-clear placeholder="搜索标题或物品编码" :prefix="h(SearchOutlined)" v-model:value="keyword" style="width: 250px;" @press-enter="load" />
      <a-select v-model:value="statusFilter" allow-clear placeholder="状态" style="width: 120px;" :options="[{ value: 'SHELF_ON', label: '上架' }, { value: 'SHELF_OFF', label: '下架' }]" @change="load" />
      <Button @click="load">查询</Button>
    </Space>
    <a-table row-key="id" :loading="loading" :data-source="data" :columns="columns"
      :pagination="{ current: page, total, pageSize: 10, onChange: (p) => { page = p; load() }, showTotal: (t) => `共 ${t} 条` }" />
  </Card>

  <a-modal v-model:open="modalOpen" :title="editing ? '编辑商品' : '新增商品'" :destroy-on-close="true" width="680" ok-text="保存" @ok="save">
    <a-form layout="vertical" :model="formState">
      <a-form-item label="关联物品（选中后显示编码/名称）" required>
        <a-select v-model:value="formState.itemId" :options="items.map((x) => ({ value: x.id, label: `${x.code} ${x.name}` }))" :filter-option="(input, opt) => String(opt.label).toLowerCase().includes(input.toLowerCase())" show-search placeholder="搜索选择物品" />
      </a-form-item>
      <div class="grid-2">
        <a-form-item label="标题" required><a-input v-model:value="formState.title" placeholder="商品标题" /></a-form-item>
        <a-form-item label="副标题"><a-input v-model:value="formState.subTitle" /></a-form-item>
      </div>
      <div class="grid-2">
        <a-form-item label="售价（元）" required><a-input-number v-model:value="formState.salePrice" :min="0" :precision="2" style="width: 100%;" /></a-form-item>
        <a-form-item label="划线价（元）"><a-input-number v-model:value="formState.marketPrice" :min="0" :precision="2" style="width: 100%;" /></a-form-item>
      </div>
      <a-form-item label="主图 URL"><a-input v-model:value="formState.mainImage" placeholder="https://..." /></a-form-item>
      <a-form-item label="图集 URL（逗号分隔）"><a-input v-model:value="formState.gallery" placeholder="https://...,https://..." /></a-form-item>
      <div class="grid-2">
        <a-form-item label="排序（越小越靠前）"><a-input-number v-model:value="formState.sortNo" style="width: 100%;" /></a-form-item>
        <a-form-item label="分类（沿用物品分类）">
          <a-input :value="editing ? (itemMap[formState.itemId]?.category?.name || '') : ''" disabled />
        </a-form-item>
      </div>
    </a-form>
  </a-modal>
</template>

<style scoped>
.page-heading { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 16px; }
.page-title { margin: 0 !important; margin-bottom: 4px !important; }
.page-subtitle { font-size: 13px; }
.table-card { border-radius: 12px; }
.grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 0 16px; }
</style>
