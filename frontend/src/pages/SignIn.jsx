import { useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '../lib/auth.jsx'
import { ErrorBox } from '../components/ui.jsx'
import { Link } from 'react-router-dom'
import { TAGLINE } from '../components/Brand.jsx'

export default function SignIn() {
  const { user, login, register } = useAuth()
  const nav = useNavigate()
  const [mode, setMode] = useState('login')
  const [f, setF] = useState({ email: '', password: '', displayName: '' })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  if (user) return <Navigate to="/app" replace />

  const set = (k) => (e) => setF({ ...f, [k]: e.target.value })
  const submit = async (e) => {
    e.preventDefault()
    setBusy(true); setError(null)
    try {
      if (mode === 'login') await login(f.email, f.password)
      else await register(f.email, f.password, f.displayName)
      nav('/app')
    } catch (err) {
      setError(err.status === 401 ? { message: 'Email or password is not right.' } : err)
    } finally { setBusy(false) }
  }

  return (
    <div className="band" style={{ paddingTop: 56 }}>
      <div className="wrap" style={{ maxWidth: 520 }}>
        <p className="eyebrow">{mode === 'login' ? 'Welcome back' : 'Create your account'}</p>
        <h1 className="display" style={{ fontSize: 'clamp(36px,5vw,54px)', margin: '14px 0 8px' }}>
          {mode === 'login' ? <>Sign <em>in.</em></> : <>Let's get <em>started.</em></>}
        </h1>
        <p className="tagline-sm" style={{ marginBottom: 6 }}>{TAGLINE}</p>
        <form onSubmit={submit} noValidate>
          {mode === 'register' && (
            <div className="field"><label htmlFor="n">Your name</label>
              <input id="n" className="input" required maxLength={160} value={f.displayName} onChange={set('displayName')} autoComplete="name" /></div>)}
          <div className="field"><label htmlFor="e">Email</label>
            <input id="e" className="input" type="email" required value={f.email} onChange={set('email')} autoComplete="email" /></div>
          <div className="field"><label htmlFor="p">Password</label>
            <input id="p" className="input" type="password" required minLength={mode === 'register' ? 10 : undefined} value={f.password} onChange={set('password')}
              autoComplete={mode === 'login' ? 'current-password' : 'new-password'} />
            {mode === 'register' && <p className="hint">At least 10 characters.</p>}
            {mode === 'login' && <p className="hint"><Link to="/forgot-password">Forgot password?</Link></p>}</div>
          <ErrorBox error={error} />
          <div className="row" style={{ marginTop: 24, gap: 16 }}>
            <button className="btn btn-primary" disabled={busy}>{busy ? 'One moment…' : mode === 'login' ? 'Sign in' : 'Create account'}</button>
            <button type="button" className="btn btn-quiet" onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setError(null) }}>
              {mode === 'login' ? 'I need an account' : 'I already have an account'}</button>
          </div>
        </form>
      </div>
    </div>
  )
}
