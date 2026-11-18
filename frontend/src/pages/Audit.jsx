import { Link, useParams } from 'react-router-dom'
import { api } from '../lib/api.js'
import { ErrorBox, Loading, Thinking, useLoad, usePoll } from '../components/ui.jsx'
import { AiTag, Status } from '../components/bits.jsx'
import { Source, Sev, cap, fmtDate, splitSummary } from '../components/bits.jsx'
import { PAGESPEED_METRICS, PAGESPEED_SCORES, SKIP_REASON, prettyKey, scoreBand } from '../lib/labels.js'

const barColour = { HIGH: 'var(--high)', MEDIUM: 'var(--medium)', LOW: 'var(--low)' }
const when = (iso) => new Date(iso).toLocaleString(undefined, { day: 'numeric', month: 'short', year: 'numeric', hour: 'numeric', minute: '2-digit' })

function Finding({ f }) {
  return (
    <article className={`receipt ${f.severity.toLowerCase()}`}><div className="receipt-body">
      <div className="receipt-head"><Sev level={f.severity} /><span className="chip chip-soft">{cap(f.category)}</span><Source label={f.sourceLabel} /></div>
      <h3 className="receipt-title">{f.title}</h3>
      <div className="receipt-quote"><span className="where">{f.pageUrl ? `Found on ${f.pageUrl}` : 'Evidence'}</span><span className="quote"><span className="hl">{f.evidence}</span></span></div>
      <dl className="kv"><dt>Why it matters</dt><dd>{f.impact}</dd><dt>Fix</dt><dd>{f.recommendation}</dd></dl>
      {f.suggestedCopy && <div className="copybox"><div className="label"><span>Suggested wording · {f.suggestedCopy.length} characters</span><AiTag /></div><p>{f.suggestedCopy}</p></div>}
    </div></article>
  )
}


function DomainPanel({ a, id }) {
  const d = a.domainReview
  if (!d) return <section className="panel"><h2 className="h3">Domain &amp; brand assessment</h2><p className="small muted" style={{ marginTop: 6 }}>This audit was saved before domains were assessed. Run a new audit to include it.</p></section>
  const ai = d.aiReview
  const change = ai?.recommendation === 'CHANGE_NEEDS_CAREFUL_REVIEW'
  const mark = (f) => f.status === 'PASS' ? 'Yes' : f.status === 'FAIL' ? 'No' : null
  return (
    <section aria-labelledby="dom-h">
      <div className="row spread"><h2 id="dom-h" className="h3">Domain &amp; brand assessment</h2>{ai ? <span className="src src-ai">AI-suggested · written assessment</span> : <Status state="UNAVAILABLE" label="Written assessment unavailable" />}</div>
      {ai ? (
        <div className={`panel ${change ? '' : 'panel-sage'}`} style={{ marginTop: 12 }}>
          <div className="verdict"><span className="big">{change ? 'Look closer.' : 'Keep it.'}</span>
            <div><h3 style={{ fontSize: 20 }}>{change ? `There may be a reason to review ${d.host}. Nothing should change until you've weighed the risks.` : `${d.host} suits the business. No change is recommended.`}</h3>
              <p style={{ marginTop: 6 }}>{ai.reasoning}</p>
              {d.aiReviewReused && <p className="small muted" style={{ marginTop: 6 }}>Reused from a review made in the last seven days for this domain.</p>}</div></div>
          {change && <div style={{ marginTop: 14 }}>
            {ai.risks?.length > 0 && <><p className="eyebrow">SEO, branding and migration risks</p><ul className="stack-s" style={{ marginTop: 6 }}>{ai.risks.map((r, i) => <li key={i}>{r}</li>)}</ul></>}
            {ai.checklist?.length > 0 && <><p className="eyebrow" style={{ marginTop: 12 }}>If you did change it, in order</p><ol style={{ margin: '6px 0 0 18px' }}>{ai.checklist.map((r, i) => <li key={i} className="small" style={{ marginTop: 3 }}>{r}</li>)}</ol></>}
            <p style={{ marginTop: 14 }}><Link className="btn btn-sm" to={`/app/profiles/${id}/launch`}>Check alternative names on GoDaddy</Link> <span className="small muted">You buy through GoDaddy. Growbie never purchases or changes anything.</span></p></div>}
        </div>) : <p className="small muted" style={{ marginTop: 8 }}>The written assessment could not be produced for this audit. The measured facts below are still accurate.</p>}
      <div className="grid g-2" style={{ gap: 36, marginTop: 20 }}>
        <div><p className="eyebrow">Verified facts</p>
          <table className="dtable" style={{ marginTop: 8 }}><tbody>{d.facts.map((f) => (
            <tr key={f.key}><td>{f.label}{f.status === 'UNAVAILABLE' && <div className="small muted">{f.detail}</div>}</td>
              <td><span className={`src ${f.status === 'UNAVAILABLE' ? 'src-na' : 'src-observed'}`}>{f.status === 'UNAVAILABLE' ? 'Unavailable' : 'Observed'}</span>{' '}
                {mark(f) ? <b>{mark(f)}</b> : f.status === 'INFO' ? <span>{f.detail}</span> : null}{f.status === 'FAIL' && <div className="small muted">{f.detail}</div>}</td></tr>))}</tbody></table></div>
        <div><p className="eyebrow">Written assessment</p>
          {ai?.verdict ? <ul className="stack-s" style={{ marginTop: 8 }}>
            <li><b>Overall.</b> {cap(ai.verdict)}.</li>
            {ai.strengths?.map((x, i) => <li key={`s${i}`}><b>Strength.</b> {x}</li>)}
            {ai.concerns?.map((x, i) => <li key={`c${i}`}><b>Concern.</b> {x}</li>)}
            {ai.suggestion && <li><b>Suggestion.</b> {ai.suggestion}</li>}</ul> : <p className="small muted" style={{ marginTop: 8 }}>Not available.</p>}</div>
      </div>
    </section>
  )
}

