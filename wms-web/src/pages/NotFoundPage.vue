<template>
  <a-result status="404" title="404" sub-title="抱歉，您访问的页面不存在。">
    <template #extra>
      <Button type="primary" @click="router.push('/')" :disabled="!hasToken">返回首页</Button>
    </template>
  </a-result>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { Button } from 'ant-design-vue'
import { getStorage } from '../utils/storage'

const router = useRouter()
const hasToken = computed(() => !!getStorage('wms_token'))
</script>
