import { Link } from 'react-router-dom'
import { api } from '../lib/api.js'
import { ErrorBox, Loading, useLoad } from '../components/ui.jsx'
import { useProfile } from '../components/ProfileShell.jsx'
import { Source, Status, fmtDate } from '../components/bits.jsx'
import { useRun } from '../lib/useRun.js'

const MARK = { PASS: '', FAIL: 'caution', NOT_ANALYSED: 'gap', UNAVAILABLE: 'gap' }

export default function Geo() {
  const profile = useProfile()
  const base = `/app/profiles/${profile.id}`
  const list = useLoad(() => api.get(`/profiles/${profile.id}/website-audits`), [profile.id])
  const latest = (list.data || []).find((a) => a.status === 'COMPLETED' || a.status === 'PARTIAL')
  const audit = useLoad(() => (latest ? api.get(`/website-audits/${latest.auditId}`) : Promise.resolve(null)), [latest?.auditId])
  const { run, busy, error: runError } = useRun()
  const start = () => run('audit', `/profiles/${profile.id}/website-audits`, (r) => `${base}/audits/${r.auditId}`)

  if (list.loading || audit.loading) return <Loading />
  if (list.error || audit.error) return <div className="wrap"><ErrorBox error={list.error || audit.error} /></div>
  const geo = audit.data?.geoReadiness

  return (
    <div className="wrap" style={{ padding: '44px 0 60px' }}>
      <div className="row" style={{ marginBottom: 14 }}><span className="chip chip-soft">GEO · AI search readiness</span>
        <Status state={geo ? 'ANALYSED' : 'NOT_YET_ANALYSED'} label={geo ? 'Readiness analysed' : undefined} />{latest && <span className="chip chip-soft">{fmtDate(latest.createdAt)}</span>}</div>
      <h1 className="title">Can AI tools read and describe <em>{profile.displayName}</em>?</h1>
      <p className="lead" style={{ margin: '18px 0 40px', maxWidth: '64ch' }}>GEO is about whether AI search tools can understand and accurately describe your business. We look at two separate things and never mix them: signals on your website, and what AI tools actually say when asked.</p>

      {!geo ? (
        <div className="panel" style={{ maxWidth: 720 }}>
          <h2 className="h3">{!profile.websiteUrl ? 'GEO starts once your website exists.' : latest ? 'This audit was saved before GEO checks existed.' : 'Not yet analysed'}</h2>
          <p style={{ marginTop: 8 }}>{!profile.websiteUrl ? "AI search tools read websites, so with no website there's nothing for us to check yet. That's shown as not yet analysed, not as a low score. Building a page with clear wording from the start is the best preparation." : 'GEO readiness comes from the same audit as your SEO results. Run an audit and it will appear here.'}</p>
          <p style={{ marginTop: 16 }}>{profile.websiteUrl ? <button className="btn btn-primary" onClick={start} disabled={!!busy}>{busy ? 'Starting…' : 'Run the audit'}</button> : <Link className="btn btn-primary" to={`${base}/details`}>Add a website address</Link>}</p>
          <ErrorBox error={runError} />
        </div>
      ) : (
        <div className="grid g-8-4" style={{ gap: 48, alignItems: 'start' }}>
          <div className="stack" style={{ gap: 44 }}>
            <section><div className="row spread"><div><p className="eyebrow">Part 1</p><h2 className="h3">Readiness signals on your website</h2></div><Source label="OBSERVED" /></div>
              <ul className="tick" style={{ marginTop: 10 }}>{geo.signals.map((s) => (
                <li key={s.key} className={MARK[s.status]}>{s.label}<small>{s.detail}</small></li>))}</ul>
              <p className="small muted" style={{ marginTop: 10 }}><b>{geo.summary}.</b> This is a checklist, not a score. There is no proven ranking formula for AI search, so we don't invent one.</p></section>
            <section><div className="row spread"><div><p className="eyebrow">Part 2</p><h2 className="h3">What AI search tools actually say</h2></div><span className="st st-notyet">Not yet tested</span></div>
              <div className="panel" style={{ marginTop: 12 }}>
                <p>We haven't run any AI search tests for {profile.displayName}, so there is nothing to report and we won't suggest otherwise. When tests exist, each result will show the exact question, which tool answered, the date, whether your business appeared or was cited, whether the details were accurate, and who appeared instead.</p>
                <p className="small muted" style={{ marginTop: 10 }}>Answers vary from one run to the next, so a single result is a sample, not a ranking.</p></div></section>
          </div>
          <aside className="stack" style={{ gap: 20 }}>
            <div className="callout"><b>No guarantees.</b> Nobody can promise visibility in ChatGPT, Gemini, Perplexity or any other AI tool. We show readiness and, later, honest observations.</div>
            <div className="callout"><b>Checked on:</b> {audit.data.websiteUrl}, {audit.data.pages.length} page(s) read, {fmtDate(audit.data.createdAt)}.</div>
            <Link className="btn btn-primary" to={`${base}/plans`}>See the GEO actions in your roadmap</Link>
          </aside>
        </div>)}
    </div>
  )
}
