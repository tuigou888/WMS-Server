<script setup>
import { computed, h, onMounted, ref } from 'vue'
import { Button, Card, Descriptions, Form, Input, Result, Select, Space, Typography, Upload, message } from 'ant-design-vue'
import { ArrowDownOutlined, ArrowUpOutlined, CameraOutlined } from '@ant-design/icons-vue'
import { api } from '../api/wms'
import { money, number } from '../utils/format'

const props = defineProps({ type: { type: String, default: 'in' } })
const inbound = computed(() => props.type !== 'out')
const warehouses = ref([])
const result = ref(null)
const ocrLoading = ref(false)
const formState = ref({ quantity: 1, unitCost: 0, salePrice: 0, locationCode: 'A-01-01' })

const ocrScan = async ({ file }) => {
  ocrLoading.value = true
  try {
    const r = await api.ocrRecognize(file)
    if (r.lines && r.lines.length > 0) {
      const line = r.lines[0]
      formState.value = { ...formState.value, itemCode: line.itemCode, quantity: line.quantity, batchNo: line.batchNo }
      message.success(`识别到 ${r.totalLines} 项，已自动填入第一项`)
    } else {
      message.warning('未识别到物品信息')
    }
  } catch (e) {
    message.error(e.message)
  } finally {
    ocrLoading.value = false
  }
  return false
}

const submit = async () => {
  try {
    const res = inbound ? await api.stockIn(formState.value) : await api.stockOut(formState.value)
    result.value = res
    message.success(inbound ? '入库成功' : '出库成功')
    formState.value = { quantity: 1, unitCost: 0, salePrice: 0, locationCode: 'A-01-01', batchNo: '', remark: '' }
    if (warehouses.value[0]) formState.value.warehouseId = warehouses.value[0].id
  } catch (e) {
    message.error(e.message)
  }
}

onMounted(() => {
  api.warehouses().then((x) => {
    warehouses.value = x
    if (x[0]) formState.value.warehouseId = x[0].id
  }).catch((e) => message.error(e.message))
})
</script>

<template>
  <div class="page-heading">
    <div>
      <Typography.Title :level="3" class="page-title">{{ inbound ? '扫码入库' : '扫码出库' }}</Typography.Title>
      <Typography.Text class="page-subtitle" type="secondary">{{ inbound ? '入库将自动更新移动加权平均成本' : '出库使用当前库存平均成本，自动计算销售利润' }}</Typography.Text>
    </div>
  </div>

  <Card class="form-card">
    <a-form layout="vertical" :model="formState" @finish="submit">
      <a-form-item name="itemCode" label="物品编码 / 扫码结果" :rules="[{ required: true, message: '请输入或扫描物品编码' }]">
        <a-input v-model:value="formState.itemCode" autofocus placeholder="如 ITEM-001" />
      </a-form-item>
      <a-space-compact style="width: 100%;">
        <a-form-item name="warehouseId" label="仓库" :rules="[{ required: true, message: '请选择仓库' }]" style="width: 50%;">
          <a-select v-model:value="formState.warehouseId" :options="warehouses.map(w => ({ value: w.id, label: w.name }))" />
        </a-form-item>
        <a-form-item name="locationCode" label="库位" :rules="[{ required: true, message: '请输入库位' }]" style="width: 50%;">
          <a-input v-model:value="formState.locationCode" placeholder="如 A-01-01" />
        </a-form-item>
      </a-space-compact>
      <a-space-compact style="width: 100%;">
        <a-form-item name="quantity" :label="inbound ? '入库数量' : '出库数量'" :rules="[{ required: true }]" style="width: 50%;">
          <a-input-number v-model:value="formState.quantity" :min="0.0001" :precision="4" style="width: 100%;" />
        </a-form-item>
        <a-form-item v-if="inbound" name="unitCost" label="入库单价" :rules="[{ required: true }]" style="width: 50%;">
          <a-input-number v-model:value="formState.unitCost" :min="0" :precision="4" prefix="¥" style="width: 100%;" />
        </a-form-item>
        <a-form-item v-else name="salePrice" label="售出价" :rules="[{ required: true }]" style="width: 50%;">
          <a-input-number v-model:value="formState.salePrice" :min="0" :precision="4" prefix="¥" style="width: 100%;" />
        </a-form-item>
      </a-space-compact>
      <a-form-item name="batchNo" label="批次号">
        <a-input v-model:value="formState.batchNo" placeholder="如 BATCH-20260718">
          <template v-if="inbound" #addonBefore>
            <a-upload accept="image/*" :show-upload-list="false" :custom-request="ocrScan">
              <Button type="link" :loading="ocrLoading" :icon="h(CameraOutlined)" style="padding: 0;">拍照识别</Button>
            </a-upload>
          </template>
        </a-input>
      </a-form-item>
      <a-form-item name="remark" label="备注">
        <a-textarea v-model:value="formState.remark" :rows="2" />
      </a-form-item>
      <Button html-type="submit" type="primary" size="large" :icon="inbound ? h(ArrowDownOutlined) : h(ArrowUpOutlined)">
        {{ inbound ? '确认入库' : '确认出库' }}
      </Button>
    </a-form>
  </Card>

  <Card v-if="result" style="margin-top: 18px;">
    <a-result
      status="success"
      :title="`${inbound ? '入库' : '出库'}单 ${result.orderNo} 已完成`"
      :sub-title="`${result.itemName} · ${number(result.quantity)} 件`"
    >
      <template #extra>
        <Descriptions bordered size="small" :column="3">
          <Descriptions.Item label="变动金额">{{ money(result.totalAmount) }}</Descriptions.Item>
          <Descriptions.Item label="当前平均成本">{{ money(result.newAvgCost) }}</Descriptions.Item>
          <Descriptions.Item label="当前库存">{{ number(result.newStockQuantity) }}</Descriptions.Item>
          <template v-if="!inbound">
            <Descriptions.Item label="销售金额">{{ money(result.saleAmount) }}</Descriptions.Item>
            <Descriptions.Item label="本次利润">{{ money(result.profit) }}</Descriptions.Item>
            <Descriptions.Item label="成本单价">{{ money(result.costUnit) }}</Descriptions.Item>
          </template>
        </Descriptions>
      </template>
    </a-result>
  </Card>
</template>