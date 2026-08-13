<script>
import { useUserStore } from '@/store/user.js'
import { useCartStore } from '@/store/cart.js'

export default {
  onLaunch() {
    console.log('App Launch · 商城小程序')
    const userStore = useUserStore()
    userStore.restore()
    // 已登录则预拉取购物车数量（用于 tabBar badge）
    if (userStore.isLoggedIn) {
      useCartStore().loadCount()
    }
  },
  onShow() {
    console.log('App Show')
  },
  onHide() {
    console.log('App Hide')
  },
}
</script>

<style>
/* 全局基础样式：与 wms-miniapp 视觉风格保持一致 */
page {
  background-color: #f5f6f7;
  color: #333;
  font-size: 28rpx;
}

.text-center { text-align: center; }
.text-right { text-align: right; }
.mt-10 { margin-top: 20rpx; }
.mt-20 { margin-top: 40rpx; }
.mb-10 { margin-bottom: 20rpx; }
.mb-20 { margin-bottom: 40rpx; }
.p-10 { padding: 20rpx; }
.p-20 { padding: 40rpx; }
.flex { display: flex; }
.flex-1 { flex: 1; }
.items-center { align-items: center; }
.justify-center { justify-content: center; }
.justify-between { justify-content: space-between; }
.flex-wrap { flex-wrap: wrap; }

.card {
  background: #fff;
  border-radius: 12rpx;
  padding: 24rpx;
  margin: 20rpx;
  box-shadow: 0 2rpx 6rpx rgba(0, 0, 0, 0.06);
}

.btn-primary {
  background: #1677ff;
  color: #fff;
  border: none;
  border-radius: 8rpx;
  padding: 20rpx 40rpx;
  font-size: 30rpx;
  line-height: 1.4;
}
.btn-primary[disabled] { opacity: 0.5; }

.btn-danger {
  background: #ff4d4f;
  color: #fff;
  border: none;
  border-radius: 8rpx;
  padding: 20rpx 40rpx;
  font-size: 30rpx;
}

.btn-outline {
  background: #fff;
  color: #1677ff;
  border: 1rpx solid #1677ff;
  border-radius: 8rpx;
  padding: 20rpx 40rpx;
  font-size: 30rpx;
}

.input {
  width: 100%;
  padding: 20rpx;
  border: 1rpx solid #d9d9d9;
  border-radius: 8rpx;
  font-size: 28rpx;
  box-sizing: border-box;
}

.label { font-size: 24rpx; color: #999; margin-bottom: 12rpx; display: block; }
.value { font-size: 28rpx; color: #333; }
.value-bold { font-weight: 600; }
.value-red { color: #ff4d4f; }
.value-green { color: #52c41a; }
.value-orange { color: #fa8c16; }
.text-muted { color: #999; font-size: 24rpx; }

.row { display: flex; margin-bottom: 24rpx; }
.row > * { flex: 1; }
.row > * + * { margin-left: 16rpx; }

.divider { height: 1rpx; background: #f0f0f0; margin: 24rpx 0; }

.section-title { font-size: 30rpx; font-weight: 600; color: #333; margin: 32rpx 0 16rpx; padding: 0 20rpx; }

.empty-state {
  padding: 80rpx 40rpx;
  text-align: center;
  color: #999;
}
.empty-state-text { font-size: 28rpx; margin-top: 16rpx; }

/* 状态 pill */
.status-pill {
  display: inline-block;
  padding: 4rpx 16rpx;
  border-radius: 16rpx;
  font-size: 22rpx;
}
.status-pill-info { background: #e6f7ff; color: #1677ff; }
.status-pill-success { background: #f6ffed; color: #52c41a; }
.status-pill-warning { background: #fffbe6; color: #faad14; }
.status-pill-error { background: #fff1f0; color: #ff4d4f; }
.status-pill-default { background: #f0f0f0; color: #666; }

.price { color: #ff4d4f; font-weight: 600; }
.price-symbol { font-size: 22rpx; }
.price-integer { font-size: 36rpx; }
.price-decimal { font-size: 22rpx; }
</style>
