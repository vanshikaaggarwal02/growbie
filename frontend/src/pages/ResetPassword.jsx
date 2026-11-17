import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api } from '../lib/api.js'
import { ErrorBox } from '../components/ui.jsx'

export default function ResetPassword() {
  const [qs] = useSearchParams()
  const token = qs.get('token') || ''
  const [pw, setPw] = useState('')
  const [again, setAgain] = useState('')
  const [busy, setBusy] = useState(false)
  const [done, setDone] = useState(false)
  const [error, setError] = useState(null)
  const problem = pw && pw.length < 10 ? 'Use at least 10 characters.' : again && pw !== again ? "The two passwords don't match." : null
  const submit = async (e) => {
    e.preventDefault(); if (problem) return
    setBusy(true); setError(null)
    try { await api.post('/auth/reset-password', { token, password: pw }); setDone(true) } catch (err) { setError(err) } finally { setBusy(false) }
  }
  return (
    <div className="band" style={{ paddingTop: 56 }}>
      <div className="wrap" style={{ maxWidth: 520 }}>
        <p className="eyebrow">Choose a new password</p>
        <h1 className="display" style={{ fontSize: 'clamp(34px,5vw,52px)', margin: '14px 0 8px' }}>Almost <em>there.</em></h1>
        {done ? (
          <div className="panel" style={{ marginTop: 20 }} role="status"><h2 className="h3">Password changed</h2>
            <p style={{ marginTop: 8 }}>You can sign in with your new password now. Any other device that was signed in has been signed out.</p>
            <p style={{ marginTop: 16 }}><Link className="btn btn-primary" to="/signin">Sign in</Link></p></div>
        ) : !token ? (
          <div className="panel" style={{ marginTop: 20 }}><p>This page needs the link from your reset email.</p><p style={{ marginTop: 14 }}><Link className="btn" to="/forgot-password">Request a new link</Link></p></div>
        ) : (
          <form onSubmit={submit} noValidate>
            <div className="field"><label htmlFor="p1">New password</label><input id="p1" className="input" type="password" value={pw} onChange={(e) => setPw(e.target.value)} autoComplete="new-password" /><p className="hint">At least 10 characters.</p></div>
            <div className="field"><label htmlFor="p2">Type it again</label><input id="p2" className="input" type="password" value={again} onChange={(e) => setAgain(e.target.value)} autoComplete="new-password" /></div>
            {problem && <p className="small" style={{ color: 'var(--high)', marginTop: 8 }} role="alert">{problem}</p>}
            <ErrorBox error={error} />
            {error?.code === 'INVALID_RESET_TOKEN' && <p style={{ marginTop: 10 }}><Link to="/forgot-password"><b>Request a new link</b></Link></p>}
            <div className="row" style={{ marginTop: 24, gap: 16 }}><button className="btn btn-primary" disabled={busy || !pw || !again || !!problem}>{busy ? 'Saving…' : 'Change password'}</button></div>
          </form>)}
      </div>
    </div>
  )
}
