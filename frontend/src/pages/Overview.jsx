import { Link } from 'react-router-dom'
import { api } from '../lib/api.js'
import { ErrorBox, Loading, useLoad } from '../components/ui.jsx'
import { useProfile } from '../components/ProfileShell.jsx'
import { AiTag, AREA_LABEL, PATH_LABEL, Source, Status, cap, fmtDate } from '../components/bits.jsx'
import OpportunityMap from '../components/OpportunityMap.jsx'
import { useRun } from '../lib/useRun.js'

const DOMAIN_VERDICT = { KEEP_CURRENT: 'Keep it', CHANGE_NEEDS_CAREFUL_REVIEW: 'Worth a closer look' }

function Trail({ ov, profile }) {
  const diag = ov.reports.length
  const prog = ov.progress
  return (
    <ol className="trail">
      <li className="done"><time>{fmtDate(profile.createdAt)} · Understand</time><b>Business details saved.</b> What you told us is the starting point for every analysis.</li>
      <li className={diag ? 'done' : 'next'}><time>{diag ? `${fmtDate(ov.reports[0].at)} · Diagnose` : 'Next · Diagnose'}</time>
        <b>{diag ? `${diag} report${diag === 1 ? '' : 's'} saved.` : 'Run your first analysis.'}</b> {diag ? 'Every finding quotes its evidence.' : 'Each result is saved separately.'}</li>
      <li className={ov.planId ? 'done' : diag ? 'next' : ''}><time>{ov.planId ? 'Recommend' : 'Then · Recommend'}</time>
        <b>{ov.planId ? 'Growth plan created.' : 'Build your growth plan.'}</b> {ov.planId ? `${prog.total} actions, ordered by priority.` : 'One roadmap from everything we know.'}</li>
      <li className={prog && (prog.inProgress || prog.completed) ? 'done' : ''}><time>Execute</time>
        <b>{prog && (prog.inProgress || prog.completed) ? `${prog.completed} done, ${prog.inProgress} in progress.` : 'Work through the roadmap.'}</b> You publish; we never change anything for you.</li>
      <li className={ov.planId && prog && prog.completed ? 'next' : ''}><time>Later · Measure</time>
        <b>Re-run the analyses after you publish.</b> We'll show what is fixed, what remains and what is new. We won't claim a change caused any result.</li>
    </ol>
  )
}

