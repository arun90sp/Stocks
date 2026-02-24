export const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080/stocks-backend/api'

export async function initSchema() {
  const res = await fetch(`${API_BASE}/init`, { method: 'POST' })
  if (!res.ok) throw new Error(await res.text())
  return res.json()
}

export async function importCsv(type, file) {
  const csvText = await file.text()
  const res = await fetch(`${API_BASE}/import/${type}`, {
    method: 'POST',
    headers: { 'Content-Type': 'text/plain' },
    body: csvText
  })
  if (!res.ok) throw new Error(await res.text())
  return res.json()
}

export async function getSummary() {
  const res = await fetch(`${API_BASE}/summary`)
  if (!res.ok) throw new Error(await res.text())
  return res.json()
}
