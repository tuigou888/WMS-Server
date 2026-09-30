<template>
  <a-result status="403" title="403" sub-title="抱歉，您没有访问该页面的权限。">
    <template #extra>
      <Button type="primary" @click="goHome">返回首页</Button>
    </template>
  </a-result>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { Button } from 'ant-design-vue'
import { useAuthStore } from '../stores/auth'
import { hasPerm } from '../utils/permission'

const router = useRouter()
const auth = useAuthStore()

function goHome() {
  const matched = router.getRoutes()
    .filter((r) => r.meta && r.meta.perm && hasPerm(auth.user, r.meta.perm))
    .map((r) => r.path)
    .sort((a, b) => a.length - b.length)
  router.push(matched.length ? matched[0] : '/login')
}
</script>
