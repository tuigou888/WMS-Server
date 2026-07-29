import { useEffect, useState } from 'react'
import { App, Button, Card, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Table, Tag, Typography } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { api } from '../api/wms'
import { dateTime, number } from '../utils/format'

const statusLabels = {
  DRAFT: ['草稿', 'default'],
  APPROVED: ['已审核', 'blue'],
  REJECTED: ['已驳回', 'red'],
  COMPLETED: ['已执行', 'green'],
}

export default function TransfersPage({ user }) {
  const { message } = App.useApp()
  const [rows, setRows] = useState([])
  const [warehouses, setWarehouses] = useState([])
  const [items, setItems] = useState([])
  const [transferOpen, setTransferOpen] = useState(false)
  const [warehouseOpen, setWarehouseOpen] = useState(false)
  const [form] = Form.useForm()
  const [warehouseForm] = Form.useForm()

  const loadTransfers = () => api.transfers().then(setRows).catch((error) => message.error(error.message))
  const loadWarehouses = () => api.warehouses().then(setWarehouses).catch((error) => message.error(error.message))

  useEffect(() => {
    loadTransfers()
    Promise.all([loadWarehouses(), api.items({ pageSize: 1000 })])
      .then(([, itemPage]) => setItems(itemPage.records))
      .catch((error) => message.error(error.message))
  }, [])

  const openTransfer = () => {
    form.resetFields()
    form.setFieldsValue({
      lines: [{ quantity: 1, sourceLocationCode: 'A-01-01', targetLocationCode: 'A-01-01' }],
    })
    setTransferOpen(true)
  }

  const createTransfer = async () => {
    try {
      await api.createTransfer(await form.validateFields())
      message.success('调拨草稿已创建')
      setTransferOpen(false)
      loadTransfers()
    } catch (error) {
      if (!error.errorFields) message.error(error.message)
    }
  }

  const createWarehouse = async () => {
    try {
      const data = await warehouseForm.validateFields()
      const warehouse = await api.createWarehouse({ ...data, status: true })
      message.success('仓库创建成功，可立即用于调拨')
      warehouseForm.resetFields()
      setWarehouseOpen(false)
      await loadWarehouses()
      form.setFieldValue('targetWarehouseId', warehouse.id)
    } catch (error) {
      if (!error.errorFields) message.error(error.message)
    }
  }

  const act = async (request) => {
    try {
      await request()
      message.success('操作成功')
      loadTransfers()
    } catch (error) {
      message.error(error.message)
    }
  }

  const warehouseOptions = warehouses.map((warehouse) => ({
    value: warehouse.id,
    label: `${warehouse.code} · ${warehouse.name}`,
  }))

  return <>
    <div className="page-heading">
      <div>
        <Typography.Title level={3} className="page-title">库存调拨</Typography.Title>
        <Typography.Text type="secondary">跨仓库、跨库位调拨，审核通过后才会同步扣减与转入库存</Typography.Text>
      </div>
      <Space>
        {user.role === 'ADMIN' && <Button onClick={() => { warehouseForm.resetFields(); setWarehouseOpen(true) }}>新增仓库</Button>}
        <Button type="primary" icon={<PlusOutlined />} onClick={openTransfer} disabled={warehouses.length < 2}>新建调拨</Button>
      </Space>
    </div>

    {warehouses.length < 2 && <Typography.Paragraph type="warning">请先新增至少一个启用仓库，才能发起跨仓库调拨。</Typography.Paragraph>}

    <Card className="table-card">
      <Table
        rowKey="id"
        dataSource={rows}
        columns={[
          { title: '调拨单号', dataIndex: 'transferNo' },
          { title: '调出仓', dataIndex: 'sourceWarehouseName' },
          { title: '调入仓', dataIndex: 'targetWarehouseName' },
          { title: '明细', render: (_, record) => record.lines.map((line) => `${line.itemCode}${line.batchNo ? ` [${line.batchNo}]` : ''} × ${number(line.quantity)}`).join('；') },
          { title: '状态', dataIndex: 'status', render: (value) => <Tag color={statusLabels[value]?.[1]}>{statusLabels[value]?.[0]}</Tag> },
          { title: '创建时间', dataIndex: 'createdAt', render: dateTime },
          {
            title: '操作',
            render: (_, record) => <Space>
              {record.status === 'DRAFT' && user.role === 'ADMIN' && <><Button type="link" onClick={() => act(() => api.reviewTransfer(record.id, { action: 'APPROVE' }))}>审核通过</Button><Button type="link" danger onClick={() => act(() => api.reviewTransfer(record.id, { action: 'REJECT' }))}>驳回</Button></>}
              {record.status === 'APPROVED' && <Popconfirm title="确认执行调拨？" onConfirm={() => act(() => api.completeTransfer(record.id))}><Button type="primary">执行</Button></Popconfirm>}
            </Space>,
          },
        ]}
      />
    </Card>

    <Modal open={transferOpen} onCancel={() => setTransferOpen(false)} onOk={createTransfer} title="新建调拨草稿" width={840} destroyOnClose>
      <Form form={form} layout="vertical">
        <Space.Compact style={{ width: '100%' }}>
          <Form.Item name="sourceWarehouseId" label="调出仓库" rules={[{ required: true, message: '请选择调出仓库' }]} style={{ width: '50%' }}>
            <Select options={warehouseOptions} placeholder="选择调出仓库" />
          </Form.Item>
          <Form.Item name="targetWarehouseId" label="调入仓库" rules={[{ required: true, message: '请选择调入仓库' }]} style={{ width: '50%' }}>
            <Select options={warehouseOptions} placeholder="选择调入仓库" />
          </Form.Item>
        </Space.Compact>
        <Form.List name="lines">
          {(fields, { add, remove }) => <>
            {fields.map((field) => <Space key={field.key} style={{ display: 'flex', marginBottom: 8 }} align="baseline">
              <Form.Item {...field} name={[field.name, 'itemCode']} rules={[{ required: true, message: '请选择物品' }]}>
                <Select style={{ width: 250 }} placeholder="物品" options={items.map((item) => ({ value: item.code, label: `${item.code} · ${item.name}` }))} />
              </Form.Item>
              <Form.Item {...field} name={[field.name, 'sourceLocationCode']} rules={[{ required: true, message: '请输入调出库位' }]}>
                <Input placeholder="调出库位" />
              </Form.Item>
              <Form.Item {...field} name={[field.name, 'targetLocationCode']} rules={[{ required: true, message: '请输入调入库位' }]}>
                <Input placeholder="调入库位" />
              </Form.Item>
              <Form.Item {...field} name={[field.name, 'batchNo']}>
                <Input placeholder="批次号（可选）" />
              </Form.Item>
              <Form.Item {...field} name={[field.name, 'quantity']} rules={[{ required: true, message: '请输入数量' }]}>
                <InputNumber min={0.0001} addonBefore="数量" />
              </Form.Item>
              {fields.length > 1 && <Button danger type="link" onClick={() => remove(field.name)}>删除</Button>}
            </Space>)}
            <Button block onClick={() => add({ quantity: 1, sourceLocationCode: 'A-01-01', targetLocationCode: 'A-01-01' })}>增加明细</Button>
          </>}
        </Form.List>
        <Form.Item name="remark" label="备注"><Input.TextArea /></Form.Item>
      </Form>
    </Modal>

    <Modal open={warehouseOpen} onCancel={() => setWarehouseOpen(false)} onOk={createWarehouse} title="新增仓库" destroyOnClose>
      <Form form={warehouseForm} layout="vertical">
        <Form.Item name="code" label="仓库编码" rules={[{ required: true, message: '请输入仓库编码' }]}><Input placeholder="例如 WH-002" /></Form.Item>
        <Form.Item name="name" label="仓库名称" rules={[{ required: true, message: '请输入仓库名称' }]}><Input placeholder="例如 备件仓" /></Form.Item>
      </Form>
    </Modal>
  </>
}
