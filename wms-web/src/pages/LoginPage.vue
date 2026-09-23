<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { Button, Card, Form, Input, Typography, message } from 'ant-design-vue'
import { LockOutlined, UserOutlined } from '@ant-design/icons-vue'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const showDemoHint = import.meta.env.DEV
const formState = ref({ username: '', password: '' })

const submit = async () => {
  if (loading.value) return
  loading.value = true
  try {
    await auth.login(formState.value)
    router.push('/dashboard')
  } catch (e) {
    message.error(e.message)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-shell">
    <div class="login-frame">
      <section class="login-aside" aria-label="系统信息">
        <div>
          <div class="login-brand"><span class="brand-mark" aria-hidden="true"><span /></span>WMS 管理系统</div>
          <div style="margin-top: 92px;">
            <h2>让每一次库存流转，都有迹可循。</h2>
            <p>面向采购、仓储与经营团队的统一运营工作台，实时掌握库存资产与业务风险。</p>
          </div>
        </div>
        <div class="login-aside-meta">
          <span><i aria-hidden="true" />库存与单据统一管理</span>
          <span><i aria-hidden="true" />角色权限与操作留痕</span>
          <span><i aria-hidden="true" />库存与经营数据实时协同</span>
        </div>
      </section>
      <Card class="login-card">
        <Typography.Title :level="2" class="login-title">登录运营工作台</Typography.Title>
        <Typography.Paragraph class="login-intro">请输入账号信息以继续处理仓储业务。</Typography.Paragraph>
        <a-form layout="vertical" :model="formState" @finish="submit">
          <a-form-item name="username" label="用户名" :rules="[{ required: true }]">
            <a-input v-model:value="formState.username" allow-clear autocomplete="username">
              <template #prefix><UserOutlined aria-hidden="true" /></template>
            </a-input>
          </a-form-item>
          <a-form-item name="password" label="密码" :rules="[{ required: true }]">
            <a-input-password v-model:value="formState.password" autocomplete="current-password">
              <template #prefix><LockOutlined aria-hidden="true" /></template>
            </a-input-password>
          </a-form-item>
          <Button html-type="submit" type="primary" size="large" block :loading="loading">登录系统</Button>
        </a-form>
        <Typography.Paragraph v-if="showDemoHint" class="login-hint" type="secondary">
          演示账号：管理员 admin / admin123<br />仓库操作员 operator / operator123
        </Typography.Paragraph>
      </Card>
    </div>
  </div>
</template>
