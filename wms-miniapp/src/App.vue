<script>
import { useUserStore } from '@/store/user.js'

export default {
  onLaunch() {
    const userStore = useUserStore()
    if (userStore.isLoggedIn) {
      // 登录页是 pages.json 首项（冷启动入口），已登录且 token 未过期则直达首页
      const expiresAt = Number(uni.getStorageSync('wms_token_expires_at') || 0)
      if (expiresAt && expiresAt <= Date.now()) {
        userStore.logout()
        return
      }
      uni.reLaunch({ url: '/pages/index/index' })
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
/* ===== 设计令牌 · 工业效率风 v1（全局引用，改动需同步各页面） ===== */
page {
  --wms-bg: #f3f5f8;          /* 页面底 · 冷工业灰 */
  --wms-card: #ffffff;        /* 卡片面 */
  --wms-border: #e5e8ec;      /* 分隔线/描边 */
  --wms-ink: #1f2329;         /* 主文字 */
  --wms-ink-2: #5c626b;       /* 次级文字 */
  --wms-ink-3: #9aa0a8;       /* 弱化文字 */
  --wms-primary: #1677ff;     /* 主蓝 */
  --wms-primary-deep: #0958d9;
  --wms-primary-bg: #e8f1fd;  /* 蓝浅底 */
  --wms-success: #52c41a;
  --wms-success-bg: #f0fae7;
  --wms-warning: #faad14;
  --wms-warning-bg: #fff8e6;
  --wms-danger: #ff4d4f;
  --wms-danger-bg: #fff1f0;
  --wms-accent: #fa8c16;      /* 安全橙 · 仅预警/强调 */
  background-color: var(--wms-bg);
  color: var(--wms-ink);
  font-size: 28rpx;
}

/* ===== 布局工具 ===== */
.text-center { text-align: center; }
.text-right { text-align: right; }
.mt-10 { margin-top: 20rpx; }
.mt-20 { margin-top: 40rpx; }
.mb-10 { margin-bottom: 20rpx; }
.mb-20 { margin-bottom: 40rpx; }
.flex { display: flex; }
.flex-1 { flex: 1; }
.items-center { align-items: center; }
.justify-center { justify-content: center; }
.justify-between { justify-content: space-between; }

/* ===== 卡片 ===== */
.card {
  background: var(--wms-card);
  border: 2rpx solid var(--wms-border);
  border-radius: 20rpx;
  padding: 32rpx;
  margin: 20rpx 24rpx;
  box-shadow: 0 2rpx 8rpx rgba(31, 35, 41, 0.04);
}

/* ===== 按钮 ===== */
.btn-primary {
  background: var(--wms-primary);
  color: #fff;
  border: none;
  border-radius: 12rpx;
  padding: 24rpx 48rpx;
  font-size: 32rpx;
  font-weight: 500;
  box-shadow: 0 4rpx 12rpx rgba(22, 119, 255, 0.25);
}
.btn-primary:active { opacity: 0.85; }
.btn-primary:disabled { opacity: 0.5; box-shadow: none; }

.btn-secondary {
  background: var(--wms-card);
  color: var(--wms-primary);
  border: 2rpx solid var(--wms-primary);
  border-radius: 12rpx;
  padding: 22rpx 48rpx;
  font-size: 32rpx;
  font-weight: 500;
}
.btn-secondary:active { background: var(--wms-primary-bg); }

.btn-danger {
  background: var(--wms-danger);
  color: #fff;
  border: none;
  border-radius: 12rpx;
  padding: 24rpx 48rpx;
  font-size: 32rpx;
  font-weight: 500;
}
.btn-danger:active { opacity: 0.85; }

/* ===== 表单 ===== */
.input {
  width: 100%;
  padding: 24rpx;
  border: 2rpx solid var(--wms-border);
  border-radius: 12rpx;
  font-size: 30rpx;
  color: var(--wms-ink);
  background: var(--wms-card);
  box-sizing: border-box;
}
.label { font-size: 26rpx; color: var(--wms-ink-2); margin-bottom: 12rpx; display: block; }
.value { font-size: 32rpx; color: var(--wms-ink); }
.value-bold { font-weight: 600; }
.value-red { color: var(--wms-danger); }
.value-green { color: var(--wms-success); }

/* ===== 状态徽标 ===== */
.badge {
  display: inline-block;
  padding: 4rpx 16rpx;
  border-radius: 8rpx;
  font-size: 24rpx;
  font-weight: 500;
}
.badge-success { background: var(--wms-success-bg); color: var(--wms-success); }
.badge-warning { background: var(--wms-warning-bg); color: var(--wms-warning); }
.badge-error { background: var(--wms-danger-bg); color: var(--wms-danger); }
.badge-info { background: var(--wms-primary-bg); color: var(--wms-primary); }
.badge-default { background: #f0f2f5; color: var(--wms-ink-3); }

.divider { height: 2rpx; background: var(--wms-border); margin: 24rpx 0; }

/* ===== 区块标题：蓝竖条 + 深色标题 ===== */
.section-title {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--wms-ink);
  border-left: 6rpx solid var(--wms-primary);
  padding-left: 16rpx;
  margin: 32rpx 0 16rpx;
}

.empty-state {
  padding: 80rpx 40rpx;
  text-align: center;
  color: var(--wms-ink-3);
}
.empty-state image { width: 160rpx; height: 160rpx; margin-bottom: 24rpx; opacity: 0.6; }
</style>
