import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from './api.js'

/** Starts an analysis (POST) and moves to its page. Shows the server's message if it can't start. */
export function useRun() {
  const nav = useNavigate()
  const [busy, setBusy] = useState(null)
  const [error, setError] = useState(null)
  const run = async (key, path, to) => {
    setBusy(key); setError(null)
    try { const r = await api.post(path); nav(to(r)) } catch (e) { setError(e) } finally { setBusy(null) }
  }
  return { run, busy, error }
}