export default function Overview() {
  const profile = useProfile()
  const { loading, data: ov, error } = useLoad(() => api.get(`/profiles/${profile.id}/overview`), [profile.id])
  const { run, busy, error: runError } = useRun()
  if (loading) return <Loading />
  if (error) return <div className="wrap"><ErrorBox error={error} /></div>
  const base = `/app/profiles/${profile.id}`
  const b = profile.businessDetails || {}
  const launch = ov.path === 'LAUNCH'
  const channel = profile.channels?.[0]
  const verdict = ov.domain?.recommendation ? DOMAIN_VERDICT[ov.domain.recommendation] : null

  const startAudit = () => run('audit', `/profiles/${profile.id}/website-audits`, (r) => `${base}/audits/${r.auditId}`)
  const startChannel = () => run('channel', `/profiles/${profile.id}/channel-analyses`, (r) => `${base}/channels/${r.analysisId}`)
  const startPlan = () => run('plan', `/profiles/${profile.id}/growth-plans`, (r) => `${base}/plans/${r.planId}`)

  // The single most important next action: the top open roadmap item, otherwise whatever is missing.
  let next
  const hasAnalysis = ov.seo.status === 'ANALYSED' || ov.youtube.status === 'ANALYSED'
  if (ov.nextStep) next = { title: ov.nextStep.title, why: ov.nextStep.rationale, actions: [['See it in the roadmap', `${base}/plans/${ov.planId}`, true], ['Draft the wording', `${base}/studio?rec=${ov.nextStep.id}`]] }
  else if (!launch && profile.websiteUrl && ov.seo.status !== 'ANALYSED') next = { title: 'Run the website audit.', why: 'It reads your site, checks the basics and your domain, and shows what to fix first with the evidence quoted. Everything else builds on it.', cta: ['Run the audit', startAudit, 'audit'] }
  else if (!launch && !profile.websiteUrl) next = { title: 'Add your website address.', why: 'The website and AI-search checks need a website to read. Add it in your business details, or tell us you do not have one yet.', actions: [['Edit business details', `${base}/details`, true]] }
  else if (hasAnalysis || launch) next = { title: launch ? 'Build your launch roadmap.' : 'Build your growth plan.', why: launch ? 'A short ordered list of what to set up first, from what you told us.' : 'One roadmap from everything we have analysed, ordered by priority.', cta: ['Build the plan', startPlan, 'plan'] }
  else next = { title: 'Run your first analysis.', why: 'Start with whichever you have: your website or your YouTube channel.', actions: [['Edit business details', `${base}/details`, true]] }

  const analysedAny = [ov.seo, ov.geo, ov.youtube].some((a) => a.status === 'ANALYSED')
  const lead = ov.seo.status === 'ANALYSED' && ov.seo.note ? ov.seo.note
    : launch ? "You're getting ready to launch, which is a good time to get the basics right. Here's what you have so far and what to set up first."
    : analysedAny ? '' : "You haven't run an analysis yet. Start with the next step below." 
  const dot = (k) => (k === 'warn' ? 'caution' : k === 'gap' ? 'gap' : '')

  return (
    <div className="wrap" style={{ padding: '44px 0 60px' }}>
      <div className="row" style={{ marginBottom: 14 }}>
        <span className="chip chip-soft">{PATH_LABEL[ov.path]}</span>
        {b.category && <span className="chip chip-soft">{b.category}{profile.marketLocation ? ` · ${profile.marketLocation}` : ''}</span>}
        {b.launchTimeline && <span className="chip chip-soft">Opening: {b.launchTimeline}</span>}
        <Link className="chip chip-soft" to={`${base}/details`}>Edit details</Link>
      </div>
      <h1 className="title">Where <em>{profile.displayName}</em> stands online.</h1>
      {lead && <p className="lead" style={{ margin: '18px 0 40px', maxWidth: '64ch' }}>{lead}{ov.seo.status === 'ANALYSED' && ov.seo.note ? <> <AiTag text="AI-suggested summary" /></> : null}</p>}

      <section className="stats" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(190px, 1fr))', gap: 22 }} aria-label="Business details">
        <div className="stat"><b style={{ fontSize: 22 }}>{profile.websiteUrl ? profile.websiteUrl.replace(/^https?:\/\//, '') : 'Not yet'}</b><span>Website</span><Source label="USER_PROVIDED" /></div>
        <div className="stat"><b style={{ fontSize: 22 }}>{verdict || (ov.domain?.host ? 'Not reviewed yet' : 'Not yet')}</b><span>Domain verdict</span>{verdict ? <Source label="AI_DERIVED" /> : <Status state={ov.domain?.host ? 'NOT_YET_ANALYSED' : 'NOT_PROVIDED'} />}</div>
        <div className="stat"><b style={{ fontSize: 22 }}>{channel ? channel.channelUrl.replace(/^https?:\/\/(www\.)?youtube\.com\//, '') : 'None'}</b><span>YouTube channel</span><Source label="USER_PROVIDED" /></div>
        <div className="stat"><b style={{ fontSize: 22 }}>{profile.primaryGoal || 'Not set'}</b><span>Main goal</span><Source label="USER_PROVIDED" /></div>
      </section>

      <section className="panel panel-dark next" style={{ marginTop: 44 }}>
        <p className="eyebrow">Recommended next step{ov.nextStep ? ' · AI-suggested' : ''}</p>
        <h2>{next.title}</h2>
        <p className="why">{next.why}</p>
        <div className="row" style={{ marginTop: 20, gap: 12 }}>
          {next.cta && <button className="btn btn-primary" disabled={!!busy} onClick={next.cta[1]}>{busy === next.cta[2] ? 'Starting…' : next.cta[0]}</button>}
          {next.actions?.map(([label, to, primary]) => <Link key={label} className={primary ? 'btn btn-primary' : 'btn btn-quiet'} style={primary ? undefined : { color: '#fff' }} to={to}>{label}</Link>)}
        </div>
        <ErrorBox error={runError} />
      </section>

      <section style={{ marginTop: 56 }} aria-labelledby="dx">
        <div className="row spread" style={{ marginBottom: 16 }}><h2 id="dx" className="h2">Digital presence <em>diagnosis.</em></h2></div>
        <div className="areas">
          {[
            ['SEO & Website', ov.seo, `${base}/seo`, 'Open SEO & Website →', ov.seo.status === 'NOT_YET_ANALYSED' && profile.websiteUrl ? ['Run the audit', startAudit, 'audit'] : null],
            ['GEO', ov.geo, `${base}/geo`, 'Open GEO →', null],
            ['YouTube', ov.youtube, `${base}/youtube`, ov.youtube.status === 'NOT_PROVIDED' ? 'See whether it suits you →' : 'Open YouTube →', ov.youtube.status === 'NOT_YET_ANALYSED' ? ['Analyse the channel', startChannel, 'channel'] : null],
          ].map(([title, area, to, link, action]) => (
            <div className="area" key={title}>
              <div className="row spread"><h3>{title}</h3><Status state={area.status} /></div>
              {area.points.length > 0 ? <ul>{area.points.map((p, i) => <li key={i} className={dot(p.kind)}>{p.text}</li>)}</ul> : <p className="small muted">{area.note}</p>}
              {action && <div><button className="btn btn-sm btn-primary" disabled={!!busy} onClick={action[1]}>{busy === action[2] ? 'Starting…' : action[0]}</button></div>}
              <Link to={to}><b>{link}</b></Link>
            </div>))}
        </div>
        {ov.domain?.reviewed && <p className="small" style={{ marginTop: 18 }}><b>Domain:</b> {ov.domain.host} · {verdict === 'Keep it' ? 'it suits the business and no change is recommended.' : 'there may be a reason to look at it more closely. Nothing should change until you have weighed the risks.'} <Link to={`${base}/seo`}>See the assessment</Link></p>}
      </section>

      {ov.planId && ov.roadmap.length > 0 && (
        <section className="grid g-5-7" style={{ gap: 56, marginTop: 56, alignItems: 'start' }}>
          <div><h2 className="h3">Growth opportunities</h2><p className="small muted" style={{ margin: '4px 0 8px' }}>Each dot is a recommendation, placed by impact and effort.</p><OpportunityMap items={ov.roadmap} /></div>
          <div>
            <div className="row spread"><h2 className="h3">Growth roadmap <AiTag /></h2><Link to={`${base}/plans/${ov.planId}`} className="small"><b>Full roadmap →</b></Link></div>
            <ol className="road" style={{ marginTop: 8 }}>
              {ov.roadmap.slice(0, 5).map((r) => (
                <li key={r.id}><span className="n">{r.position}</span>
                  <div><h3>{r.title}</h3><div className="meta"><span className={`pri pri-${r.priority.toLowerCase()}`}>{cap(r.priority)} priority</span><span className="chip chip-soft">{AREA_LABEL[r.module]}</span><Source label={r.sourceLabel} /></div></div>
                  <span className="chip chip-soft">{r.status === 'COMPLETED' ? 'Done' : r.status === 'IN_PROGRESS' ? 'In progress' : 'Not started'}</span></li>))}
            </ol>
            <p className="small muted" style={{ marginTop: 10 }}>Priority comes from a fixed impact, effort and evidence rule. It is a way to order the work, not a forecast of results.</p>
          </div>
        </section>)}

      <section className="panel" style={{ marginTop: 56 }} aria-labelledby="dn">
        <div className="row spread"><h2 id="dn" className="h3">Domains &amp; names <span className="small muted" style={{ fontWeight: 500 }}>(optional)</span></h2><span className="src src-observed">Checked with GoDaddy</span></div>
        <p className="small muted" style={{ marginTop: 6, maxWidth: '64ch' }}>Check whether a domain is free, see its indicative price, and get name ideas that fit the business. You buy through GoDaddy; Growbie never purchases or changes a domain for you.</p>
        <div className="row" style={{ marginTop: 14, gap: 12 }}><Link className="btn btn-primary" to={`${base}/launch`}>Open domains &amp; names</Link></div>
      </section>

      <section className="grid g-2" style={{ gap: 56, marginTop: 56, alignItems: 'start' }}>
        <div><h2 className="h3" style={{ marginBottom: 14 }}>Activity trail</h2><Trail ov={ov} profile={profile} /></div>
        <div>
          <div className="row spread"><h2 className="h3">Reports &amp; history</h2><Link to={`${base}/reports`} className="small"><b>All reports →</b></Link></div>
          {ov.reports.length === 0 ? <p className="muted" style={{ marginTop: 10 }}>Nothing yet. Your first report will appear here, and each later one is saved separately.</p> :
            <table className="dtable" style={{ marginTop: 10 }}><thead><tr><th>Date</th><th>Report</th><th>Result</th></tr></thead>
              <tbody>{ov.reports.map((r) => <tr key={r.id}><td>{fmtDate(r.at)}</td><td>{r.area}</td><td>{r.result}</td></tr>)}</tbody></table>}
        </div>
      </section>
    </div>
  )
}
