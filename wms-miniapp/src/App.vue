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

.mt-10 { margin-top: 20rpx; }
.mt-20 { margin-top: 40rpx; }
.mb-10 { margin-bottom: 20rpx; }
.mb-20 { margin-bottom: 40rpx; }

.flex { display: flex; }
.flex-1 { flex: 1; }
.items-center { align-items: center; }
.justify-center { justify-content: center; }
.justify-between { justify-content: space-between; }

.card {
  background: #fff;
  border-radius: 16rpx;
  padding: 32rpx;
  margin: 20rpx;
  box-shadow: 0 2rpx 6rpx rgba(0,0,0,0.08);
}

.btn-primary {
  background: #1677ff;
  color: #fff;
  border: none;
  border-radius: 12rpx;
  padding: 24rpx 48rpx;
  font-size: 32rpx;
}
.btn-primary:disabled { opacity: 0.6; }

.btn-secondary {
  background: #fff;
  color: #1677ff;
  border: 2rpx solid #1677ff;
  border-radius: 12rpx;
  padding: 24rpx 48rpx;
  font-size: 32rpx;
}

.btn-danger {
  background: #ff4d4f;
  color: #fff;
  border: none;
  border-radius: 12rpx;
  padding: 24rpx 48rpx;
  font-size: 32rpx;
}

.input {
  width: 100%;
  padding: 24rpx;
  border: 2rpx solid #d9d9d9;
  border-radius: 12rpx;
  font-size: 32rpx;
  box-sizing: border-box;
}

.label { font-size: 28rpx; color: #666; margin-bottom: 12rpx; display: block; }
.value { font-size: 32rpx; color: #333; }
.value-bold { font-weight: 600; }
.value-red { color: #ff4d4f; }
.value-green { color: #52c41a; }

.badge {
  display: inline-block;
  padding: 4rpx 16rpx;
  border-radius: 24rpx;
  font-size: 24rpx;
}
.badge-success { background: #f6ffed; color: #52c41a; border: 2rpx solid #b7eb8f; }
.badge-warning { background: #fffbe6; color: #faad14; border: 2rpx solid #ffe58f; }
.badge-error { background: #fff1f0; color: #ff4d4f; border: 2rpx solid #ffa39e; }
.badge-info { background: #e6f7ff; color: #1677ff; border: 2rpx solid #91d5ff; }
.badge-default { background: #f0f0f0; color: #666; border: 2rpx solid #d9d9d9; }

.divider { height: 2rpx; background: #f0f0f0; margin: 24rpx 0; }

.section-title { font-size: 30rpx; font-weight: 600; color: #333; margin: 32rpx 0 16rpx; }

.empty-state {
  padding: 80rpx 40rpx;
  text-align: center;
  color: #999;
}
.empty-state image { width: 160rpx; height: 160rpx; margin-bottom: 24rpx; opacity: 0.6; }
</style>
