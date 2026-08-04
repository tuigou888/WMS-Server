const memory = new Map()

function hasWebStorage() {
  try { return typeof localStorage !== 'undefined' && !!localStorage } catch { return false }
}

export function getStorage(key) {
  try {
    if (hasWebStorage()) return localStorage.getItem(key)
  } catch { /* ignore */ }
  return memory.get(key) ?? null
}

export function setStorage(key, value) {
  memory.set(key, value)
  try {
    if (hasWebStorage()) localStorage.setItem(key, value)
  } catch { /* ignore */ }
}

export function removeStorage(key) {
  memory.delete(key)
  try {
    if (hasWebStorage()) localStorage.removeItem(key)
  } catch { /* ignore */ }
}

export function getJson(key) {
  try { return JSON.parse(getStorage(key)) } catch { return null }
}

export function setJson(key, value) {
  setStorage(key, JSON.stringify(value))
}
