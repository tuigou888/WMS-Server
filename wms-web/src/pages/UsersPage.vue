<script setup>
import { h, onMounted, ref } from 'vue'
import { Button, Card, Form, Input, Modal, Select, Switch, Table, Tag, Typography, message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { api } from '../api/wms'
import { permissionLabel, roleLabel } from '../utils/labels'

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

const roleNames = { ADMIN: '管理员', WAREHOUSE: '仓库操作员', PROCUREMENT: '采购专员', AUDITOR: '审计员', FINANCE: '财务人员', CUSTOMER_SERVICE: '客服人员', CUSTOMER: '商城客户' }
const roleOptions = Object.entries(roleNames).map(([value, label]) => ({ value, label }))

const columns = [
  { title: '用户名', dataIndex: 'username' },
  { title: '姓名', dataIndex: 'displayName' },
  { title: '角色', dataIndex: 'role', customRender: ({ text }) => h(Tag, { color: text === 'ADMIN' ? 'purple' : text === 'CUSTOMER' ? 'default' : 'blue' }, roleLabel(text)) },
  { title: '状态', dataIndex: 'enabled', customRender: ({ text }) => h(Tag, { color: text ? 'green' : 'default' }, text ? '启用' : '停用') },
  { title: '操作', customRender: ({ record }) => h(Button, { type: 'link', onClick: () => show(record) }, '编辑') },
]

const matrixCols = [{ title: '权限', dataIndex: 'code', customRender: ({ text }) => permissionLabel(text) }]
const matrixColumns = () => [...matrixCols, ...Object.keys(matrix.value?.roles || {}).sort().map((role) => ({ title: roleLabel(role), customRender: ({ record }) => h(Tag, { color: matrix.value?.roles?.[role]?.includes(record.code) ? 'green' : 'default' }, matrix.value?.roles?.[role]?.includes(record.code) ? '✓' : '✗') }))]
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
    <a-table row-key="code" size="small" :scroll="{ x: 'max-content' }" :pagination="false" :data-source="matrix.all.map((x) => ({ code: x }))" :columns="matrixColumns()" />
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
        <a-select v-model:value="formState.role" :options="roleOptions" />
      </a-form-item>
      <a-form-item name="enabled" label="状态">
        <a-switch v-model:checked="formState.enabled" checked-children="启用" un-checked-children="停用" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>
