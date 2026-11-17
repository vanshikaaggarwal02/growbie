import { Link } from 'react-router-dom'
import { api } from '../lib/api.js'
import { ErrorBox, Loading, useLoad } from '../components/ui.jsx'
import { useProfile } from '../components/ProfileShell.jsx'
import { PlanReport } from './Plan.jsx'
import { useRun } from '../lib/useRun.js'

/** The growth plan section opens on the latest roadmap, or offers to build one. */
export default function Plans() {
  const profile = useProfile()
  const base = `/app/profiles/${profile.id}`
  const { loading, data, error } = useLoad(() => api.get(`/profiles/${profile.id}/growth-plans`), [profile.id])
  const { run, busy, error: runError } = useRun()
  if (loading) return <Loading />
  if (error) return <div className="wrap"><ErrorBox error={error} /></div>
  const finished = (data || []).filter((g) => g.status === 'COMPLETED' || g.status === 'PARTIAL')
  const start = () => run('plan', `/profiles/${profile.id}/growth-plans`, (r) => `${base}/plans/${r.planId}`)
  if (finished.length === 0) {
    const running = (data || []).find((g) => g.status === 'QUEUED' || g.status === 'RUNNING')
    return (
      <div className="wrap" style={{ padding: '44px 0 60px' }}>
        <div className="row" style={{ marginBottom: 14 }}><span className="chip chip-soft">Growth Plan</span></div>
        <h1 className="title">One roadmap, <em>in priority order.</em></h1>
        <p className="lead" style={{ margin: '18px 0 28px', maxWidth: '62ch' }}>The plan combines everything we know: your answers, the website audit, GEO readiness, your domain and YouTube if you have a channel. {profile.profileType === 'NEW_BUSINESS' ? 'For a business that is still launching, it lists what to set up first.' : 'Run an analysis first if you have not yet, so the plan has evidence to work from.'}</p>
        {running ? <Link className="btn btn-primary" to={`${base}/plans/${running.planId}`}>A plan is being built — view it</Link>
          : <button className="btn btn-primary" onClick={start} disabled={!!busy}>{busy ? 'Starting…' : 'Build my plan'}</button>}
        <ErrorBox error={runError} />
      </div>)
  }
  return (
    <>
      <div className="wrap" style={{ paddingTop: 28 }}><div className="row" style={{ gap: 10 }}>
        <button className="btn btn-sm btn-primary" onClick={start} disabled={!!busy}>{busy ? 'Starting…' : 'Build a new plan'}</button>
        <Link className="btn btn-sm" to={`${base}/reports?tab=plans`}>Earlier plans</Link></div><ErrorBox error={runError} /></div>
      <PlanReport planId={finished[0].planId} />
    </>
  )
}