function CrawlReport({ a }) {
  const c = a.crawl
  if (!c) return <div className="panel"><h2 className="h3">What was read</h2>
    <p className="small muted" style={{ marginTop: 6 }}>{a.pages.length} page{a.pages.length === 1 ? '' : 's'} read. This audit was saved before detailed crawl notes were recorded, so skipped pages are not listed.</p></div>
  return (
    <div className="panel">
      <div className="row spread"><h2 className="h3">What was read</h2><span className={`chip ${c.complete ? 'chip-soft' : 'chip-lime'}`}>{c.complete ? 'Full coverage' : 'Partial coverage'}</span></div>
      <p className="small" style={{ margin: '8px 0' }}><b>{c.pagesRead} page{c.pagesRead === 1 ? '' : 's'} read.</b> The site {c.robotsTxtFound ? 'has a robots.txt file, which we follow.' : 'has no robots.txt file, so every public page was allowed.'}</p>
      <ul className="stack" style={{ gap: 4, marginBottom: 10 }}>
        {a.pages.map((p) => <li key={p.url} className="small" style={{ wordBreak: 'break-all' }}>✓ {p.url} <span className="muted">· {p.wordCount} words</span></li>)}
      </ul>
      {c.skipped?.length > 0 && <>
        <h3 className="small" style={{ fontWeight: 800, margin: '12px 0 4px' }}>Pages left out ({c.skipped.length})</h3>
        <div>{c.skipped.map((s) => (
          <div key={s.url} className="skiprow"><code>{s.url}</code><br /><b>{SKIP_REASON[s.reason] || s.reason}.</b> <span className="muted">{s.detail}</span></div>))}</div>
      </>}
      <h3 className="small" style={{ fontWeight: 800, margin: '14px 0 4px' }}>Does this affect the audit?</h3>
      <ul className="stack" style={{ gap: 6 }}>{c.impact.map((t, i) => <li key={i} className="small">{t}</li>)}</ul>
    </div>
  )
}

