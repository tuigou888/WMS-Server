// 微信 showActionSheet 的 itemList 上限 6 项，超限时调用直接 fail（且无 fail 回调时选择器静默打不开）。
// 超过 6 项按页展示，末项为"更多…"翻页；resolve 选中下标，取消/失败返回 -1。
export function chooseIndex(labels) {
  return new Promise((resolve) => {
    // "更多…"要占一个槽位，每页最多 5 项 + 1 个翻页项 = 6（微信 itemList 上限）
    const PAGE = 5
    const pick = (offset) => {
      const slice = labels.slice(offset, offset + PAGE)
      const items = [...slice]
      if (offset + PAGE < labels.length) items.push('更多…')
      uni.showActionSheet({
        itemList: items,
        success: (r) => {
          if (r.tapIndex === items.length - 1 && offset + PAGE < labels.length) {
            pick(offset + PAGE)
            return
          }
          resolve(offset + r.tapIndex)
        },
        fail: () => resolve(-1),
      })
    }
    pick(0)
  })
}
