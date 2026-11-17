import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api } from '../lib/api.js'
import { ErrorBox, Loading, useLoad } from '../components/ui.jsx'
import { useProfile } from '../components/ProfileShell.jsx'

const when = (iso) => new Date(iso).toLocaleString(undefined, { day: 'numeric', month: 'short', year: 'numeric', hour: 'numeric', minute: '2-digit' })
const compact = (n) => new Intl.NumberFormat(undefined, { notation: 'compact', maximumFractionDigits: 2 }).format(n)
const Change = ({ now, before, fmt = (x) => x.toLocaleString() }) => {
  if (now == null || before == null) return null
  const d = now - before
  return <span className={d > 0 ? 'delta-up' : d < 0 ? 'delta-down' : 'delta-flat'}> {d > 0 ? '▲ +' : d < 0 ? '▼ ' : ''}{d === 0 ? 'no change' : fmt(d)}</span>
}

export default function ChannelHistory({ embedded = false }) {
  const p = useProfile()
  const nav = useNavigate()
  const { loading, data, error } = useLoad(() => api.get(`/profiles/${p.id}/channel-analyses`), [p.id])
  const [busy, setBusy] = useState(false)
  const [startError, setStartError] = useState(null)
  const run = async () => {
    setBusy(true); setStartError(null)
    try { const r = await api.post(`/profiles/${p.id}/channel-analyses`); nav(`/app/profiles/${p.id}/channels/${r.analysisId}`) }
    catch (e) { setStartError(e) } finally { setBusy(false) }
  }
  return (
    <div className="wrap" style={{ padding: embedded ? '0 0 20px' : '44px 0 60px' }}>
      <div className="row spread" style={{ marginBottom: 8 }}>
        {embedded ? <span /> : <h1 className="display" style={{ fontSize: 'clamp(30px,4vw,48px)' }}>Channel <em>history.</em></h1>}
        <button className="btn btn-primary" onClick={run} disabled={busy}>{busy ? 'Starting…' : 'Analyse my channel'}</button>
      </div>
      {!embedded && <p className="lead" style={{ margin: '0 0 24px', maxWidth: '62ch' }}>Each analysis is saved with the numbers measured that day, so you can see how the channel moves between runs. Changes are shown against the run before.</p>}
      <ErrorBox error={startError} />
      {loading && <Loading />}
      <ErrorBox error={error} />
      {data && data.length === 0 && <div className="panel"><h2 className="h3">No analyses yet</h2><p className="muted" style={{ marginTop: 6 }}>Run the first one to see your channel's patterns.</p></div>}
      {data && data.length > 0 && <table className="hist">
        <thead><tr><th>Date</th><th>Status</th><th>Subscribers</th><th>Total views</th><th>Videos</th><th>Uploads per week</th><th></th></tr></thead>
        <tbody>{data.map((s, i) => {
          const prev = data.slice(i + 1).find((x) => x.status === 'COMPLETED' || x.status === 'PARTIAL')
          return (
            <tr key={s.analysisId}>
              <td><b>{when(s.createdAt)}</b>{i === 0 && <><br /><span className="chip chip-dark">Latest</span></>}</td>
              <td><span className="chip chip-soft">{s.status === 'PARTIAL' ? 'Partial' : s.status.charAt(0) + s.status.slice(1).toLowerCase()}</span></td>
              <td>{s.subscribersHidden ? <span className="muted">Hidden</span> : s.subscribers == null ? <span className="src src-na">Unavailable</span> : <>≈{compact(s.subscribers)}<Change now={s.subscribers} before={prev?.subscribers} fmt={compact} /></>}</td>
              <td>{s.totalViews == null ? <span className="src src-na">Unavailable</span> : <>{compact(s.totalViews)}<Change now={s.totalViews} before={prev?.totalViews} fmt={compact} /></>}</td>
              <td>{s.videoCount == null ? <span className="src src-na">Unavailable</span> : <>{s.videoCount.toLocaleString()}<Change now={s.videoCount} before={prev?.videoCount} /></>}</td>
              <td>{s.uploadsPerWeek == null ? <span className="muted">n/a</span> : <>{s.uploadsPerWeek}<Change now={s.uploadsPerWeek} before={prev?.uploadsPerWeek} fmt={(x) => Math.round(x * 10) / 10} /></>}</td>
              <td>{(s.status === 'COMPLETED' || s.status === 'PARTIAL') && <Link className="btn btn-sm" to={`/app/profiles/${p.id}/channels/${s.analysisId}`}>Open</Link>}</td>
            </tr>)
        })}</tbody></table>}
      <p className="small muted" style={{ marginTop: 14 }}>Subscriber counts are rounded by YouTube, so small differences may not be real.</p>
    </div>
  )
}
