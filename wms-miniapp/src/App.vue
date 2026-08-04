<script>
import { useUserStore } from '@/store/user.js'

export default {
  onLaunch() {
    console.log('App Launch')
    const userStore = useUserStore()
    if (userStore.isLoggedIn) {
      // 启动时预加载仓库列表
      this.loadWarehouses()
    }
  },
  onShow() {
    console.log('App Show')
  },
  onHide() {
    console.log('App Hide')
  },
  methods: {
    async loadWarehouses() {
      try {
        const { api } = await import('@/api/request.js')
        const list = await api.warehouses(true)
        useUserStore().setWarehouses(list)
      } catch (e) {
        console.warn('加载仓库列表失败:', e)
      }
    },
  },
}
</script>

<style>
/* 每个页面公共 css */
page {
  background-color: #f5f5f5;
}

.text-center {
  text-align: center;
}

.text-right {
  text-align: right;
}

.mt-10 { margin-top: 10px; }
.mt-20 { margin-top: 20px; }
.mb-10 { margin-bottom: 10px; }
.mb-20 { margin-bottom: 20px; }

.flex { display: flex; }
.flex-1 { flex: 1; }
.items-center { align-items: center; }
.justify-center { justify-content: center; }
.justify-between { justify-content: space-between; }

.card {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  margin: 10px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.08);
}

.btn-primary {
  background: #1677ff;
  color: #fff;
  border: none;
  border-radius: 6px;
  padding: 12px 24px;
  font-size: 16px;
}
.btn-primary:disabled { opacity: 0.6; }

.btn-secondary {
  background: #fff;
  color: #1677ff;
  border: 1px solid #1677ff;
  border-radius: 6px;
  padding: 12px 24px;
  font-size: 16px;
}

.btn-danger {
  background: #ff4d4f;
  color: #fff;
  border: none;
  border-radius: 6px;
  padding: 12px 24px;
  font-size: 16px;
}

.input {
  width: 100%;
  padding: 12px;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  font-size: 16px;
  box-sizing: border-box;
}

.label { font-size: 14px; color: #666; margin-bottom: 6px; display: block; }
.value { font-size: 16px; color: #333; }
.value-bold { font-weight: 600; }
.value-red { color: #ff4d4f; }
.value-green { color: #52c41a; }

.row { display: flex; margin-bottom: 12px; }
.row > * { flex: 1; }
.row > *:first-child { margin-right: 8px; }

.badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 12px;
}
.badge-success { background: #f6ffed; color: #52c41a; border: 1px solid #b7eb8f; }
.badge-warning { background: #fffbe6; color: #faad14; border: 1px solid #ffe58f; }
.badge-error { background: #fff1f0; color: #ff4d4f; border: 1px solid #ffa39e; }
.badge-info { background: #e6f7ff; color: #1677ff; border: 1px solid #91d5ff; }
.badge-default { background: #f0f0f0; color: #666; border: 1px solid #d9d9d9; }

.divider { height: 1px; background: #f0f0f0; margin: 12px 0; }

.section-title { font-size: 15px; font-weight: 600; color: #333; margin: 16px 0 8px; }

.empty-state {
  padding: 40px 20px;
  text-align: center;
  color: #999;
}
.empty-state image { width: 80px; height: 80px; margin-bottom: 12px; opacity: 0.6; }
</style>