<template>
  <view class="about">
    <image class="logo" :src="logo" mode="aspectFit" />
    <text class="name">WMS 机械商城小程序</text>
    <text class="ver">版本 1.0.0</text>
    <text class="desc">基于 Spring Boot 3.3 + Vue 3 + uni-app 构建</text>
    <text class="desc">后端：wms-server · 管理端：wms-web</text>
  </view>
</template>
<script>
export default { data() { return { logo: '/static/logo.png' } } }
</script>
<style scoped>
.about { min-height: 100vh; display: flex; flex-direction: column; align-items: center; padding: 160rpx 40rpx; }
.logo { width: 160rpx; height: 160rpx; border-radius: 32rpx; }
.name { font-size: 36rpx; font-weight: 700; margin-top: 30rpx; }
.ver { font-size: 26rpx; color: #999; margin-top: 12rpx; }
.desc { font-size: 24rpx; color: #999; margin-top: 8rpx; }
</style>
