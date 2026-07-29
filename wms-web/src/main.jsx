import React, { useEffect, useState } from 'react'
import ReactDOM from 'react-dom/client'
import { App as AntApp, Button, ConfigProvider, Layout, Menu, Space, Typography } from 'antd'
import { AppstoreOutlined, DashboardOutlined, DatabaseOutlined, FileTextOutlined, InboxOutlined, LogoutOutlined, PieChartOutlined, QrcodeOutlined, SettingOutlined, SwapOutlined, TeamOutlined, UnorderedListOutlined } from '@ant-design/icons'
import 'antd/dist/reset.css'
import './styles.css'
import { api } from './api/wms'
import LoginPage from './pages/LoginPage'
import DashboardPage from './pages/DashboardPage'
import ItemsPage from './pages/ItemsPage'
import MovementPage from './pages/MovementPage'
import InventoryPage from './pages/InventoryPage'
import ReportsPage from './pages/ReportsPage'
import PartnersPage from './pages/PartnersPage'
import DocumentsPage from './pages/DocumentsPage'
import TransfersPage from './pages/TransfersPage'
import StocktakesPage from './pages/StocktakesPage'
import ToolsPage from './pages/ToolsPage'
import UsersPage from './pages/UsersPage'
const { Sider, Header, Content } = Layout
const menuItems = (admin) => [
  { key: 'dashboard', icon: <DashboardOutlined />, label: '仪表盘' },
  { type: 'group', label: '基础资料', children: [{ key: 'items', icon: <AppstoreOutlined />, label: '物品档案' },{ key: 'partners', icon: <TeamOutlined />, label: '供应商 / 客户' }] },
  { type: 'group', label: '仓储业务', children: [{ key: 'stock-in', icon: <InboxOutlined />, label: '扫码入库' },{ key: 'stock-out', icon: <LogoutOutlined />, label: '扫码出库' },{ key: 'documents', icon: <FileTextOutlined />, label: '入库 / 出库单' },{ key: 'transfers', icon: <SwapOutlined />, label: '库存调拨' },{ key: 'stocktakes', icon: <UnorderedListOutlined />, label: '库存盘点' },{ key: 'inventory', icon: <DatabaseOutlined />, label: '库存管理' }] },
  { type: 'group', label: '数据中心', children: [{ key: 'reports', icon: <PieChartOutlined />, label: '报表中心' },{ key: 'tools', icon: <QrcodeOutlined />, label: '二维码与 Excel' }] },
  ...(admin ? [{ type: 'group', label: '系统设置', children: [{ key: 'users', icon: <SettingOutlined />, label: '用户与权限' }] }] : []),
]
function Workspace({user,onLogout}){const [selected,setSelected]=useState('dashboard');const pages={dashboard:DashboardPage,items:ItemsPage,partners:PartnersPage,'stock-in':()=> <MovementPage type="in"/>,'stock-out':()=> <MovementPage type="out"/>,documents:()=> <DocumentsPage user={user}/>,transfers:()=> <TransfersPage user={user}/>,stocktakes:()=> <StocktakesPage user={user}/>,inventory:InventoryPage,reports:ReportsPage,tools:ToolsPage,users:UsersPage};const Page=pages[selected]||DashboardPage;return <Layout className="app-shell"><Sider width={236} className="app-sider"><div className="brand"><SwapOutlined/><span>WMS 管理系统</span></div><Menu theme="dark" mode="inline" selectedKeys={[selected]} items={menuItems(user.role==='ADMIN')} onClick={({key})=>setSelected(key)}/></Sider><Layout><Header className="app-header"><Typography.Text type="secondary">仓库进销存管理</Typography.Text><Space><Typography.Text>{user.displayName||user.username} · {user.role==='ADMIN'?'管理员':'仓库操作员'}</Typography.Text><Button type="link" onClick={onLogout}>退出</Button></Space></Header><Content className="app-content"><Page/></Content></Layout></Layout>}
function Root(){const [user,setUser]=useState(()=>{try{return JSON.parse(localStorage.getItem('wms_user'))}catch{return null}});const [checking,setChecking]=useState(!!localStorage.getItem('wms_token'));useEffect(()=>{if(!localStorage.getItem('wms_token')){setChecking(false);return}api.me().then(x=>{setUser(x);localStorage.setItem('wms_user',JSON.stringify(x))}).catch(()=>{localStorage.removeItem('wms_token');localStorage.removeItem('wms_user');setUser(null)}).finally(()=>setChecking(false))},[]);const login=u=>{setUser(u);localStorage.setItem('wms_user',JSON.stringify(u))};const logout=async()=>{try{await api.logout()}finally{localStorage.removeItem('wms_token');localStorage.removeItem('wms_user');setUser(null)}};return <ConfigProvider theme={{token:{colorPrimary:'#1677ff',borderRadius:8,fontFamily:'-apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif'}}}><AntApp>{checking?<div className="login-shell"><Typography.Text>正在验证登录状态…</Typography.Text></div>:user?<Workspace user={user} onLogout={logout}/>:<LoginPage onLogin={login}/>}</AntApp></ConfigProvider>}
ReactDOM.createRoot(document.getElementById('root')).render(<Root />)
