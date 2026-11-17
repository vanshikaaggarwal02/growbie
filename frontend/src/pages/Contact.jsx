import { useState } from 'react'
import { api } from '../lib/api.js'
import { ErrorBox } from '../components/ui.jsx'
import { SUPPORT_EMAIL } from '../components/Brand.jsx'

export default function Contact() {
  const [f, setF] = useState({ name: '', email: '', business: '', message: '', website: '' })
  const [busy, setBusy] = useState(false)
  const [sent, setSent] = useState(false)
  const [error, setError] = useState(null)
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value })
  const short = f.message.length > 0 && f.message.trim().length < 10
  const submit = async (e) => {
    e.preventDefault(); if (short) return
    setBusy(true); setError(null)
    try { await api.post('/contact', f); setSent(true) } catch (err) { setError(err) } finally { setBusy(false) }
  }
  return (
    <div className="band" style={{ paddingTop: 56 }}>
      <div className="wrap" style={{ maxWidth: 640 }}>
        <p className="eyebrow">Contact</p>
        <h1 className="display" style={{ fontSize: 'clamp(34px,5vw,54px)', margin: '14px 0 8px' }}>Talk to <em>us.</em></h1>
        <p className="muted">Questions, feedback or something not working? Write to us and we'll reply by email. You can also write straight to <a href={`mailto:${SUPPORT_EMAIL}`}>{SUPPORT_EMAIL}</a>.</p>
        {sent ? (
          <div className="panel" style={{ marginTop: 24 }} role="status"><h2 className="h3">Thanks, it's with us</h2>
            <p style={{ marginTop: 8 }}>We'll reply to <b>{f.email}</b>. If you don't hear back, check your spam folder.</p></div>
        ) : (
          <form onSubmit={submit} noValidate className="panel" style={{ marginTop: 24 }}>
            <div className="field" style={{ marginTop: 0 }}><label htmlFor="n">Your name</label><input id="n" className="input" required maxLength={160} value={f.name} onChange={set('name')} autoComplete="name" /></div>
            <div className="field"><label htmlFor="e">Email</label><input id="e" className="input" type="email" required value={f.email} onChange={set('email')} autoComplete="email" /></div>
            <div className="field"><label htmlFor="b">Your business <span className="muted" style={{ fontWeight: 500 }}>(optional)</span></label><input id="b" className="input" maxLength={160} value={f.business} onChange={set('business')} /></div>
            <div className="field"><label htmlFor="m">Message</label><textarea id="m" className="input" style={{ minHeight: 140 }} required maxLength={5000} value={f.message} onChange={set('message')} />
              {short && <p className="hint" style={{ color: 'var(--high)' }} role="alert">Please write at least a sentence.</p>}</div>
            {/* Hidden field: people never see or fill it, bots often do. */}
            <div aria-hidden="true" style={{ position: 'absolute', left: '-9999px' }}><label>Website<input tabIndex={-1} autoComplete="off" value={f.website} onChange={set('website')} /></label></div>
            <ErrorBox error={error} />
            <div style={{ marginTop: 22 }}><button className="btn btn-primary" disabled={busy || !f.name.trim() || !f.email.trim() || f.message.trim().length < 10}>{busy ? 'Sending…' : 'Send message'}</button></div>
          </form>)}
      </div>
    </div>
  )
}
