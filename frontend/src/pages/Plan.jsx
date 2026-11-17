import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api } from '../lib/api.js'
import { ErrorBox, Loading, Thinking, useLoad, usePoll } from '../components/ui.jsx'
import { AiTag, AREA_LABEL, splitSummary, Source, cap, fmtDate } from '../components/bits.jsx'
import OpportunityMap from '../components/OpportunityMap.jsx'

const STATUSES = [['OPEN', 'Open'], ['IN_PROGRESS', 'In progress'], ['COMPLETED', 'Done']]

function Rec({ r, profileId, onChange }) {
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState(null)
  const set = async (status) => {
    setBusy(true); setErr(null)
    try { await api.patch(`/recommendations/${r.id}`, { status }); onChange() } catch (e) { setErr(e) } finally { setBusy(false) }
  }
  const dismissed = r.status === 'DISMISSED'
  return (
    <details className="rec" open={r.position === 1}>
      <summary>
        <span className="n">{r.position}</span>
        <div className={r.status === 'COMPLETED' || dismissed ? 'done' : ''}><h3>{r.title}</h3>
          <div className="meta"><span className={`pri pri-${r.priority.toLowerCase()}`}>{cap(r.priority)} priority</span>
            <span className="chip chip-soft">{AREA_LABEL[r.module] || cap(r.module)}</span><span className="chip chip-soft">{cap(r.impact)} impact</span><span className="chip chip-soft">{cap(r.effort)} effort</span>{r.confidence && <span className="chip chip-soft">{cap(r.confidence)} confidence</span>}<Source label={r.sourceLabel} /></div></div>
        {dismissed ? <span className="chip chip-soft">Dismissed</span> :
          <div className="seg" role="group" aria-label="Status" onClick={(e) => e.stopPropagation()}>
            {STATUSES.map(([v, l]) => <button key={v} type="button" disabled={busy} className={r.status === v ? 'on' : ''} aria-pressed={r.status === v} onClick={() => set(v)}>{l}</button>)}
          </div>}
      </summary>
      <div className="body">
        <div className="receipt-quote"><span className="where">Evidence{r.sourceIds?.length ? ` · ${r.sourceIds.join(', ')}` : ''}</span><span className="quote"><span className="hl">{r.evidence}</span></span></div>
        <dl className="kv"><dt>Why it matters</dt><dd>{r.rationale}</dd><dt>First step</dt><dd>{r.suggestedAction}</dd><dt>How you'll know</dt><dd>{r.howToMeasure}</dd></dl>
        <ErrorBox error={err} />
        <div className="row" style={{ marginTop: 14 }}>
          <Link className="btn btn-sm btn-primary" to={`/app/profiles/${profileId}/studio?rec=${r.id}`}>Draft the wording</Link>
          {dismissed ? <button className="btn btn-sm btn-quiet" onClick={() => set('OPEN')}>Restore</button>
            : <button className="btn btn-sm btn-quiet" onClick={() => set('DISMISSED')} disabled={busy}>Dismiss</button>}
        </div>
      </div>
    </details>
  )
}

export function PlanReport({ planId }) {
  const { loading, data, error, reload } = useLoad(() => api.get(`/growth-plans/${planId}`), [planId])
  const running = data && (data.status === 'QUEUED' || data.status === 'RUNNING')
  usePoll(() => api.get(`/growth-plans/${planId}`), running, reload)

  if (loading && !data) return <Loading />
  if (error) return <div className="wrap"><ErrorBox error={error} /></div>
  const p = data
  if (running) return <Thinking kind="plan" title="Building your roadmap" />
  if (p.status === 'FAILED') return <div className="wrap state"><h1 className="h2">The plan didn't finish</h1>
    <ErrorBox error={{ message: p.warnings?.[0] || 'Something went wrong while building the plan.' }} />
    <p style={{ marginTop: 16 }}><Link className="btn" to={`/app/profiles/${p.profileId}`}>Back</Link></p></div>

  const g = p.progress
  return (
    <div className="wrap" style={{ padding: '44px 0 60px' }}>
      <div className="row" style={{ marginBottom: 14 }}><span className="chip chip-soft">Growth plan</span><span className="chip chip-soft">{fmtDate(p.createdAt)}</span></div>
      <div className="row" style={{ marginBottom: 12, gap: 10 }}><AiTag /><span className="small muted">The roadmap is written by AI from your saved results. Each step quotes its evidence, and priority follows a fixed rule.</span></div>
      <h1 className="display" style={{ fontSize: 'clamp(30px,4vw,50px)', maxWidth: '24ch', margin: '0 0 18px' }}>{splitSummary(p.summary).head}</h1>
      {splitSummary(p.summary).rest && <p className="lead" style={{ maxWidth: '70ch', margin: '0 0 8px' }}>{splitSummary(p.summary).rest}</p>}
      {p.warnings?.length > 0 && <div className="callout" style={{ marginBottom: 20 }}>{p.warnings.join(' ')}</div>}
      <div className="grid g-7-5" style={{ gap: 48, alignItems: 'start', marginTop: 24 }}>
        <section aria-labelledby="r-h">
          <h2 id="r-h" className="h3">{p.recommendations.length} recommendations, in order</h2>
          <p className="small muted" style={{ margin: '4px 0 14px' }}>Ranked by priority, then impact, then effort.</p>
          {p.recommendations.map((r) => <Rec key={r.id} r={r} profileId={p.profileId} onChange={reload} />)}
        </section>
        <aside className="stack sticky" aria-label="Plan overview">
          <div className="panel"><h2 className="h3" style={{ marginBottom: 6 }}>Opportunity map</h2>
            <OpportunityMap items={p.recommendations} />
            <p className="legend"><span><i style={{ background: '#C8F169' }} />High priority</span><span><i style={{ background: '#DDE6D5' }} />Medium</span><span><i style={{ background: '#ECEBE0' }} />Low</span></p></div>
          <div className="panel panel-sage"><h2 className="h3" style={{ marginBottom: 10 }}>Your progress</h2>
            <div className="count"><div><b>{g.open}</b><span>Open</span></div><div><b>{g.inProgress}</b><span>In progress</span></div><div><b>{g.completed}</b><span>Done</span></div><div><b>{g.dismissed}</b><span>Dismissed</span></div></div>
            <p className="small" style={{ marginTop: 14 }}>Generating a new plan later keeps what you've done.</p></div>
          <div className="callout"><b>How priority works.</b> Impact, effort and evidence strength are rated from what we found, and priority follows a fixed rule. Confidence reflects how solid the evidence is: measured facts rate higher than AI-suggested ones. It is an ordering aid, not a prediction of business growth.</div>
          {p.limitations?.length > 0 && <div className="callout"><b>Limits.</b> {p.limitations.join(' ')}</div>}
        </aside>
      </div>
    </div>
  )
}

export default function Plan() {
  const { planId } = useParams()
  return <PlanReport planId={planId} />
}
