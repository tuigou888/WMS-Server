import fs from 'node:fs'
import path from 'node:path'

const outputDir = path.resolve('dist/build/mp-weixin')
const appJsonPath = path.join(outputDir, 'app.json')

if (!fs.existsSync(appJsonPath)) {
  console.error(`微信小程序编译产物不存在：${appJsonPath}`)
  process.exit(1)
}

const appConfig = JSON.parse(fs.readFileSync(appJsonPath, 'utf8'))
const iconPaths = (appConfig.tabBar?.list || [])
  .flatMap((item) => [item.iconPath, item.selectedIconPath])
  .filter(Boolean)

const missing = iconPaths.filter((relativePath) => {
  const assetPath = path.join(outputDir, relativePath)
  return !fs.existsSync(assetPath) || fs.statSync(assetPath).size === 0
})

if (missing.length) {
  console.error(`微信小程序 tabBar 静态资源缺失：\n- ${missing.join('\n- ')}`)
  process.exit(1)
}

console.log(`微信小程序静态资源校验通过：${iconPaths.length} 个 tabBar 图标`)
