import { useEffect, useState } from 'react'
import { App, Card, Segmented, Table, Tag, Typography, Button, Input, Space, DatePicker } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { api } from '../api/wms'
import { dateTime, money, number } from '../utils/format'

const bucketColor={'>90':'magenta','60-90':'volcano','30-60':'orange','0-30':'green'}

export default function ReportsPage2(){
  const {message}=App.useApp()
  const [tab,setTab]=useState('age')
  const [age,setAge]=useState([])
  const [summary,setSummary]=useState([])
  const [summaryLoading,setSummaryLoading]=useState(false)
  const [ageLoading,setAgeLoading]=useState(false)
  const [period,setPeriod]=useState(dayjs().format('YYYY-MM'))
  const [keyword,setKeyword]=useState('')

  const loadAge=()=>{setAgeLoading(true);api.inventoryAge().then(setAge).catch(e=>message.error(e.message)).finally(()=>setAgeLoading(false))}
  const loadSummary=()=>{setSummaryLoading(true);api.inOutSummary(period).then(setSummary).catch(e=>message.error(e.message)).finally(()=>setSummaryLoading(false))}

  useEffect(()=>{if(tab==='age')loadAge();else loadSummary()},[tab])

  const filteredAge=keyword?age.filter(x=>x.itemCode.includes(keyword)||x.itemName.includes(keyword)):age

  const ageCols=[
    {title:'物品',render:(_,r)=><><b>{r.itemName}</b><br/><Typography.Text type="secondary">{r.itemCode} · {r.unit}</Typography.Text></>},
    {title:'仓库 / 库位',render:(_,r)=><>{r.warehouseName}<br/><Typography.Text type="secondary">{r.locationCode||'-'}</Typography.Text></>},
    {title:'批次号',dataIndex:'batchNo',render:v=>v?<Tag color="blue">{v}</Tag>:'-'},
    {title:'数量',dataIndex:'quantity',render:number},
    {title:'金额',dataIndex:'amount',render:money},
    {title:'最早入库日期',dataIndex:'earliestInDate',render:v=>v||'-'},
    {title:'库龄（天）',dataIndex:'ageDays',render:v=><b>{v}</b>},
    {title:'库龄区间',dataIndex:'bucket',render:v=><Tag color={bucketColor[v]}>{v} 天</Tag>},
  ]

  const summaryCols=[
    {title:'物品',dataIndex:'itemName',render:(v,r)=><><b>{v}</b><br/><Typography.Text type="secondary">{r.itemCode} · {r.unit}</Typography.Text></>},
    {title:'期初数量',dataIndex:'openingQuantity',render:number},
    {title:'入库数量',dataIndex:'inQuantity',render:number},
    {title:'入库金额',dataIndex:'inAmount',render:money},
    {title:'出库数量',dataIndex:'outQuantity',render:number},
    {title:'出库金额',dataIndex:'outAmount',render:money},
    {title:'期末数量',dataIndex:'endingQuantity',render:number},
    {title:'期末金额',dataIndex:'endingAmount',render:money},
  ]

  return <>
    <div className="page-heading">
      <div><Typography.Title level={3} className="page-title">报表中心 · 拓展</Typography.Title>
      <Typography.Text type="secondary">库龄分析（识别呆滞料）与收发存汇总（期末对账）</Typography.Text></div>
    </div>
    <Card className="table-card" title={<Segmented value={tab} onChange={setTab} options={[{label:'库龄分析',value:'age'},{label:'收发存汇总',value:'summary'}]}/>}>
      {tab==='age'?
        <Table rowKey="id" loading={ageLoading} dataSource={filteredAge} columns={ageCols}
          title={()=><Space><Input.Search placeholder="搜索编码/名称" value={keyword} onChange={e=>setKeyword(e.target.value)} style={{width:220}}/><Button icon={<ReloadOutlined/>} onClick={loadAge}>刷新</Button></Space>}/>
        :<>
          <Space style={{marginBottom:16}}>
            <DatePicker picker="month" allowClear={false} value={dayjs(period)} onChange={v=>setPeriod(v.format('YYYY-MM'))}/>
            <Button icon={<ReloadOutlined/>} onClick={loadSummary}>查询</Button>
          </Space>
          <Table rowKey="itemCode" loading={summaryLoading} dataSource={summary} columns={summaryCols} pagination={{pageSize:20}}/>
        </>}
    </Card>
  </>
}
