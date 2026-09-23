import fs from 'node:fs'
import path from 'node:path'

const manifest = fs.readFileSync(path.resolve('src/manifest.json'), 'utf8')
const match = manifest.match(/"mp-weixin"\s*:\s*\{[\s\S]*?"appid"\s*:\s*"([^"]*)"/)
const appid = match?.[1]?.trim()
const apiBase = process.env.VITE_API_BASE?.trim()
const errors = []
if (!appid) errors.push('src/manifest.json 的 mp-weixin.appid 未配置')
if (!apiBase || !apiBase.startsWith('https://')) errors.push('VITE_API_BASE 必须是 HTTPS 地址')
if (errors.length) {
  console.error(`微信生产发布配置不完整：\n- ${errors.join('\n- ')}`)
  process.exit(1)
}
console.log(`微信生产发布配置通过：${appid} -> ${apiBase}`)
