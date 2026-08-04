<script setup>
import { h, onMounted, ref } from 'vue'
import { Button, Card, Input, InputNumber, Modal, Popconfirm, Select, Space, Switch, Table, Tag, Typography, message } from 'ant-design-vue'
import { DeleteOutlined, EditOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { api } from '../api/wms'
import { money, number } from '../utils/format'

const empty = { unit: '个', safetyStock: 0, maxStock: 0, minStock: 0, status: true }

const data = ref([])
const total = ref(0)
const loading = ref(false)
const keyword = ref('')
const modalOpen = ref(false)
const editing = ref(null)
const categories = ref([])
const warehouses = ref([])
const inventoryMap = ref({})
const formState = ref({ ...empty })

const load = async () => {
  loading.value = true
  try {
    const [items, inv] = await Promise.all([
      api.items({ page: 1, pageSize: 100, keyword: keyword.value || undefined }),
      api.inventory(),
    ])
    data.value = items.records
    total.value = items.total
    const map = {}
    inv.forEach((x) => {
      const id = x.itemId
      if (!map[id]) map[id] = []
      map[id].push(x)
    })
    inventoryMap.value = map
  } catch (e) {
    message.error(e.message)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  load()
  api.categories().then((x) => { categories.value = x }).catch((e) => message.error(e.message))
  api.warehouses().then((x) => { warehouses.value = x }).catch((e) => message.error(e.message))
})

const open = (record) => {
  editing.value = record || null
  formState.value = { ...empty, ...(record || {}) }
  modalOpen.value = true
}

const save = async () => {
  const values = { ...formState.value }
  try {
    if (editing.value) await api.updateItem(editing.value.id, values)
    else await api.createItem(values)
    message.success(editing.value ? '物品已更新' : '物品已新增')
    modalOpen.value = false
    load()
  } catch (e) {
    message.error(e.message)
  }
}

const columns = [
  { title: '物品编码', dataIndex: 'code', width: 145 },
  {
    title: '物品名称', dataIndex: 'name',
    render: (v, r) => h('span', [h('b', v), h('br'), h(Typography.Text, { type: 'secondary' }, r.specs || '未设置规格')]),
  },
  { title: '单位', dataIndex: 'unit', width: 60 },
  { title: '安全库存', dataIndex: 'safetyStock', width: 100, render: (v) => number(v) },
  { title: '状态', dataIndex: 'status', width: 75, render: (v) => h(Tag, { color: v ? 'green' : 'default' }, v ? '启用' : '停用') },
  {
    title: '存储位置', width: 300,
    render: (_, r) => {
      const invs = inventoryMap.value[r.id]
      if (!invs || invs.length === 0) return h(Typography.Text, { type: 'secondary' }, '暂无库存')
      return h(Space, { size: [4, 4], wrap: true }, invs.map((inv) => h(Tag, { color: 'blue', style: { margin: 0 } }, [
        `${inv.warehouseName}${inv.locationCode ? ` / ${inv.locationCode}` : ''}`,
        h('span', { style: { fontWeight: 600, marginLeft: 4 } }, number(inv.quantity)),
      ])))
    },
  },
  {
    title: '操作', width: 130,
    render: (_, r) => h(Space, [
      h(Button, { type: 'link', icon: h(EditOutlined), onClick: () => open(r) }, '编辑'),
      h(Popconfirm, {
        title: '确认删除该物品？',
        onConfirm: () => api.deleteItem(r.id).then(() => { message.success('已删除'); load() }).catch((e) => message.error(e.message)),
      }, { default: () => h(Button, { type: 'link', danger: true, icon: h(DeleteOutlined) }, '删除') }),
    ]),
  },
]
</script>

<template>
  <div class="page-heading">
    <div>
      <Typography.Title :level="3" class="page-title">物品档案</Typography.Title>
      <Typography.Text class="page-subtitle" type="secondary">维护物品编码、规格与安全库存</Typography.Text>
    </div>
    <Button type="primary" :icon="h(PlusOutlined)" @click="open()">新增物品</Button>
  </div>

  <Card class="table-card">
    <Space style="margin-bottom: 16px;">
      <a-input allow-clear placeholder="搜索物品编码或名称" :prefix="h(SearchOutlined)" v-model:value="keyword" style="width: 250px;" @press-enter="load" />
      <Button @click="load">查询</Button>
    </Space>
    <a-table row-key="id" :loading="loading" :data-source="data" :columns="columns" :pagination="{ total, pageSize: 100, showTotal: (t) => `共 ${t} 条` }" />
  </Card>

  <a-modal v-model:open="modalOpen" :title="editing ? '编辑物品' : '新增物品'" :destroy-on-close="true" width="660" ok-text="保存" @ok="save">
    <a-form layout="vertical" :model="formState">
      <a-space-compact style="width: 100%;">
        <a-form-item name="code" label="物品编码" :rules="[{ required: true, message: '请输入物品编码' }]" style="width: 50%;">
          <a-input v-model:value="formState.code" placeholder="如 ITEM-20260716-0001" />
        </a-form-item>
        <a-form-item name="name" label="物品名称" :rules="[{ required: true, message: '请输入物品名称' }]" style="width: 50%;">
          <a-input v-model:value="formState.name" />
        </a-form-item>
      </a-space-compact>
      <a-space-compact style="width: 100%;">
        <a-form-item name="categoryId" label="分类" style="width: 33.3%;">
          <a-select v-model:value="formState.categoryId" allow-clear :options="categories.map((x) => ({ value: x.id, label: x.name }))" />
        </a-form-item>
        <a-form-item name="unit" label="单位" style="width: 33.3%;">
          <a-input v-model:value="formState.unit" />
        </a-form-item>
        <a-form-item name="specs" label="规格型号" style="width: 33.3%;">
          <a-input v-model:value="formState.specs" />
        </a-form-item>
      </a-space-compact>
      <a-space-compact style="width: 100%;">
        <a-form-item name="safetyStock" label="安全库存" style="width: 33.3%;">
          <a-input-number v-model:value="formState.safetyStock" :min="0" style="width: 100%;" />
        </a-form-item>
        <a-form-item name="minStock" label="最小库存" style="width: 33.3%;">
          <a-input-number v-model:value="formState.minStock" :min="0" style="width: 100%;" />
        </a-form-item>
        <a-form-item name="maxStock" label="最大库存" style="width: 33.3%;">
          <a-input-number v-model:value="formState.maxStock" :min="0" style="width: 100%;" />
        </a-form-item>
      </a-space-compact>
      <a-form-item name="defaultWarehouseId" label="默认仓库">
        <a-select v-model:value="formState.defaultWarehouseId" allow-clear placeholder="选择默认存放仓库" :options="warehouses.map((x) => ({ value: x.id, label: x.name }))" />
      </a-form-item>
      <a-form-item name="remark" label="备注">
        <a-textarea v-model:value="formState.remark" :rows="2" />
      </a-form-item>
      <a-form-item name="status" label="状态">
        <a-switch v-model:checked="formState.status" checked-children="启用" un-checked-children="停用" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>
