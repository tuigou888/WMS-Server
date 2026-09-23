// 兼容历史列配置：Ant Design Vue 4 使用 customRender。
export const normalizeColumns = (columns = []) => columns.map((column) => {
  if (!column?.render || column.customRender) return column
  const { render, ...rest } = column
  return {
    ...rest,
    customRender: ({ text, record, index, column: currentColumn }) => render(text, record, index, currentColumn),
  }
})