function PageSpeed({ ps }) {
  if (!ps) return null
  return (
    <div className="panel">
      <h2 className="h3">Google PageSpeed (mobile)</h2>
      {ps.available === false ? <p className="small muted" style={{ marginTop: 6 }}>Unavailable for this audit: {ps.error}</p> : <>
        <p className="small muted" style={{ margin: '4px 0 10px' }}>One test of the homepage on a simulated mid-range phone. Scores run 0 to 100: 90 or more is good, 50 to 89 needs work, below 50 is poor.</p>
        <ul className="stack" style={{ gap: 12 }}>{Object.entries(ps.scores || {}).map(([k, v]) => {
          const [name, expl] = PAGESPEED_SCORES[k] || [prettyKey(k), '']
          const [band, cls] = scoreBand(v)
          return <li key={k}><div className="row spread"><b>{name}</b><span className={`small tag-${cls}`}><b>{v}</b> · {band}</span></div>
            <div className={`bar ${cls}`} role="img" aria-label={`${name} ${v} out of 100`}><i style={{ width: `${v}%` }} /></div><p className="explain">{expl}</p></li>
        })}</ul>
        {Object.keys(ps.metrics || {}).length > 0 && <>
          <h3 className="small" style={{ fontWeight: 800, margin: '16px 0 6px' }}>How the page loads</h3>
          <ul className="stack" style={{ gap: 10 }}>{Object.entries(ps.metrics).map(([k, v]) => {
            const [name, expl] = PAGESPEED_METRICS[k] || [prettyKey(k), '']
            return <li key={k}><div className="row spread"><span>{name}</span><b>{v}</b></div><p className="explain">{expl}</p></li>
          })}</ul></>}
        {ps.issues?.length > 0 && <p className="small muted" style={{ marginTop: 12 }}>PageSpeed also flagged: {ps.issues.join('; ')}.</p>}
      </>}
    </div>
  )
}

