import { onMounted, watch } from 'vue'
import { message } from 'ant-design-vue'

/**
 * 表单草稿保护（#5）：401 过期被踢出登录时，填到一半的表单随组件销毁一起丢失。
 *
 * 弹窗打开期间持续把表单快照写入 sessionStorage；正常提交成功或手动关闭弹窗即清除，
 * 只有被 401 踢出（来不及走关闭流程）才会留下草稿——重新登录回跳原页面后自动恢复。
 * revive 钩子用于把 JSON 序列化后变回字符串的字段还原成组件需要的类型（如 dayjs 日期）。
 */
export function useFormDraft(key, openRef, formStateRef, { revive } = {}) {
  const storageKey = `wms_draft_${key}`
  const save = () => {
    try { sessionStorage.setItem(storageKey, JSON.stringify({ form: formStateRef.value })) } catch { /* 存储异常不阻断编辑 */ }
  }
  watch(formStateRef, () => { if (openRef.value) save() }, { deep: true })
  watch(openRef, (v) => { if (!v) sessionStorage.removeItem(storageKey) })
  onMounted(() => {
    let draft = null
    try { draft = JSON.parse(sessionStorage.getItem(storageKey) || 'null') } catch { draft = null }
    sessionStorage.removeItem(storageKey)
    if (!draft?.form) return
    formStateRef.value = revive ? revive(draft.form) : draft.form
    openRef.value = true
    message.info('已恢复上次未提交的表单草稿')
  })
}
