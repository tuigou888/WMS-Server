import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([BarChart, LineChart, PieChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

export { echarts }

const moneyVal = (v) => `¥${Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}`
const numVal = (v) => Number(v).toLocaleString('zh-CN', { maximumFractionDigits: 4 })

const axisLabel = { color: '#7d8d92', fontSize: 11 }
const splitLine = { lineStyle: { color: '#e8efed', type: 'dashed' } }
const baseGrid = { left: 8, right: 16, top: 36, bottom: 0, containLabel: true }

function areaGradient(color) {
  return new echarts.graphic.LinearGradient(0, 0, 0, 1, [
    { offset: 0, color: `${color}26` },
    { offset: 1, color: `${color}03` },
  ])
}

export const trendOption = (data) => ({
  tooltip: { trigger: 'axis', valueFormatter: moneyVal },
  legend: { data: ['入库金额', '出库金额'], top: 0, textStyle: { color: '#64748b', fontSize: 11 } },
  grid: baseGrid,
  xAxis: { type: 'category', data: data.map((d) => d.date), axisLabel, axisLine: { lineStyle: { color: '#b5c4c0' } } },
  yAxis: { type: 'value', axisLabel, splitLine },
  series: [
    { name: '入库金额', type: 'line', smooth: true, data: data.map((d) => Number(d.inbound)), lineStyle: { width: 2, color: '#0b6b53' }, itemStyle: { color: '#0b6b53' }, areaStyle: { color: areaGradient('#0b6b53') } },
    { name: '出库金额', type: 'line', smooth: true, data: data.map((d) => Number(d.outbound)), lineStyle: { width: 2, color: '#b7791f' }, itemStyle: { color: '#b7791f' }, areaStyle: { color: areaGradient('#b7791f') } },
  ],
})

export const pieOption = (data, colors) => ({
  tooltip: { trigger: 'item', formatter: (p) => `${p.name} ${numVal(p.value)}` },
  series: [{
    type: 'pie', radius: ['45%', '70%'], center: ['50%', '52%'],
    data: data.map((d, i) => ({ name: d.name, value: Number(d.value), itemStyle: { color: colors[i % colors.length] } })),
    label: { formatter: (p) => `${p.name} ${numVal(p.value)}` },
    labelLine: { lineStyle: { color: '#a8b8b4' } },
  }],
})

export const profitOption = (data) => ({
  tooltip: { trigger: 'axis', valueFormatter: moneyVal },
  legend: { data: ['成本金额', '销售金额', '利润'], top: 0, textStyle: { color: '#64748b', fontSize: 11 } },
  grid: baseGrid,
  xAxis: { type: 'category', data: data.map((d) => d.month), axisLabel, axisLine: { lineStyle: { color: '#b5c4c0' } } },
  yAxis: { type: 'value', axisLabel, splitLine },
  series: [
    { name: '成本金额', type: 'bar', barMaxWidth: 26, data: data.map((d) => Number(d.cost)), itemStyle: { color: '#b7791f', borderRadius: [3, 3, 0, 0] } },
    { name: '销售金额', type: 'bar', barMaxWidth: 26, data: data.map((d) => Number(d.sale)), itemStyle: { color: '#2563a6', borderRadius: [3, 3, 0, 0] } },
    { name: '利润', type: 'bar', barMaxWidth: 26, data: data.map((d) => Number(d.profit)), itemStyle: { color: '#0b6b53', borderRadius: [3, 3, 0, 0] } },
  ],
})

export const valueOption = (data, colors) => ({
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, valueFormatter: moneyVal },
  grid: { left: 8, right: 16, top: 16, bottom: 0, containLabel: true },
  xAxis: { type: 'value', axisLabel, splitLine },
  yAxis: { type: 'category', data: data.map((d) => d.name), axisLabel, axisLine: { lineStyle: { color: '#b5c4c0' } } },
  series: [{
    type: 'bar', barMaxWidth: 20,
    data: data.map((d, i) => ({ value: Number(d.value), itemStyle: { color: colors[i % colors.length], borderRadius: [0, 4, 4, 0] } })),
  }],
})
