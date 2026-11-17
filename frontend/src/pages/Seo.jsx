import { Link } from 'react-router-dom'
import { api } from '../lib/api.js'
import { ErrorBox, Loading, useLoad } from '../components/ui.jsx'
import { useProfile } from '../components/ProfileShell.jsx'
import { Status } from '../components/bits.jsx'
import { AuditReport } from './Audit.jsx'
import { useRun } from '../lib/useRun.js'

/** A business with no website yet: general guidance, not an audit and not a failure. */
function NoWebsite({ profile }) {
  const base = `/app/profiles/${profile.id}`
  return (
    <div className="wrap" style={{ padding: '44px 0 60px' }}>
      <div className="row" style={{ marginBottom: 14 }}><span className="chip chip-soft">SEO &amp; Website</span><Status state="NOT_PROVIDED" /></div>
      <h1 className="title">No website yet, and that's <em>not a failure.</em></h1>
      <p className="lead" style={{ margin: '18px 0 40px', maxWidth: '62ch' }}>There's nothing to audit, so here is what a business like {profile.displayName} usually needs to be found. This is general guidance, not a measured result about your business.</p>
      <div className="grid g-8-4" style={{ gap: 48, alignItems: 'start' }}>
        <div className="stack" style={{ gap: 40 }}>
          <section><h2 className="h3">The minimum digital foundation</h2>
            <ul className="tick" style={{ marginTop: 10 }}>
              <li className="gap">A domain name that matches the business<small>Optional to buy now. See the ideas on the right.</small></li>
              <li className="gap">A simple site: what you offer, who it's for, where you are, how to get in touch or order</li>
              <li className="gap">A Google Business Profile, if customers find you locally<small>Needs a real address or service area</small></li>
              <li className="gap">A clear page title, description and one main heading on the homepage</li>
              <li className="gap">A contact or ordering path that works on a phone</li></ul>
            <p className="small muted" style={{ marginTop: 8 }}>Open items are shown as open, not failed.</p></section>
          <section><h2 className="h3">What the first page should say</h2>
            <dl className="kv" style={{ marginTop: 10 }}>
              <dt>Heading</dt><dd>Say what the business is and where, in plain words.</dd>
              <dt>First paragraph</dt><dd>Who it is for and what a first-time customer gets.</dd>
              <dt>Details</dt><dd>Prices, hours, address and anything you can stand behind. Only claims you can support.</dd></dl></section>
          <section><h2 className="h3">Before you launch</h2><ul className="stack-s" style={{ marginTop: 8 }}>
            <li>Use your business name and what you sell in the page title.</li><li>Put your location in the text, not only in an image.</li><li>Make sure every page can be reached from the homepage.</li></ul></section>
        </div>
        <aside className="stack" style={{ gap: 20 }}>
          <div className="panel"><h2 className="h3">Domain ideas</h2><p className="small muted" style={{ margin: '6px 0 10px' }}>Only if you want them. Availability and prices come from GoDaddy with the date checked. You buy through GoDaddy.</p><Link className="btn btn-primary" to={`${base}/launch`}>Find a domain</Link></div>
          <div className="panel"><h2 className="h3">Already have a site?</h2><p className="small muted" style={{ margin: '6px 0 10px' }}>Add its address and we'll audit it.</p><Link className="btn" to={`${base}/details`}>Add website address</Link></div>
        </aside>
      </div>
    </div>
  )
}

export default function Seo() {
  const profile = useProfile()
  const base = `/app/profiles/${profile.id}`
  const { loading, data, error } = useLoad(() => api.get(`/profiles/${profile.id}/website-audits`), [profile.id])
  const { run, busy, error: runError } = useRun()
  if (loading) return <Loading />
  if (error) return <div className="wrap"><ErrorBox error={error} /></div>
  const finished = (data || []).filter((a) => a.status === 'COMPLETED' || a.status === 'PARTIAL')
  if (!profile.websiteUrl && finished.length === 0) return <NoWebsite profile={profile} />
  const start = () => run('audit', `/profiles/${profile.id}/website-audits`, (r) => `${base}/audits/${r.auditId}`)
  if (finished.length === 0) {
    const running = (data || []).find((a) => a.status === 'QUEUED' || a.status === 'RUNNING')
    return (
      <div className="wrap" style={{ padding: '44px 0 60px' }}>
        <div className="row" style={{ marginBottom: 14 }}><span className="chip chip-soft">SEO &amp; Website</span><Status state="NOT_YET_ANALYSED" /></div>
        <h1 className="title">Let's see how <em>{profile.websiteUrl.replace(/^https?:\/\//, '')}</em> is doing.</h1>
        <p className="lead" style={{ margin: '18px 0 28px', maxWidth: '62ch' }}>The audit reads up to 10 pages, runs plain checks and Google PageSpeed, assesses your domain, and checks how ready the site is for AI search. Each result quotes its evidence.</p>
        {running ? <Link className="btn btn-primary" to={`${base}/audits/${running.auditId}`}>An audit is running — view it</Link>
          : <button className="btn btn-primary" onClick={start} disabled={!!busy}>{busy ? 'Starting…' : 'Run the audit'}</button>}
        <ErrorBox error={runError} />
      </div>
    )
  }
  return (
    <>
      <div className="wrap" style={{ paddingTop: 28 }}>
        <div className="row" style={{ gap: 10 }}>
          <button className="btn btn-sm btn-primary" onClick={start} disabled={!!busy}>{busy ? 'Starting…' : 'Run a new audit'}</button>
          <Link className="btn btn-sm" to={`${base}/reports`}>Audit history</Link>
        </div>
        <ErrorBox error={runError} />
      </div>
      <AuditReport id={profile.id} auditId={finished[0].auditId} />
    </>
  )
}
