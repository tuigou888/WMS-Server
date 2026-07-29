import { useEffect, useState } from 'react'
import { App, Card, Col, Row, Segmented, Statistic, Table, Tag, Typography } from 'antd'
import { WarningOutlined } from '@ant-design/icons'
import { api } from '../api/wms'
import { dateTime, money, number } from '../utils/format'

const severityColor = { HIGH: 'red', MEDIUM: 'orange', LOW: 'blue' }
const severityLabel = { HIGH: '高', MEDIUM: '中', LOW: '低' }
const priorityColor = { HIGH: 'red', MEDIUM: 'orange', LOW: 'blue' }
const priorityLabel = { HIGH: '高优先级', MEDIUM: '中优先级', LOW: '低优先级' }

export default function ReportsPage() {
  const { message } = App.useApp()
  const [tab, setTab] = useState('profit')
  const [alerts, setAlerts] = useState([])
  const [profit, setProfit] = useState([])
  const [anomalies, setAnomalies] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setLoading(true)
    Promise.all([api.alerts(), api.profit(), api.anomalies()])
      .then(([a, p, an]) => { setAlerts(a); setProfit(p); setAnomalies(an) })
      .catch((e) => message.error(e.message))
      .finally(() => setLoading(false))
  }, [])

  const totalSale = profit.reduce((s, x) => s + Number(x.saleAmount || 0), 0)
  const totalProfit = profit.reduce((s, x) => s + Number(x.profit || 0), 0)

  return (
    <>
      <div className="page-heading">
        <div>
          <Typography.Title level={3} className="page-title">报表中心</Typography.Title>
          <Typography.Text className="page-subtitle" type="secondary">销售利润分析、库存预警与异常检测</Typography.Text>
        </div>
      </div>

      <Row gutter={18}>
        <Col span={8}>
          <Card className="metric-card">
            <Statistic title="销售出库笔数" value={profit.length} />
          </Card>
        </Col>
        <Col span={8}>
          <Card className="metric-card">
            <Statistic title="销售金额" value={totalSale} precision={2} prefix="¥" />
          </Card>
        </Col>
        <Col span={8}>
          <Card className="metric-card">
            <Statistic title="销售利润" value={totalProfit} precision={2} prefix="¥"
              valueStyle={{ color: totalProfit >= 0 ? '#16a34a' : '#dc2626' }} />
          </Card>
        </Col>
      </Row>

      {/* 库存预警（含智能优先级 + 建议补货） */}
      <Card title="库存预警（智能优先级）" className="table-card" style={{ marginTop: 18 }}>
        <Table
          rowKey="itemId" loading={loading} dataSource={alerts}
          columns={[
            { title: '物品编码', dataIndex: 'itemCode' },
            { title: '物品名称', dataIndex: 'itemName' },
            { title: '安全库存', dataIndex: 'safetyStock', render: number },
            { title: '当前库存', dataIndex: 'currentStock', render: (v) => <b className="negative">{number(v)}</b> },
            { title: '缺货数量', dataIndex: 'shortage', render: (v) => <Tag color="red">{number(v)}</Tag> },
            {
              title: '优先级', dataIndex: 'priority', render: (v) => (
                <Tag color={priorityColor[v]}>{priorityLabel[v] || v}</Tag>
              )
            },
            { title: '日均出库', dataIndex: 'dailyAvgOut', render: (v) => number(v) },
            { title: '建议补货', dataIndex: 'suggestedOrder', render: (v) => <b>{number(v)}</b> },
          ]}
        />
      </Card>

      {/* 异常检测 */}
      <Card title={<><WarningOutlined /> 库存异常检测</>} className="table-card" style={{ marginTop: 18 }}>
        {anomalies.length ? (
          <Table
            rowKey={(r, i) => r.type + '-' + i} loading={loading} dataSource={anomalies}
            columns={[
              {
                title: '类型', dataIndex: 'type', render: (v) => ({
                  CONTINUOUS_DECLINE: '连续出库下降', MISSING_BATCH: '缺少批次号', ABNORMAL_OUTBOUND: '出库异常',
                }[v] || v),
              },
              {
                title: '严重程度', dataIndex: 'severity', render: (v) => (
                  <Tag color={severityColor[v]}>{severityLabel[v]}</Tag>
                ),
              },
              { title: '物品编码', dataIndex: 'itemCode' },
              { title: '物品名称', dataIndex: 'itemName' },
              { title: '描述', dataIndex: 'description' },
            ]}
          />
        ) : (
          <Typography.Text type="secondary">暂无异常检测结果</Typography.Text>
        )}
      </Card>

      {/* 销售利润明细 */}
      <Card title="销售利润明细" className="table-card" style={{ marginTop: 18 }}>
        <Table
          rowKey="id" loading={loading} dataSource={profit}
          columns={[
            { title: '单据编号', dataIndex: 'referenceNo' },
            { title: '物品', render: (_, r) => <><b>{r.itemName}</b><br /><Typography.Text type="secondary">{r.itemCode}</Typography.Text></> },
            { title: '数量', dataIndex: 'quantity', render: (v) => number(Math.abs(Number(v))) },
            { title: '成本金额', dataIndex: 'totalCostAmount', render: money },
            { title: '销售金额', dataIndex: 'saleAmount', render: money },
            { title: '利润', dataIndex: 'profit', render: (v) => <b className={Number(v) >= 0 ? 'positive' : 'negative'}>{money(v)}</b> },
            { title: '时间', dataIndex: 'transactionAt', render: dateTime },
          ]}
        />
      </Card>
    </>
  )
}