import { useCallback, useEffect, useState } from 'react'
import { App, Button, Card, Descriptions, Drawer, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Table, Tag, Typography } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { api } from '../api/wms'
import { dateTime, number } from '../utils/format'

const statuses={DRAFT:['草稿','default'],APPROVED:['已审核','blue'],REJECTED:['已驳回','red'],COMPLETED:['已执行','green']}

export default function AdjustmentsPage({ user }){
  const {message}=App.useApp()
  const [data,setData]=useState([])
  const [items,setItems]=useState([])
  const [warehouses,setWarehouses]=useState([])
  const [open,setOpen]=useState(false)
  const [detail,setDetail]=useState()
  const [form]=Form.useForm()
  const action=Form.useWatch('action',form)

  const load=useCallback(()=>{api.adjustments().then(setData).catch(e=>message.error(e.message))},[message])
  useEffect(()=>{load();Promise.all([api.items({pageSize:1000}),api.warehouses()]).then(([i,w])=>{setItems(i.records);setWarehouses(w)}).catch(e=>message.error(e.message))},[load,message])

  const save=async()=>{
    try{
      const v=await form.validateFields()
      await api.createAdjustment(v)
      message.success('报损报溢草稿已创建')
      setOpen(false);load()
    }catch(e){if(e?.errorFields)return;message.error(e.message)}
  }
  const act=async(fn)=>{try{await fn();message.success('操作成功');load();if(detail)setDetail(null)}catch(e){message.error(e.message)}}
  const review=(r,actionType)=>act(()=>api.reviewAdjustment(r.id,{action:actionType}))

  return <>
    <div className="page-heading">
      <div><Typography.Title level={3} className="page-title">报损 / 报溢</Typography.Title>
      <Typography.Text type="secondary">用于货物破损、过期、丢失、盘盈等非采购/销售性质库存调整</Typography.Text></div>
      <Button type="primary" icon={<PlusOutlined/>} onClick={()=>{form.setFieldsValue({action:'LOSS',lines:[{quantity:1}]});setOpen(true)}}>新建报损报溢</Button>
    </div>
    <Card className="table-card"><Table rowKey="id" dataSource={data} columns={[
      {title:'单据号',dataIndex:'adjustmentNo'},
      {title:'类型',dataIndex:'action',render:v=><Tag color={v==='LOSS'?'volcano':'green'}>{v==='LOSS'?'报损':'报溢'}</Tag>},
      {title:'仓库',dataIndex:'warehouseName'},
      {title:'原因',dataIndex:'reason'},
      {title:'状态',dataIndex:'status',render:v=><Tag color={statuses[v]?.[1]}>{statuses[v]?.[0]||v}</Tag>},
      {title:'审核人',dataIndex:'reviewer'},
      {title:'创建时间',dataIndex:'createdAt',render:dateTime},
      {title:'操作',render:(_,r)=><Space>
        <Button type="link" onClick={()=>setDetail(r)}>详情</Button>
        {r.status==='DRAFT'&&user.role==='ADMIN'&&<>
          <Button type="link" onClick={()=>review(r,'APPROVE')}>审核通过</Button>
          <Button type="link" danger onClick={()=>review(r,'REJECT')}>驳回</Button>
        </>}
        {r.status==='APPROVED'&&<Popconfirm title="执行后将调整库存，确认继续？" onConfirm={()=>act(()=>api.completeAdjustment(r.id))}><Button type="primary">执行</Button></Popconfirm>}
      </Space>}
    ]}/></Card>

    <Modal open={open} title="新建报损 / 报溢草稿" width={850} onCancel={()=>setOpen(false)} onOk={save} destroyOnClose>
      <Form form={form} layout="vertical">
        <Space.Compact style={{width:'100%'}}>
          <Form.Item name="action" label="类型" rules={[{required:true}]} style={{width:'25%'}}>
            <Select options={[{value:'LOSS',label:'报损（减库存）'},{value:'GAIN',label:'报溢（加库存）'}]}/>
          </Form.Item>
          <Form.Item name="warehouseId" label="仓库" rules={[{required:true}]} style={{width:'40%'}}>
            <Select options={warehouses.map(x=>({value:x.id,label:x.name}))}/>
          </Form.Item>
          <Form.Item name="reason" label="原因" style={{width:'35%'}}><Input placeholder="破损 / 过期 / 盘盈 / 丢失"/></Form.Item>
        </Space.Compact>
        <Form.List name="lines">{(fields,{add,remove})=><>
          {fields.map((field,index)=><Card key={field.key} size="small" title={`明细 ${index+1}`} extra={fields.length>1&&<Button type="link" danger onClick={()=>remove(field.name)}>删除</Button>} style={{marginBottom:10}}>
            <Space.Compact style={{width:'100%'}}>
              <Form.Item {...field} name={[field.name,'itemCode']} rules={[{required:true}]} style={{width:'50%'}}>
                <Select showSearch placeholder="物品" options={items.map(x=>({value:x.code,label:`${x.code} · ${x.name}`}))}/>
              </Form.Item>
              <Form.Item {...field} name={[field.name,'locationCode']} rules={[{required:true}]} style={{width:'30%'}}><Input placeholder="库位"/></Form.Item>
              <Form.Item {...field} name={[field.name,'quantity']} rules={[{required:true}]} style={{width:'20%'}}><InputNumber min={0.0001} style={{width:'100%'}} addonBefore="数量"/></Form.Item>
            </Space.Compact>
          </Card>)}
          <Button block onClick={()=>add({quantity:1})}>增加明细</Button>
        </>}</Form.List>
        <Form.Item name="remark" label="备注"><Input.TextArea/></Form.Item>
      </Form>
    </Modal>

    <Drawer open={!!detail} onClose={()=>setDetail()} title={detail?.adjustmentNo} width={620}>
      {detail&&<>
        <Descriptions bordered size="small" column={1}>
          <Descriptions.Item label="类型">{detail.action==='LOSS'?'报损':'报溢'}</Descriptions.Item>
          <Descriptions.Item label="状态">{statuses[detail.status]?.[0]}</Descriptions.Item>
          <Descriptions.Item label="仓库">{detail.warehouseName}</Descriptions.Item>
          <Descriptions.Item label="原因">{detail.reason||'-'}</Descriptions.Item>
          <Descriptions.Item label="备注">{detail.remark||'-'}</Descriptions.Item>
        </Descriptions>
        <Table style={{marginTop:16}} pagination={false} rowKey="id" dataSource={detail.lines} columns={[
          {title:'物品',render:(_,r)=>`${r.itemCode} · ${r.itemName}`},
          {title:'库位',dataIndex:'locationCode'},
          {title:'批次',dataIndex:'batchNo',render:v=>v||'-'},
          {title:'数量',dataIndex:'quantity',render:number}
        ]}/>
      </>}
    </Drawer>
  </>
}
