<script setup>
import { h, onMounted, ref } from 'vue'
import { Button, Card, Form, Input, Modal, Select, Switch, Table, Tag, Typography, message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { api } from '../api/wms'

const rows = ref([])
const matrix = ref(null)
const open = ref(false)
const editing = ref(null)
const formState = ref({ role: 'WAREHOUSE', enabled: true })

const load = () => api.users().then((x) => { rows.value = x }).catch((e) => message.error(e.message))

onMounted(() => {
  load()
  api.permissions().then((m) => { matrix.value = m }).catch((e) => message.error(e.message))
})

const show = (u) => {
  editing.value = u || null
  formState.value = { role: 'WAREHOUSE', enabled: true, ...(u || {}) }
  open.value = true
}

const save = async () => {
  try {
    const v = { ...formState.value }
    if (editing.value) await api.updateUser(editing.value.id, v)
    else await api.createUser(v)
    message.success('保存成功')
    open.value = false
    load()
  } catch (e) {
    if (!e.errorFields) message.error(e.message)
  }
}

const columns = [
  { title: '用户名', dataIndex: 'username' },
  { title: '姓名', dataIndex: 'displayName' },
  { title: '角色', dataIndex: 'role', render: (v) => h(Tag, { color: v === 'ADMIN' ? 'purple' : 'blue' }, v === 'ADMIN' ? '管理员' : '仓库操作员') },
  { title: '状态', dataIndex: 'enabled', render: (v) => h(Tag, { color: v ? 'green' : 'default' }, v ? '启用' : '停用') },
  { title: '操作', render: (_, r) => h(Button, { type: 'link', onClick: () => show(r) }, '编辑') },
]

const matrixCols = [
  { title: '权限', dataIndex: 'code' },
  { title: '管理员 ADMIN', render: () => h(Tag, { color: 'green' }, '✓') },
  { title: '仓库操作员 WAREHOUSE', render: (_, r) => h(Tag, { color: matrix.value?.roles?.WAREHOUSE?.includes(r.code) ? 'green' : 'default' }, matrix.value?.roles?.WAREHOUSE?.includes(r.code) ? '✓' : '✗') },
]
</script>

<template>
  <div class="page-heading">
    <div>
      <Typography.Title :level="3" class="page-title">用户与权限</Typography.Title>
      <Typography.Text type="secondary">管理员可维护用户；管理员负责审核，仓库操作员可执行已审核单据</Typography.Text>
    </div>
    <Button type="primary" :icon="h(PlusOutlined)" @click="show()">新建用户</Button>
  </div>

  <Card class="table-card">
    <a-table row-key="id" :data-source="rows" :columns="columns" />
  </Card>

  <Card v-if="matrix" title="权限矩阵" style="margin-top: 16px;">
    <template #extra><Typography.Text type="secondary">角色由系统固定映射到权限，管理员拥有全部权限</Typography.Text></template>
    <a-table row-key="code" size="small" :pagination="false" :data-source="matrix.all.map((x) => ({ code: x }))" :columns="matrixCols" />
  </Card>

  <a-modal v-model:open="open" :title="editing ? '编辑用户' : '新建用户'" :destroy-on-close="true" @ok="save">
    <a-form layout="vertical" :model="formState">
      <a-form-item name="username" label="用户名" :rules="[{ required: true }]">
        <a-input :disabled="!!editing" v-model:value="formState.username" />
      </a-form-item>
      <a-form-item name="displayName" label="姓名" :rules="[{ required: true }]">
        <a-input v-model:value="formState.displayName" />
      </a-form-item>
      <a-form-item name="password" :label="editing ? '重置密码（留空不修改）' : '密码'" :rules="editing ? [] : [{ required: true, min: 6 }]">
        <a-input-password v-model:value="formState.password" />
      </a-form-item>
      <a-form-item name="role" label="角色" :rules="[{ required: true }]">
        <a-select v-model:value="formState.role" :options="[{ value: 'ADMIN', label: '管理员' }, { value: 'WAREHOUSE', label: '仓库操作员' }]" />
      </a-form-item>
      <a-form-item name="enabled" label="状态">
        <a-switch v-model:checked="formState.enabled" checked-children="启用" un-checked-children="停用" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>