import { Link, useSearchParams } from 'react-router-dom'
import { api } from '../lib/api.js'
import { ErrorBox, Loading, useLoad } from '../components/ui.jsx'
import { useProfile } from '../components/ProfileShell.jsx'
import { Sev } from '../components/bits.jsx'
import { PAGESPEED_METRICS, PAGESPEED_SCORES, prettyKey } from '../lib/labels.js'

const CHANGE = {
  FIXED: ['Fixed', 'delta-up'], STILL_FAILING: ['Still failing', 'delta-down'], NEWLY_FAILING: ['Newly failing', 'delta-down'],
  STILL_PASSING: ['Still passing', 'delta-flat'], NOT_ASSESSED: ['Could not be compared', 'delta-flat'],
}
const Delta = ({ d }) => d == null ? <span className="delta-flat">n/a</span>
  : d > 0 ? <span className="delta-up">▲ +{d}</span> : d < 0 ? <span className="delta-down">▼ {d}</span> : <span className="delta-flat">no change</span>

function FindingLine({ f }) {
  return <li style={{ padding: '8px 0', borderTop: '1px solid var(--line)' }}><Sev level={f.severity} /> <b>{f.title}</b>
    <div className="small muted">Evidence: "{f.evidence}"</div></li>
}

export default function AuditCompare() {
  const p = useProfile()
  const [qs] = useSearchParams()
  const from = qs.get('from'), to = qs.get('to')
  const url = `/profiles/${p.id}/website-audits/compare${from && to ? `?from=${from}&to=${to}` : ''}`
  const { loading, data: c, error } = useLoad(() => api.get(url), [url])

  if (loading) return <Loading />
  if (error) return (
    <div className="wrap state">
      <h1 className="h2">{error.code === 'NOT_ENOUGH_AUDITS' ? 'Nothing to compare yet' : 'Could not compare'}</h1>
      <ErrorBox error={error} />
      <p style={{ marginTop: 16 }}><Link className="btn btn-primary" to={`/app/profiles/${p.id}/audits`}>Go to audit history</Link></p>
    </div>)
  const t = c.totals
  const label = (side) => new Date(side.createdAt).toLocaleString(undefined, { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' })
  const changed = c.checks.filter((x) => x.change !== 'STILL_PASSING')

  return (
    <div className="wrap" style={{ padding: '44px 0 60px' }}>
      <div className="row" style={{ marginBottom: 14 }}><span className="chip chip-soft">Audit comparison</span><span className="chip chip-soft">{c.to.websiteUrl}</span></div>
      <h1 className="display" style={{ fontSize: 'clamp(30px,4vw,48px)', margin: '0 0 8px' }}>What changed between <em>{label(c.from)}</em> and <em>{label(c.to)}</em></h1>
      <p className="small muted" style={{ marginBottom: 24 }}>
        <Link to={`/app/profiles/${p.id}/audits/${c.from.auditId}`}>Open the earlier audit</Link> · <Link to={`/app/profiles/${p.id}/audits/${c.to.auditId}`}>Open the later audit</Link>
        {' '}· {c.from.pagesRead} page(s) read then, {c.to.pagesRead} now</p>

      <div className="stats cmp-grid" style={{ marginBottom: 12 }}>
        <div className="stat"><b>{t.fixedChecks}</b><span>Checks now passing that failed before</span></div>
        <div className="stat"><b>{t.stillFailingChecks}</b><span>Checks still failing</span></div>
        <div className="stat"><b>{t.newlyFailingChecks}</b><span>Checks that started failing</span></div>
        <div className="stat"><b>{t.resolvedFindings}</b><span>Findings resolved</span></div>
        <div className="stat"><b>{t.persistingFindings}</b><span>Findings still present</span></div>
        <div className="stat"><b>{t.newFindings}</b><span>New findings</span></div>
      </div>
      <div className="callout" style={{ margin: '12px 0 28px' }}>{c.note}</div>

      <div className="grid g-2" style={{ gap: 48, alignItems: 'start' }}>
        <section>
          <h2 className="h3">Checks that changed</h2>
          {changed.length === 0 ? <p className="muted" style={{ marginTop: 8 }}>Every check has the same result as before.</p>
            : <ul style={{ marginTop: 8 }}>{changed.map((x) => (
              <li key={x.key} className="skiprow"><span className={CHANGE[x.change][1]}>{CHANGE[x.change][0]}</span> · {x.label}</li>))}</ul>}
        </section>
        <section>
          <h2 className="h3">Google PageSpeed (mobile)</h2>
          {!c.from.pageSpeedAvailable || !c.to.pageSpeedAvailable
            ? <p className="muted" style={{ marginTop: 8 }}>PageSpeed was unavailable in {!c.from.pageSpeedAvailable ? 'the earlier' : 'the later'} audit, so scores cannot be compared.</p> : null}
          <table className="hist" style={{ marginTop: 8 }}>
            <thead><tr><th>Score</th><th>Before</th><th>Now</th><th>Change</th></tr></thead>
            <tbody>{c.pageSpeedScores.map((s) => (
              <tr key={s.key}><td>{(PAGESPEED_SCORES[s.key] || [prettyKey(s.key)])[0]}</td><td>{s.before ?? 'unavailable'}</td><td>{s.after ?? 'unavailable'}</td><td><Delta d={s.change} /></td></tr>))}</tbody>
          </table>
          <table className="hist" style={{ marginTop: 18 }}>
            <thead><tr><th>Timing</th><th>Before</th><th>Now</th></tr></thead>
            <tbody>{c.pageSpeedMetrics.map((m) => (
              <tr key={m.key}><td>{(PAGESPEED_METRICS[m.key] || [prettyKey(m.key)])[0]}</td><td>{m.before ?? 'unavailable'}</td><td>{m.after ?? 'unavailable'}</td></tr>))}</tbody>
          </table>
          <p className="small muted" style={{ marginTop: 8 }}>PageSpeed numbers vary between runs, so a small change is not necessarily real.</p>
        </section>
      </div>

      <section className="changegroup" style={{ marginTop: 36 }}>
        <h3><span className="delta-up">Resolved</span> <span className="chip chip-soft">{c.findings.resolved.length}</span></h3>
        {c.findings.resolved.length ? <ul>{c.findings.resolved.map((f) => <FindingLine key={f.id} f={f} />)}</ul> : <p className="muted">None of the earlier findings disappeared.</p>}
        <h3><span className="delta-down">Still present</span> <span className="chip chip-soft">{c.findings.persisting.length}</span></h3>
        {c.findings.persisting.length ? <ul>{c.findings.persisting.map((x) => <FindingLine key={x.after.id} f={x.after} />)}</ul> : <p className="muted">No earlier finding is still present.</p>}
        <h3><span>New</span> <span className="chip chip-soft">{c.findings.added.length}</span></h3>
        {c.findings.added.length ? <ul>{c.findings.added.map((f) => <FindingLine key={f.id} f={f} />)}</ul> : <p className="muted">No new findings.</p>}
      </section>
      <p style={{ marginTop: 28 }}><Link className="btn" to={`/app/profiles/${p.id}/audits`}>Back to audit history</Link></p>
    </div>
  )
}
