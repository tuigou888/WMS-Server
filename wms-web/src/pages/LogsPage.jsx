import { useCallback, useEffect, useState } from 'react'
import { App, Button, Card, Input, Select, Space, Table, Tag, Typography } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { api } from '../api/wms'
import { dateTime } from '../utils/format'

const statusColor={SUCCESS:'green',ERROR:'red'}

export default function LogsPage(){
  const {message}=App.useApp()
  const [data,setData]=useState([])
  const [loading,setLoading]=useState(false)
  const [username,setUsername]=useState()
  const [action,setAction]=useState()
  const [result,setResult]=useState()

  const load=useCallback(()=>{
    setLoading(true)
    api.operationLogs({username,action,result}).then(setData).catch(e=>message.error(e.message)).finally(()=>setLoading(false))
  },[username,action,result,message])

  useEffect(()=>{load()},[load])

  return <>
    <div className="page-heading">
      <div><Typography.Title level={3} className="page-title">操作日志</Typography.Title>
      <Typography.Text type="secondary">审计追踪所有登录、单据操作、库存变动记录</Typography.Text></div>
    </div>
    <Card className="table-card">
      <Space style={{marginBottom:16}}>
        <Input placeholder="用户" value={username} onChange={e=>setUsername(e.target.value)} style={{width:150}}/>
        <Input placeholder="动作" value={action} onChange={e=>setAction(e.target.value)} style={{width:150}}/>
        <Select placeholder="结果" allowClear value={result} onChange={setResult} options={[{value:'SUCCESS',label:'成功'},{value:'ERROR',label:'失败'}]} style={{width:120}}/>
        <Button type="primary" icon={<ReloadOutlined/>} onClick={load}>查询</Button>
      </Space>
      <Table rowKey="id" loading={loading} dataSource={data} columns={[
        {title:'时间',dataIndex:'operationAt',render:dateTime},
        {title:'用户',dataIndex:'username'},
        {title:'动作',dataIndex:'action',render:v=><Tag>{v}</Tag>},
        {title:'请求',render:(_,r)=><><b>{r.method}</b> <Typography.Text type="secondary">{r.path}</Typography.Text></>},
        {title:'结果',dataIndex:'result',render:v=><Tag color={statusColor[v]}>{v==='SUCCESS'?'成功':'失败'}</Tag>},
        {title:'消息',dataIndex:'message',ellipsis:true},
      ]} pagination={{pageSize:20}}/>
    </Card>
  </>
}
