<script setup>
import { h, onMounted, ref } from 'vue'
import { Button, Card, Form, Input, Modal, Popconfirm, Select, Switch, Table, Tag, Typography, message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { api } from '../api/wms'

const empty = { type: 'SUPPLIER', enabled: true }
const data = ref([])
const loading = ref(false)
const open = ref(false)
const editing = ref(null)
const formState = ref({ ...empty })

const load = () => {
  loading.value = true
  api.partners().then((x) => { data.value = x }).catch((e) => message.error(e.message)).finally(() => { loading.value = false })
}

onMounted(load)

const show = (p) => {
  editing.value = p || null
  formState.value = { ...empty, ...(p || {}) }
  open.value = true
}

const save = async () => {
  try {
    const v = { ...formState.value }
    if (editing.value) await api.updatePartner(editing.value.id, v)
    else await api.createPartner(v)
    message.success('保存成功')
    open.value = false
    load()
  } catch (e) {
    message.error(e.message)
  }
}

const columns = [
  { title: '编码', dataIndex: 'code' },
  { title: '名称', dataIndex: 'name' },
  { title: '类型', dataIndex: 'type', render: (v) => h(Tag, { color: v === 'SUPPLIER' ? 'blue' : v === 'CUSTOMER' ? 'green' : 'purple' }, v === 'SUPPLIER' ? '供应商' : v === 'CUSTOMER' ? '客户' : '供应商/客户') },
  { title: '联系人', dataIndex: 'contactName' },
  { title: '联系电话', dataIndex: 'phone' },
  { title: '地址', dataIndex: 'address', ellipsis: true },
  { title: '状态', dataIndex: 'enabled', render: (v) => h(Tag, { color: v ? 'green' : 'default' }, v ? '启用' : '停用') },
  {
    title: '操作',
    render: (_, r) => h('span', [
      h(Button, { type: 'link', onClick: () => show(r) }, '编辑'),
      h(Popconfirm, {
        title: '确认删除该单位？',
        onConfirm: () => api.deletePartner(r.id).then(() => { message.success('已删除'); load() }).catch((e) => message.error(e.message)),
      }, { default: () => h(Button, { type: 'link', danger: true }, '删除') }),
    ]),
  },
]
</script>

<template>
  <div class="page-heading">
    <div>
      <Typography.Title :level="3" class="page-title">供应商 / 客户</Typography.Title>
      <Typography.Text type="secondary">维护采购供应商、销售客户和联系人信息</Typography.Text>
    </div>
    <Button type="primary" :icon="h(PlusOutlined)" @click="show()">新增单位</Button>
  </div>

  <Card class="table-card">
    <a-table row-key="id" :loading="loading" :data-source="data" :columns="columns" />
  </Card>

  <a-modal v-model:open="open" :title="editing ? '编辑往来单位' : '新增往来单位'" :destroy-on-close="true" @ok="save">
    <a-form layout="vertical" :model="formState">
      <a-form-item name="code" label="单位编码" :rules="[{ required: true }]">
        <a-input v-model:value="formState.code" placeholder="如 SUP-001" />
      </a-form-item>
      <a-form-item name="name" label="单位名称" :rules="[{ required: true }]">
        <a-input v-model:value="formState.name" />
      </a-form-item>
      <a-form-item name="type" label="类型" :rules="[{ required: true }]">
        <a-select v-model:value="formState.type" :options="[{ value: 'SUPPLIER', label: '供应商' }, { value: 'CUSTOMER', label: '客户' }, { value: 'BOTH', label: '供应商/客户' }]" />
      </a-form-item>
      <a-form-item name="contactName" label="联系人">
        <a-input v-model:value="formState.contactName" />
      </a-form-item>
      <a-form-item name="phone" label="联系电话">
        <a-input v-model:value="formState.phone" />
      </a-form-item>
      <a-form-item name="email" label="邮箱">
        <a-input v-model:value="formState.email" />
      </a-form-item>
      <a-form-item name="address" label="地址">
        <a-input v-model:value="formState.address" />
      </a-form-item>
      <a-form-item name="remark" label="备注">
        <a-textarea v-model:value="formState.remark" :rows="2" />
      </a-form-item>
      <a-form-item name="enabled" label="状态">
        <a-switch v-model:checked="formState.enabled" checked-children="启用" un-checked-children="停用" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>