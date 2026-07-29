import { useEffect, useState } from 'react'
import {
  AlertOutlined, ArrowDownOutlined, ArrowUpOutlined, DatabaseOutlined, WalletOutlined,
} from '@ant-design/icons'
import { App, Card, Col, Empty, Row, Skeleton, Table, Tag, Typography } from 'antd'
import {
  Area, AreaChart, Bar, BarChart, CartesianGrid, Cell, Legend, Pie, PieChart,
  ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts'
import { api } from '../api/wms'
import { dateTime, money, number } from '../utils/format'

const metricStyle = [
  { icon: <DatabaseOutlined />, color: '#e6f4ff', iconColor: '#1677ff' },
  { icon: <WalletOutlined />, color: '#f6ffed', iconColor: '#52c41a' },
  { icon: <ArrowDownOutlined />, color: '#fff7e6', iconColor: '#fa8c16' },
  { icon: <AlertOutlined />, color: '#fff1f0', iconColor: '#ff4d4f' },
]

const CHART_COLORS = ['#1677ff', '#52c41a', '#fa8c16', '#eb2f96', '#722ed1', '#13c2c2', '#f5222d', '#faad14', '#2f54eb', '#a0d911']

export default function DashboardPage() {
  const [data, setData] = useState()
  const { message } = App.useApp()

  useEffect(() => {
    api.dashboard().then(setData).catch((e) => message.error(e.message))
  }, [])

  if (!data) return <Skeleton active paragraph={{ rows: 10 }} />

  const pieData = data.categoryDistribution?.length
    ? data.categoryDistribution.map((d) => ({ ...d, value: Number(d.value) }))
    : [{ name: '暂无数据', value: 1 }]

  const metrics = [
    ['库存品种', data.stockItemCount, '当前有库存的物品', ''],
    ['库存总金额', money(data.totalAmount), '按移动加权成本计价', ''],
    ['今日销售额', money(data.todayOutboundAmount), '本日销售出库金额', ''],
    ['库存预警', data.alertCount, '低于安全库存的物品', ''],
  ]

  const profitFormatter = (val) => [`¥${Number(val).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}`, undefined]

  return (
    <>
      <div className="page-heading">
        <div>
          <Typography.Title level={3} className="page-title">仪表盘</Typography.Title>
          <Typography.Text className="page-subtitle" type="secondary">
            实时掌握仓库库存、出入库与预警情况
          </Typography.Text>
        </div>
      </div>

      {/* 指标卡片 */}
      <Row gutter={[18, 18]}>
        {metrics.map(([title, value, tip], index) => (
          <Col span={6} key={title}>
            <Card className="metric-card">
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <div>
                  <Typography.Text type="secondary">{title}</Typography.Text>
                  <div style={{ fontSize: 24, fontWeight: 650, margin: '6px 0' }}>{value}</div>
                  <Typography.Text className="muted">{tip}</Typography.Text>
                </div>
                <div
                  className="metric-icon"
                  style={{ background: metricStyle[index].color, color: metricStyle[index].iconColor }}
                >
                  {metricStyle[index].icon}
                </div>
              </div>
            </Card>
          </Col>
        ))}
      </Row>

      {/* 第1行图表：趋势 + 分类 */}
      <Row gutter={[18, 18]} style={{ marginTop: 18 }}>
        <Col span={14}>
          <Card title="出入库趋势（近 14 天）" className="table-card">
            {data.dailyTrend?.length ? (
              <ResponsiveContainer width="100%" height={280}>
                <AreaChart data={data.dailyTrend}>
                  <defs>
                    <linearGradient id="colorIn" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#1677ff" stopOpacity={0.15} />
                      <stop offset="95%" stopColor="#1677ff" stopOpacity={0.01} />
                    </linearGradient>
                    <linearGradient id="colorOut" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#52c41a" stopOpacity={0.15} />
                      <stop offset="95%" stopColor="#52c41a" stopOpacity={0.01} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                  <XAxis dataKey="date" fontSize={12} stroke="#94a3b8" />
                  <YAxis fontSize={12} stroke="#94a3b8" />
                  <Tooltip formatter={profitFormatter} contentStyle={{ borderRadius: 8, border: '1px solid #e5e7eb' }} />
                  <Legend />
                  <Area type="monotone" dataKey="inbound" name="入库金额" stroke="#1677ff" fill="url(#colorIn)" strokeWidth={2} />
                  <Area type="monotone" dataKey="outbound" name="出库金额" stroke="#52c41a" fill="url(#colorOut)" strokeWidth={2} />
                </AreaChart>
              </ResponsiveContainer>
            ) : (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无趋势数据" />
            )}
          </Card>
        </Col>
        <Col span={10}>
          <Card title="库存分类分布（数量）" className="table-card">
            {data.categoryDistribution?.length ? (
              <ResponsiveContainer width="100%" height={280}>
                <PieChart>
                  <Pie data={pieData} dataKey="value" nameKey="name" cx="50%" cy="50%" outerRadius={90} innerRadius={45}
                    label={({ name, value }) => `${name} ${number(value)}`} labelLine={{ stroke: '#94a3b8' }}>
                    {pieData.map((_, idx) => (<Cell key={idx} fill={CHART_COLORS[idx % CHART_COLORS.length]} />))}
                  </Pie>
                  <Tooltip formatter={(val) => [number(val), '库存数量']} />
                </PieChart>
              </ResponsiveContainer>
            ) : (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无分类数据" />
            )}
          </Card>
        </Col>
      </Row>

      {/* 第2行图表：金额相关（月度利润 + 分类金额） */}
      <Row gutter={[18, 18]} style={{ marginTop: 18 }}>
        <Col span={12}>
          <Card title="月度利润趋势" className="table-card">
            {data.monthlyProfit?.length ? (
              <ResponsiveContainer width="100%" height={280}>
                <BarChart data={data.monthlyProfit}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                  <XAxis dataKey="month" fontSize={12} stroke="#94a3b8" />
                  <YAxis fontSize={12} stroke="#94a3b8" />
                  <Tooltip formatter={profitFormatter} contentStyle={{ borderRadius: 8 }} />
                  <Legend />
                  <Bar dataKey="cost" name="成本金额" fill="#fa8c16" radius={[4, 4, 0, 0]} />
                  <Bar dataKey="sale" name="销售金额" fill="#1677ff" radius={[4, 4, 0, 0]} />
                  <Bar dataKey="profit" name="利润" fill="#52c41a" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            ) : (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无利润数据" />
            )}
          </Card>
        </Col>
        <Col span={12}>
          <Card title="库存金额分布（按分类）" className="table-card">
            {data.valueByCategory?.length ? (
              <ResponsiveContainer width="100%" height={280}>
                <BarChart data={data.valueByCategory} layout="vertical">
                  <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                  <XAxis type="number" fontSize={12} stroke="#94a3b8" />
                  <YAxis type="category" dataKey="name" fontSize={12} stroke="#94a3b8" width={80} />
                  <Tooltip formatter={profitFormatter} contentStyle={{ borderRadius: 8 }} />
                  <Bar dataKey="value" name="库存金额" radius={[0, 4, 4, 0]}>
                    {data.valueByCategory.map((_, idx) => (
                      <Cell key={idx} fill={CHART_COLORS[idx % CHART_COLORS.length]} />
                    ))}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            ) : (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无金额数据" />
            )}
          </Card>
        </Col>
      </Row>

      {/* 第3行：表格区 */}
      <Row gutter={[18, 18]} style={{ marginTop: 18 }}>
        <Col span={15}>
          <Card title="库存金额 TOP 物品" className="table-card">
            <Table
              rowKey="itemCode"
              pagination={false}
              dataSource={data.topItemsByValue}
              columns={[
                { title: '物品编码', dataIndex: 'itemCode' },
                { title: '物品名称', dataIndex: 'itemName' },
                { title: '单位', dataIndex: 'unit', width: 60 },
                { title: '库存数量', dataIndex: 'quantity', render: (v) => number(v) },
                { title: '库存金额', dataIndex: 'value', render: (v) => <b>{money(v)}</b> },
              ]}
            />
          </Card>
        </Col>
        <Col span={9}>
          <Card title="近期库存流水" className="table-card">
            <Table
              rowKey="id"
              pagination={false}
              size="small"
              dataSource={data.recentTransactions}
              columns={[
                { title: '单据编号', dataIndex: 'referenceNo', ellipsis: true },
                {
                  title: '类型', dataIndex: 'transactionType', width: 60,
                  render: (v) => (<Tag color={v === 'in' ? 'green' : 'volcano'}>{v === 'in' ? '入库' : '出库'}</Tag>),
                },
                {
                  title: '金额', render: (_, r) => r.saleAmount ? money(r.saleAmount) : money(r.totalCostAmount),
                },
              ]}
            />
          </Card>
          <Card title="库存预警" className="table-card" style={{ marginTop: 18 }}>
            {data.alerts.length ? (
              data.alerts.map((a) => (
                <div key={a.itemId} style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0', borderBottom: '1px solid #f0f0f0' }}>
                  <div>
                    <b>{a.itemName}</b><br />
                    <Typography.Text type="secondary">{a.itemCode}</Typography.Text>
                  </div>
                  <div style={{ textAlign: 'right' }}>
                    <Typography.Text className="negative">缺少 {number(a.shortage)} {a.unit}</Typography.Text><br />
                    <Typography.Text type="secondary">安全库存 {number(a.safetyStock)}</Typography.Text>
                  </div>
                </div>
              ))
            ) : (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无库存预警" />
            )}
          </Card>
        </Col>
      </Row>
    </>
  )
}