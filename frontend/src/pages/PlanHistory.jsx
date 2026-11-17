import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api } from '../lib/api.js'
import { ErrorBox, Loading, useLoad } from '../components/ui.jsx'
import { useProfile } from '../components/ProfileShell.jsx'
import { splitSummary } from '../components/bits.jsx'

const when = (iso) => new Date(iso).toLocaleString(undefined, { day: 'numeric', month: 'short', year: 'numeric', hour: 'numeric', minute: '2-digit' })

export default function PlanHistory({ embedded = false }) {
  const p = useProfile()
  const nav = useNavigate()
  const { loading, data, error } = useLoad(() => api.get(`/profiles/${p.id}/growth-plans`), [p.id])
  const [busy, setBusy] = useState(false)
  const [startError, setStartError] = useState(null)
  const run = async () => {
    setBusy(true); setStartError(null)
    try { const r = await api.post(`/profiles/${p.id}/growth-plans`); nav(`/app/profiles/${p.id}/plans/${r.planId}`) }
    catch (e) { setStartError(e) } finally { setBusy(false) }
  }
  return (
    <div className="wrap" style={{ padding: embedded ? '0 0 20px' : '44px 0 60px' }}>
      <div className="row spread" style={{ marginBottom: 8 }}>
        {embedded ? <span /> : <h1 className="display" style={{ fontSize: 'clamp(30px,4vw,48px)' }}>Growth <em>plans.</em></h1>}
        <button className="btn btn-primary" onClick={run} disabled={busy}>{busy ? 'Starting…' : 'Build a new plan'}</button>
      </div>
      {!embedded && <p className="lead" style={{ margin: '0 0 24px', maxWidth: '62ch' }}>Plans are built from your saved audits, channel analyses and domain checks. A new plan carries over what you've already done.</p>}
      <ErrorBox error={startError} />
      {loading && <Loading />}
      <ErrorBox error={error} />
      {data && data.length === 0 && <div className="panel"><h2 className="h3">No plans yet</h2><p className="muted" style={{ marginTop: 6 }}>Run an audit or analysis first, then build your plan.</p></div>}
      {data && data.map((g, i) => (
        <Link key={g.planId} className="profile-row" to={`/app/profiles/${p.id}/plans/${g.planId}`}>
          <div><h2 style={{ fontSize: 20 }}>{when(g.createdAt)} {i === 0 && <span className="chip chip-dark">Latest</span>}</h2>
            <p className="small muted">{g.summary ? splitSummary(g.summary).head : g.status.toLowerCase()}</p></div>
          {g.progress && <span className="small"><b>{g.progress.completed}</b> of {g.progress.total} done</span>}
        </Link>))}
    </div>
  )
}
