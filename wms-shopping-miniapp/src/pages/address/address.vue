<template>
  <view class="page">
    <view class="card" v-for="a in list" :key="a.id" @tap="select(a)" style="margin: 20rpx 24rpx">
      <view class="flex justify-between">
        <text class="addr-name">{{ a.name }} <text class="addr-phone">{{ a.phone }}</text></text>
        <text v-if="a.defaultFlag" class="badge">默认</text>
      </view>
      <text class="addr-detail">{{ a.address }}</text>
      <view class="flex justify-between mt-10">
        <text class="edit-link" @tap.stop="edit(a)">编辑</text>
        <text class="del-link" @tap.stop="del(a)">删除</text>
      </view>
    </view>
    <view v-if="!list.length" class="empty"><text>暂无收货地址</text></view>
    <view class="add-wrap"><button class="btn-primary" @tap="add">+ 新增地址</button></view>
  </view>
</template>

<script>
import { customers } from '@/api/market.js'

export default {
  data() { return { list: [], selectMode: false } },
  onLoad(opt) { this.selectMode = opt && opt.select === '1' },
  onShow() { this.load() },
  methods: {
    async load() { try { this.list = await customers.list() } catch (e) {} },
    add() { uni.navigateTo({ url: '/pages/address-edit/address-edit' }) },
    edit(a) { uni.navigateTo({ url: `/pages/address-edit/address-edit?id=${a.id}` }) },
    del(a) {
      uni.showModal({ title: '删除地址', content: '确认删除？', success: async (b) => { if (b) { try { await customers.remove(a.id); this.load() } catch (e) { uni.showToast({ title: (e && e.message) || '删除失败', icon: 'none' }) } } } })
    },
    select(a) {
      if (this.selectMode) {
        // 回传给结算页（通过 storage 传递）
        uni.setStorageSync('checkout_address', a)
        uni.navigateBack()
      }
    },
  },
}
</script>

<style scoped>
.addr-name { font-size: 32rpx; font-weight: 600; }
.addr-phone { font-weight: 400; color: #666; font-size: 26rpx; }
.addr-detail { font-size: 28rpx; color: #333; margin-top: 8rpx; display: block; }
.badge { color: #1677ff; font-size: 22rpx; border: 1rpx solid #1677ff; padding: 2rpx 12rpx; border-radius: 8rpx; }
.edit-link { color: #1677ff; font-size: 26rpx; }
.del-link { color: #ff4d4f; font-size: 26rpx; }
.add-wrap { position: fixed; bottom: 40rpx; left: 40rpx; right: 40rpx; }
.btn-primary { border-radius: 40rpx; }
.empty { padding: 100rpx; text-align: center; color: #999; }
</style>