export function AuditReport({ id, auditId }) {
  const { loading, data, error, reload } = useLoad(() => api.get(`/website-audits/${auditId}`), [auditId])
  const history = useLoad(() => api.get(`/profiles/${id}/website-audits`), [id])
  const running = data && (data.status === 'QUEUED' || data.status === 'RUNNING')
  usePoll(() => api.get(`/website-audits/${auditId}`), running, reload)

  if (loading && !data) return <Loading />
  if (error) return <div className="wrap"><ErrorBox error={error} /></div>
  const a = data
  if (running) return <Thinking kind="audit" title={`Reading ${a.websiteUrl}`} />
  if (a.status === 'FAILED') return <div className="wrap state"><h1 className="h2">The audit didn't finish</h1>
    <ErrorBox error={{ message: a.warnings?.[0] || 'Something went wrong while reading the site.' }} />
    <p style={{ marginTop: 16 }}><Link className="btn btn-primary" to={`/app/profiles/${id}/audits`}>Back to audit history</Link></p></div>

  const checks = Array.isArray(a.readinessChecks) ? a.readinessChecks : Object.values(a.readinessChecks || {})
  const assessed = checks.filter((c) => c.passed !== null && c.passed !== undefined)
  const passed = assessed.filter((c) => c.passed).length
  const high = a.findings.filter((f) => f.severity === 'HIGH')
  const rest = a.findings.filter((f) => f.severity !== 'HIGH')
  const findingsUnavailable = !a.summary
  const others = (history.data || []).filter((x) => x.auditId !== a.auditId && (x.status === 'COMPLETED' || x.status === 'PARTIAL'))
  const previous = others.filter((x) => new Date(x.createdAt) < new Date(a.createdAt))[0]
  const notes = (a.warnings || []).filter((w) =>
    !/page\(s\) were skipped because robots\.txt/.test(w)
    && !/^The written findings are unavailable right now\b/.test(w)
    && !/^Written website findings could not be generated\b/.test(w))
  const head = splitSummary(a.summary || '')

  return (
    <div className="wrap" style={{ padding: '44px 0 60px' }}>
      <div className="row" style={{ marginBottom: 14 }}>
        <span className="chip chip-soft">Website audit</span><span className="chip chip-soft">{a.websiteUrl}</span>
        <span className="chip chip-soft">{when(a.createdAt)}</span>
        <span className="chip chip-soft">{a.pages.length} page{a.pages.length === 1 ? '' : 's'} read</span>
        <span className="chip chip-dark">{a.status === 'PARTIAL' ? (findingsUnavailable ? 'Written findings unavailable' : 'Partial') : 'Complete'}</span>
      </div>
      <h1 className="display" style={{ fontSize: 'clamp(30px,4vw,50px)', maxWidth: '24ch', margin: '0 0 18px' }}>{head.head || 'Audit results'}</h1>
      {head.rest && <p className="lead" style={{ maxWidth: '70ch', margin: '0 0 8px' }}>{head.rest}</p>}
      {findingsUnavailable
        ? <p className="small muted" style={{ margin: '8px 0 0' }}>The written website findings are unavailable for this audit. The checks, PageSpeed and domain facts are measured.</p>
        : <p className="small" style={{ margin: '8px 0 0' }}><AiTag /> The summary, findings and suggested wording are written by AI from the evidence quoted below. The checks, PageSpeed and domain facts are measured.</p>}
      <div className="row" style={{ margin: '12px 0 4px', gap: 10 }}>
        <Link className="btn btn-sm" to={`/app/profiles/${id}/audits`}>All audits</Link>
        {previous && <Link className="btn btn-sm btn-primary" to={`/app/profiles/${id}/audits/compare?from=${previous.auditId}&to=${a.auditId}`}>Compare with the previous audit ({fmtDate(previous.createdAt)})</Link>}
      </div>
      {findingsUnavailable && <div className="callout" style={{ marginTop: 16 }}>We couldn’t produce written website findings for this audit. Your crawl, measured checks, and any available PageSpeed results are still shown. Try running the audit again later.</div>}
      {notes.length > 0 && <div className="callout" style={{ marginTop: 16 }}>{notes.map((w, i) => <p key={i}>{w}</p>)}</div>}

      <div className="grid g-8-4" style={{ gap: 48, alignItems: 'start', marginTop: 28 }}>
        <section className="stack" aria-labelledby="f-h">
          <div className="row spread"><h2 id="f-h" className="h3">{findingsUnavailable ? 'Written findings unavailable' : `${a.findings.length} finding${a.findings.length === 1 ? '' : 's'}, most important first`}</h2>
            {!findingsUnavailable && <span className="small muted">Every quote is copied from your page. Findings we can't verify are removed. "High" is the most serious level.</span>}</div>
          {high.map((f) => <Finding key={f.id} f={f} />)}
          {rest.map((f) => (
            <details key={f.id} className="compact" style={{ '--bar': barColour[f.severity] }}>
              <summary><Sev level={f.severity} /><b>{f.title}</b><Source label={f.sourceLabel} /></summary>
              <div className="more">
                <div className="receipt-quote"><span className="where">{f.pageUrl ? `Found on ${f.pageUrl}` : 'Evidence'}</span><span className="quote"><span className="hl">{f.evidence}</span></span></div>
                <p className="small"><b>Why it matters.</b> {f.impact}</p>
                <p className="small"><b>Fix.</b> {f.recommendation}</p>
                {f.suggestedCopy && <div className="copybox"><div className="label"><span>Suggested wording</span><AiTag /></div><p>{f.suggestedCopy}</p></div>}
              </div>
            </details>))}
          <DomainPanel a={a} id={id} />
          <div className="row" style={{ marginTop: 8 }}><Link className="btn btn-primary" to={`/app/profiles/${id}/plans`}>See these in your growth plan</Link></div>
        </section>

        <aside className="stack sticky" aria-label="Audit details">
          <CrawlReport a={a} />
          <div className="panel">
            <div className="row spread" style={{ marginBottom: 6 }}><h2 className="h3">Basic checks</h2><Source label="OBSERVED" /></div>
            <p className="small" style={{ marginBottom: 8 }}><b>{passed} of {assessed.length}</b> passed{checks.length > assessed.length ? `, ${checks.length - assessed.length} could not be assessed` : ''}.</p>
            <ul className="checklist">
              {checks.map((c) => {
                const cls = c.passed === true ? 'p' : c.passed === false ? 'f' : 'n'
                return <li key={c.key} className={cls}><b>{cls === 'p' ? '✓' : cls === 'f' ? '✕' : '–'}</b><span>{c.label}<small>{c.detail}</small></span></li>
              })}
            </ul>
            <p className="small muted" style={{ marginTop: 10 }}>Fixed rules we can verify, such as "has a title" or "served over HTTPS". They are a checklist of basics, not a success score. A failed check is not always a problem for your business, and passing every check does not guarantee traffic.</p>
          </div>
          <PageSpeed ps={a.pageSpeed} />
          {a.limitations?.length > 0 && <div className="callout"><b>What this audit can't see.</b>
            <ul style={{ margin: '8px 0 0 18px' }}>{a.limitations.map((l, i) => <li key={i} className="small" style={{ marginTop: 4 }}>{l}</li>)}</ul></div>}
        </aside>
      </div>
    </div>
  )
}

export default function Audit() {
  const { id, auditId } = useParams()
  return <AuditReport id={id} auditId={auditId} />
}
