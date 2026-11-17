import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api } from '../lib/api.js'
import { ErrorBox, Loading, useLoad } from '../components/ui.jsx'
import { useProfile } from '../components/ProfileShell.jsx'
import { splitSummary } from '../components/bits.jsx'

const when = (iso) => new Date(iso).toLocaleString(undefined, { day: 'numeric', month: 'short', year: 'numeric', hour: 'numeric', minute: '2-digit' })

export default function AuditHistory({ embedded = false }) {
  const p = useProfile()
  const nav = useNavigate()
  const { loading, data, error } = useLoad(() => api.get(`/profiles/${p.id}/website-audits`), [p.id])
  const [busy, setBusy] = useState(false)
  const [startError, setStartError] = useState(null)

  const run = async () => {
    setBusy(true); setStartError(null)
    try { const r = await api.post(`/profiles/${p.id}/website-audits`); nav(`/app/profiles/${p.id}/audits/${r.auditId}`) }
    catch (e) { setStartError(e) } finally { setBusy(false) }
  }
  const finished = (data || []).filter((a) => a.status === 'COMPLETED' || a.status === 'PARTIAL')

  return (
    <div className="wrap" style={{ padding: embedded ? '0 0 20px' : '44px 0 60px' }}>
      <div className="row spread" style={{ marginBottom: 8 }}>
        {embedded ? <span /> : <h1 className="display" style={{ fontSize: 'clamp(30px,4vw,48px)' }}>Audit <em>history.</em></h1>}
        <div className="row" style={{ gap: 10 }}>
          {finished.length >= 2 && <Link className="btn" to={`/app/profiles/${p.id}/audits/compare`}>Compare the latest two</Link>}
          <button className="btn btn-primary" onClick={run} disabled={busy}>{busy ? 'Starting…' : 'Run a new audit'}</button>
        </div>
      </div>
      {!embedded && <p className="lead" style={{ margin: '0 0 24px', maxWidth: '62ch' }}>Every audit is kept as its own record, with its findings and wording, so you can come back after making changes and see what moved.</p>}
      <ErrorBox error={startError} />
      {loading && <Loading />}
      <ErrorBox error={error} />
      {data && data.length === 0 && <div className="panel"><h2 className="h3">No audits yet</h2><p className="muted" style={{ margin: '6px 0 0' }}>Run your first audit to see what to fix first.</p></div>}
      {data && data.length > 0 && (
        <table className="hist">
          <thead><tr><th>Date</th><th>Website</th><th>Result</th><th>Findings</th><th>PageSpeed (mobile)</th><th></th></tr></thead>
          <tbody>{data.map((a, i) => {
            const done = a.status === 'COMPLETED' || a.status === 'PARTIAL'
            return (
              <tr key={a.auditId}>
                <td><b>{when(a.createdAt)}</b>{i === 0 && <><br /><span className="chip chip-dark">Latest</span></>}</td>
                <td style={{ wordBreak: 'break-all' }}>{a.websiteUrl}<br /><span className="small muted">{a.pagesRead} page{a.pagesRead === 1 ? '' : 's'} read</span></td>
                <td style={{ maxWidth: 360 }}>
                  <span className={`chip ${done ? 'chip-soft' : ''}`}>{a.status === 'PARTIAL' ? 'Partial' : a.status.charAt(0) + a.status.slice(1).toLowerCase()}</span>
                  {a.summary && <p className="small" style={{ marginTop: 6 }}>{splitSummary(a.summary).head}</p>}
                  {a.readinessSummary && <p className="small muted">{a.readinessSummary}</p>}
                </td>
                <td>{done ? <span className="sevcount"><span className="sev sev-high">{a.findings.high} high</span><span className="sev sev-medium">{a.findings.medium} medium</span><span className="sev sev-low">{a.findings.low} low</span></span> : <span className="muted">—</span>}</td>
                <td>{a.performanceScore != null ? <>Performance <b>{a.performanceScore}</b><br />SEO <b>{a.seoScore ?? '—'}</b></> : <span className="muted">Unavailable</span>}</td>
                <td style={{ whiteSpace: 'nowrap' }}>
                  <Link className="btn btn-sm" to={`/app/profiles/${p.id}/audits/${a.auditId}`}>Open</Link>{' '}
                  {done && i > 0 && finished.length > 1 && <Link className="btn btn-sm btn-quiet" to={`/app/profiles/${p.id}/audits/compare?from=${a.auditId}&to=${finished[0].auditId}`}>Compare with latest</Link>}
                </td>
              </tr>)
          })}</tbody>
        </table>)}
    </div>
  )
}
