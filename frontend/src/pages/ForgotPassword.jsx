import { useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../lib/api.js'
import { ErrorBox } from '../components/ui.jsx'
import { SUPPORT_EMAIL } from '../components/Brand.jsx'

export default function ForgotPassword() {
  const [email, setEmail] = useState('')
  const [busy, setBusy] = useState(false)
  const [sent, setSent] = useState(false)
  const [error, setError] = useState(null)
  const submit = async (e) => {
    e.preventDefault(); setBusy(true); setError(null)
    try { await api.post('/auth/forgot-password', { email }); setSent(true) } catch (err) { setError(err) } finally { setBusy(false) }
  }
  return (
    <div className="band" style={{ paddingTop: 56 }}>
      <div className="wrap" style={{ maxWidth: 520 }}>
        <p className="eyebrow">Forgot your password?</p>
        <h1 className="display" style={{ fontSize: 'clamp(34px,5vw,52px)', margin: '14px 0 8px' }}>Let's get you <em>back in.</em></h1>
        {sent ? (
          <div className="panel" style={{ marginTop: 20 }} role="status">
            <h2 className="h3">Check your email</h2>
            <p style={{ marginTop: 8 }}>If an account exists for <b>{email}</b>, we've sent a link to set a new password. It works once and expires in 30 minutes.</p>
            <p className="small muted" style={{ marginTop: 10 }}>Nothing there? Check your spam folder, or write to <a href={`mailto:${SUPPORT_EMAIL}`}>{SUPPORT_EMAIL}</a>.</p>
            <p style={{ marginTop: 16 }}><Link className="btn" to="/signin">Back to sign in</Link></p>
          </div>
        ) : (
          <form onSubmit={submit} noValidate>
            <p className="muted">Enter the email you signed up with and we'll send you a link to choose a new password.</p>
            <div className="field"><label htmlFor="e">Email</label>
              <input id="e" className="input" type="email" required value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" /></div>
            <ErrorBox error={error} />
            <div className="row" style={{ marginTop: 24, gap: 16 }}>
              <button className="btn btn-primary" disabled={busy || !email.trim()}>{busy ? 'Sending…' : 'Send reset link'}</button>
              <Link className="btn btn-quiet" to="/signin">Back to sign in</Link>
            </div>
          </form>)}
      </div>
    </div>
  )
}
