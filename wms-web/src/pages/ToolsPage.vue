<script setup>
import { h, onMounted, ref } from 'vue'
import { Button, Card, Col, Form, Image, Row, Select, Typography, Upload, message } from 'ant-design-vue'
import { DownloadOutlined, QrcodeOutlined, UploadOutlined } from '@ant-design/icons-vue'
import { api } from '../api/wms'

const items = ref([])
const qr = ref(null)
const qrLoading = ref(false)
const exportLoading = ref(false)
const formState = ref({})

onMounted(() => {
  api.items({ pageSize: 1000 }).then((x) => { items.value = x.records }).catch((e) => message.error(e.message))
})

const generateQr = async () => {
  if (qrLoading.value) return
  qrLoading.value = true
  try {
    const data = await api.qrcode(formState.value.itemCode)
    qr.value = data
  } catch (e) {
    if (!e.errorFields) message.error(e.message)
  } finally {
    qrLoading.value = false
  }
}

const download = async () => {
  if (exportLoading.value) return
  exportLoading.value = true
  try {
    const blob = await api.exportItems()
    if (typeof window === 'undefined' || typeof URL === 'undefined') throw new Error('当前环境不支持文件下载')
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = '物品档案.xlsx'
    document.body.appendChild(a)
    a.click()
    setTimeout(() => {
      try { document.body.removeChild(a); URL.revokeObjectURL(url) } catch { /* ignore */ }
    }, 1000)
  } catch (e) {
    message.error(e.message)
  } finally {
    exportLoading.value = false
  }
}

const upload = async ({ file, onSuccess, onError }) => {
  try {
    const r = await api.importItems(file)
    message.success(`导入完成：新增 ${r.created}，更新 ${r.updated}，跳过 ${r.skipped}`)
    onSuccess?.(r, file)
  } catch (e) {
    message.error(e.message)
    onError?.(e)
  }
  return false
}
</script>

<template>
  <div class="page-heading">
    <div>
      <Typography.Title :level="3" class="page-title">二维码与 Excel</Typography.Title>
      <Typography.Text type="secondary">生成物品二维码，批量导入或导出物品档案</Typography.Text>
    </div>
  </div>
  <Row :gutter="18">
    <Col :xs="24" :lg="12">
      <Card>
        <template #title><QrcodeOutlined /> 物品二维码</template>
        <a-form layout="vertical" :model="formState">
          <a-form-item name="itemCode" label="选择物品" :rules="[{ required: true }]">
            <a-select v-model:value="formState.itemCode" show-search placeholder="请搜索并选择物品" option-filter-prop="label" :options="items.map((x) => ({ value: x.code, label: `${x.code} · ${x.name}` }))" />
          </a-form-item>
          <Button type="primary" :loading="qrLoading" @click="generateQr">生成二维码</Button>
        </a-form>
        <div v-if="qr" class="qr-result">
          <a-image :width="250" :src="qr.image" />
          <Typography.Paragraph copyable>{{ qr.content }}</Typography.Paragraph>
          <Typography.Text type="secondary">扫码内容：物品编码，可直接用于入/出库扫码输入。</Typography.Text>
        </div>
      </Card>
    </Col>
    <Col :xs="24" :lg="12">
      <Card title="物品档案 Excel">
        <Typography.Paragraph>导出当前物品档案，或上传按相同列顺序编辑后的 .xlsx 文件进行新增/更新。</Typography.Paragraph>
        <Button :icon="h(DownloadOutlined)" :loading="exportLoading" @click="download">导出物品档案</Button>
        <a-upload accept=".xlsx" :show-upload-list="false" :custom-request="upload" style="margin-left: 12px; display: inline-block;">
          <Button :icon="h(UploadOutlined)">导入 Excel</Button>
        </a-upload>
      </Card>
    </Col>
  </Row>
</template>