import fs from 'node:fs'
import path from 'node:path'

const manifest = fs.readFileSync(path.resolve('src/manifest.json'), 'utf8')
const match = manifest.match(/"mp-weixin"\s*:\s*\{[\s\S]*?"appid"\s*:\s*"([^"]*)"/)
const appid = match?.[1]?.trim()
const apiBase = process.env.VITE_API_BASE?.trim()
const errors = []
if (!appid) errors.push('src/manifest.json 的 mp-weixin.appid 未配置')
if (!apiBase || !apiBase.startsWith('https://')) errors.push('VITE_API_BASE 必须是 HTTPS 地址')
// H1 防回归：两个小程序共用同一 appid 会互相覆盖（微信后台一个 appid 只对应一个小程序）。
// 兄弟工程不存在（单独检出发布）时跳过比对。
const siblingManifest = path.resolve('../wms-shopping-miniapp/src/manifest.json')
if (fs.existsSync(siblingManifest)) {
  const siblingAppid = fs.readFileSync(siblingManifest, 'utf8').match(/"mp-weixin"\s*:\s*\{[\s\S]*?"appid"\s*:\s*"([^"]*)"/)?.[1]?.trim()
  if (appid && siblingAppid && appid === siblingAppid) {
    errors.push(`appid 与 ../wms-shopping-miniapp 相同（${appid}）：微信后台一个 appid 只对应一个小程序，后上传会覆盖前者`)
  }
}
// 工程内一致性：根目录 project.config.json 是开发者工具打开源码工程时使用的 appid，
// 与 manifest 构建产物不一致会导致"开发工具与正式上传指向不同小程序主体"
const projectConfigPath = path.resolve('project.config.json')
if (fs.existsSync(projectConfigPath)) {
  const projectAppid = fs.readFileSync(projectConfigPath, 'utf8').match(/"appid"\s*:\s*"([^"]*)"/)?.[1]?.trim()
  if (appid && projectAppid && projectAppid !== appid) {
    errors.push(`project.config.json 的 appid（${projectAppid}）与 src/manifest.json 的 mp-weixin.appid（${appid}）不一致`)
  }
}
if (errors.length) {
  console.error(`微信生产发布配置不完整：\n- ${errors.join('\n- ')}`)
  process.exit(1)
}
console.log(`微信生产发布配置通过：${appid} -> ${apiBase}`)
