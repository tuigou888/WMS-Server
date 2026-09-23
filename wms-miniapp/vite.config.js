import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

const linuxWechatWxssCompatibility = {
  name: 'wms-linux-wechat-wxss-compatibility',
  apply: 'build',
  enforce: 'post',
  generateBundle(_, bundle) {
    for (const asset of Object.values(bundle)) {
      if (asset.type !== 'asset' || asset.fileName !== 'app.wxss') continue
      asset.source = String(asset.source).replace(
        /page\{--status-bar-height:25px;--top-window-height:0px;--window-top:0px;--window-bottom:0px;--window-left:0px;--window-right:0px;--window-magin:0px\}/,
        '',
      )
    }
  },
}

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    uni(),
    linuxWechatWxssCompatibility,
  ],
})
