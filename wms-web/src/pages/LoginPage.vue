<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { Button, Card, Form, Input, Typography, message } from 'ant-design-vue'
import { LockOutlined, UserOutlined } from '@ant-design/icons-vue'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const formState = ref({ username: 'admin', password: '' })

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
    <Card class="login-card">
      <Typography.Title :level="2">WMS 管理系统</Typography.Title>
      <Typography.Paragraph type="secondary">仓库进销存管理平台</Typography.Paragraph>
      <a-form layout="vertical" :model="formState" @finish="submit">
        <a-form-item name="username" label="用户名" :rules="[{ required: true }]">
          <a-input v-model:value="formState.username" allow-clear>
            <template #prefix><UserOutlined /></template>
          </a-input>
        </a-form-item>
        <a-form-item name="password" label="密码" :rules="[{ required: true }]">
          <a-input-password v-model:value="formState.password">
            <template #prefix><LockOutlined /></template>
          </a-input-password>
        </a-form-item>
        <Button html-type="submit" type="primary" size="large" block :loading="loading">登录系统</Button>
      </a-form>
      <Typography.Paragraph class="login-hint" type="secondary">
        演示管理员：admin / admin123<br />仓库操作员：operator / operator123
      </Typography.Paragraph>
    </Card>
  </div>
</template>
