<template>
  <view class="empty-state">
    <view class="empty-icon">📦</view>
    <text class="empty-text">{{ title || '暂无内容' }}</text>
    <slot></slot>
  </view>
</template>
<script>
export default { props: { title: String } }
</script>
<style scoped>
.empty-state { padding: 80rpx 40rpx; text-align: center; }
.empty-icon { font-size: 80rpx; opacity: 0.4; margin-bottom: 16rpx; }
.empty-text { font-size: 28rpx; color: #999; }
</style>
