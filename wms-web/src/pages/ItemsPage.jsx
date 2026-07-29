import { useCallback, useEffect, useState } from 'react'
import { App, Button, Card, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Switch, Table, Tag, Typography } from 'antd'
import { DeleteOutlined, EditOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons'
import { api } from '../api/wms'
import { money, number } from '../utils/format'

const empty = { unit: '个', safetyStock: 0, maxStock: 0, minStock: 0, status: true }

export default function ItemsPage() {
  const { message } = App.useApp()
  const [data, setData] = useState([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [keyword, setKeyword] = useState('')
  const [modal, setModal] = useState(false)
  const [editing, setEditing] = useState()
  const [categories, setCategories] = useState([])
  const [warehouses, setWarehouses] = useState([])
  const [inventoryMap, setInventoryMap] = useState({})
  const [form] = Form.useForm()

  const load = useCallback(() => {
    setLoading(true)
    Promise.all([
      api.items({ page: 1, pageSize: 100, keyword: keyword || undefined }),
      api.inventory(),
    ])
      .then(([items, inv]) => {
        setData(items.records)
        setTotal(items.total)
        // 按 itemId 分组库存
        const map = {}
        inv.forEach((x) => {
          const id = x.itemId
          if (!map[id]) map[id] = []
          map[id].push(x)
        })
        setInventoryMap(map)
      })
      .catch((e) => message.error(e.message))
      .finally(() => setLoading(false))
  }, [keyword])

  useEffect(() => { load(); api.categories().then(setCategories); api.warehouses().then(setWarehouses) }, [])

  const open = (record) => { setEditing(record); form.setFieldsValue(record || empty); setModal(true) }

  const save = async () => {
    const values = await form.validateFields()
    try {
      if (editing) await api.updateItem(editing.id, values)
      else await api.createItem(values)
      message.success(editing ? '物品已更新' : '物品已新增')
      setModal(false)
      load()
    } catch (e) { message.error(e.message) }
  }

  const columns = [
    { title: '物品编码', dataIndex: 'code', width: 145 },
    {
      title: '物品名称', dataIndex: 'name',
      render: (v, r) => <><b>{v}</b><br /><Typography.Text type="secondary">{r.specs || '未设置规格'}</Typography.Text></>,
    },
    { title: '单位', dataIndex: 'unit', width: 60 },
    { title: '安全库存', dataIndex: 'safetyStock', width: 100, render: number },
    {
      title: '状态', dataIndex: 'status', width: 75,
      render: (v) => <Tag color={v ? 'green' : 'default'}>{v ? '启用' : '停用'}</Tag>,
    },
    {
      title: '存储位置',
      width: 300,
      render: (_, r) => {
        const invs = inventoryMap[r.id]
        if (!invs || invs.length === 0) return <Typography.Text type="secondary">暂无库存</Typography.Text>
        return (
          <Space size={[4, 4]} wrap>
            {invs.map((inv, idx) => (
              <Tag key={idx} color="blue" style={{ margin: 0 }}>
                {inv.warehouseName}{inv.locationCode ? ` / ${inv.locationCode}` : ''}
                <span style={{ fontWeight: 600, marginLeft: 4 }}>{number(inv.quantity)}</span>
              </Tag>
            ))}
          </Space>
        )
      },
    },
    {
      title: '操作', width: 130,
      render: (_, r) => (
        <Space>
          <Button type="link" icon={<EditOutlined />} onClick={() => open(r)}>编辑</Button>
          <Popconfirm title="确认删除该物品？" onConfirm={() => api.deleteItem(r.id).then(() => { message.success('已删除'); load() }).catch((e) => message.error(e.message))}>
            <Button type="link" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <>
      <div className="page-heading">
        <div>
          <Typography.Title level={3} className="page-title">物品档案</Typography.Title>
          <Typography.Text className="page-subtitle" type="secondary">维护物品编码、规格与安全库存</Typography.Text>
        </div>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => open()}>新增物品</Button>
      </div>

      <Card className="table-card">
        <Space style={{ marginBottom: 16 }}>
          <Input allowClear placeholder="搜索物品编码或名称" prefix={<SearchOutlined />} value={keyword} onChange={(e) => setKeyword(e.target.value)} onPressEnter={load} style={{ width: 250 }} />
          <Button onClick={load}>查询</Button>
        </Space>
        <Table rowKey="id" loading={loading} dataSource={data} columns={columns} pagination={{ total, pageSize: 100, showTotal: (t) => `共 ${t} 条` }} />
      </Card>

      <Modal open={modal} title={editing ? '编辑物品' : '新增物品'} onCancel={() => setModal(false)} onOk={save} okText="保存" width={660} destroyOnClose>
        <Form form={form} layout="vertical" initialValues={empty}>
          <Space.Compact style={{ width: '100%' }}>
            <Form.Item name="code" label="物品编码" rules={[{ required: true, message: '请输入物品编码' }]} style={{ width: '50%' }}><Input placeholder="如 ITEM-20260716-0001" /></Form.Item>
            <Form.Item name="name" label="物品名称" rules={[{ required: true, message: '请输入物品名称' }]} style={{ width: '50%' }}><Input /></Form.Item>
          </Space.Compact>
          <Space.Compact style={{ width: '100%' }}>
            <Form.Item name="categoryId" label="分类" style={{ width: '33.3%' }}><Select allowClear options={categories.map((x) => ({ value: x.id, label: x.name }))} /></Form.Item>
            <Form.Item name="unit" label="单位" style={{ width: '33.3%' }}><Input /></Form.Item>
            <Form.Item name="specs" label="规格型号" style={{ width: '33.3%' }}><Input /></Form.Item>
          </Space.Compact>
          <Space.Compact style={{ width: '100%' }}>
            <Form.Item name="safetyStock" label="安全库存" style={{ width: '33.3%' }}><InputNumber min={0} style={{ width: '100%' }} /></Form.Item>
            <Form.Item name="minStock" label="最小库存" style={{ width: '33.3%' }}><InputNumber min={0} style={{ width: '100%' }} /></Form.Item>
            <Form.Item name="maxStock" label="最大库存" style={{ width: '33.3%' }}><InputNumber min={0} style={{ width: '100%' }} /></Form.Item>
          </Space.Compact>
          <Form.Item name="defaultWarehouseId" label="默认仓库"><Select allowClear placeholder="选择默认存放仓库" options={warehouses.map((x) => ({ value: x.id, label: x.name }))} /></Form.Item>
          <Form.Item name="remark" label="备注"><Input.TextArea rows={2} /></Form.Item>
          <Form.Item name="status" label="状态" valuePropName="checked"><Switch checkedChildren="启用" unCheckedChildren="停用" /></Form.Item>
        </Form>
      </Modal>
    </>
  )
}