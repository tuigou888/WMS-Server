import { useEffect, useState } from 'react'
import { App, Button, Card, Col, Form, Image, Row, Select, Typography, Upload } from 'antd'
import { DownloadOutlined, QrcodeOutlined, UploadOutlined } from '@ant-design/icons'
import { api } from '../api/wms'

export default function ToolsPage() {
  const { message } = App.useApp()
  const [items, setItems] = useState([])
  const [qr, setQr] = useState()
  const [qrLoading, setQrLoading] = useState(false)
  const [exportLoading, setExportLoading] = useState(false)
  const [form] = Form.useForm()

  useEffect(() => {
    api.items({ pageSize: 1000 })
      .then((x) => setItems(x.records))
      .catch((e) => message.error(e.message))
  }, [])

  const generateQr = async () => {
    if (qrLoading) return
    setQrLoading(true)
    try {
      const { itemCode } = await form.validateFields()
      const data = await api.qrcode(itemCode)
      setQr(data)
    } catch (e) {
      if (!e.errorFields) message.error(e.message)
    } finally {
      setQrLoading(false)
    }
  }

  const download = async () => {
    if (exportLoading) return
    setExportLoading(true)
    try {
      const blob = await api.exportItems()
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = '物品档案.xlsx'
      document.body.appendChild(a)
      a.click()
      setTimeout(() => {
        document.body.removeChild(a)
        URL.revokeObjectURL(url)
      }, 1000)
    } catch (e) {
      message.error(e.message)
    } finally {
      setExportLoading(false)
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

  return (
    <>
      <div className="page-heading">
        <div>
          <Typography.Title level={3} className="page-title">二维码与 Excel</Typography.Title>
          <Typography.Text type="secondary">生成物品二维码，批量导入或导出物品档案</Typography.Text>
        </div>
      </div>
      <Row gutter={18}>
        <Col xs={24} lg={12}>
          <Card title={<><QrcodeOutlined /> 物品二维码</>}>
            <Form form={form} layout="vertical">
              <Form.Item name="itemCode" label="选择物品" rules={[{ required: true }]}>
                <Select
                  showSearch
                  placeholder="请搜索并选择物品"
                  optionFilterProp="label"
                  options={items.map((x) => ({ value: x.code, label: `${x.code} · ${x.name}` }))}
                />
              </Form.Item>
              <Button type="primary" onClick={generateQr} loading={qrLoading}>
                生成二维码
              </Button>
            </Form>
            {qr && (
              <div className="qr-result">
                <Image width={250} src={qr.image} />
                <Typography.Paragraph copyable>{qr.content}</Typography.Paragraph>
                <Typography.Text type="secondary">
                  扫码内容：物品编码，可直接用于入/出库扫码输入。
                </Typography.Text>
              </div>
            )}
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card title="物品档案 Excel">
            <Typography.Paragraph>
              导出当前物品档案，或上传按相同列顺序编辑后的 .xlsx 文件进行新增/更新。
            </Typography.Paragraph>
            <Button icon={<DownloadOutlined />} onClick={download} loading={exportLoading}>
              导出物品档案
            </Button>
            <Upload accept=".xlsx" showUploadList={false} customRequest={upload}>
              <Button style={{ marginLeft: 12 }} icon={<UploadOutlined />}>
                导入 Excel
              </Button>
            </Upload>
          </Card>
        </Col>
      </Row>
    </>
  )
}