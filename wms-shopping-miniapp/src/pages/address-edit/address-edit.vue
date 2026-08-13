<template>
  <view class="page">
    <view class="card">
      <view class="form-item">
        <text class="label">收货人</text>
        <input class="input" v-model="form.name" placeholder="请输入姓名" />
      </view>
      <view class="form-item">
        <text class="label">手机号</text>
        <input class="input" v-model="form.phone" type="number" maxlength="11" placeholder="请输入手机号" />
      </view>
      <view class="form-item">
        <text class="label">详细地址</text>
        <textarea class="input textarea" v-model="form.address" placeholder="省市区 + 详细地址" />
      </view>
      <view class="form-item row" @tap="form.defaultFlag=!form.defaultFlag">
        <text class="label" style="flex:1">设为默认地址</text>
        <view :class="['switch', form.defaultFlag&&'on']"><view class="dot"></view></view>
      </view>
    </view>
    <button class="btn-primary save-btn" @tap="save">保存</button>
  </view>
</template>

<script>
import { customers } from '@/api/market.js'

export default {
  data() { return { id: null, form: { name: '', phone: '', address: '', defaultFlag: false } } },
  onLoad(opt) {
    if (opt.id) {
      this.id = opt.id
      this.load(opt.id)
    }
  },
  methods: {
    async load(id) {
      const list = await customers.list()
      const cur = list.find(a => a.id == id)
      if (cur) this.form = { name: cur.name, phone: cur.phone, address: cur.address, defaultFlag: cur.defaultFlag }
    },
    async save() {
      if (!this.form.name || !this.form.phone || !this.form.address) { uni.showToast({ title: '请填写完整', icon: 'none' }); return }
      try {
        if (this.id) await customers.update(this.id, this.form)
        else await customers.save(this.form)
        uni.showToast({ title: '保存成功', icon: 'success' })
        setTimeout(() => uni.navigateBack(), 400)
      } catch (e) { uni.showToast({ title: (e && e.message) || '保存失败', icon: 'none' }) }
    },
  },
}
</script>

<style scoped>
.page { padding: 20rpx; }
.form-item { margin-bottom: 32rpx; }
.label { font-size: 26rpx; color: #666; margin-bottom: 12rpx; display: block; }
.textarea { height: 160rpx; }
.switch { width: 96rpx; height: 52rpx; border-radius: 26rpx; background: #d9d9d9; position: relative; transition: .2s; }
.switch.on { background: #1677ff; }
.dot { width: 44rpx; height: 44rpx; border-radius: 50%; background: #fff; position: absolute; top: 4rpx; left: 4rpx; transition: .2s; }
.switch.on .dot { left: 48rpx; }
.save-btn { margin-top: 40rpx; border-radius: 40rpx; }
</style>
