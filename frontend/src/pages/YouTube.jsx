import { useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../lib/api.js'
import { ErrorBox, Loading, Thinking, useLoad } from '../components/ui.jsx'
import { useProfile } from '../components/ProfileShell.jsx'
import { Status } from '../components/bits.jsx'
import { ChannelReport } from './Channel.jsx'
import { useRun } from '../lib/useRun.js'

const CHOICES = [['START_NOW', 'Start now', 'Only if you already have time and footage.'], ['CONSIDER_LATER', 'Consider it later', 'Once the basics are in place.'], ['FOCUS_ELSEWHERE_FIRST', 'Focus elsewhere first', 'If something more basic is missing.']]

/** No channel: YouTube is optional, so say whether it would suit this business instead of marking it failed. */
function NoChannel({ profile }) {
  const base = `/app/profiles/${profile.id}`
  const saved = useLoad(() => api.get(`/profiles/${profile.id}/youtube-relevance`), [profile.id])
  const [result, setResult] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  const r = result || saved.data
  const assess = async () => { setBusy(true); setError(null); try { setResult(await api.post(`/profiles/${profile.id}/youtube-relevance`)) } catch (e) { setError(e) } finally { setBusy(false) } }
  return (
    <div className="wrap" style={{ padding: '44px 0 60px' }}>
      <div className="row" style={{ marginBottom: 14 }}><span className="chip chip-soft">YouTube</span><Status state="NOT_PROVIDED" /></div>
      <h1 className="title">Should <em>{profile.displayName}</em> be on YouTube?</h1>
      <p className="lead" style={{ margin: '18px 0 36px', maxWidth: '62ch' }}>YouTube is optional. Nobody is told to start a channel by default; we look at what the business is, who it's for and what you have to work with.</p>
      {saved.loading ? <Loading /> : !r ? (
        <div className="panel" style={{ maxWidth: 720 }}>
          <h2 className="h3">Get an assessment</h2><p className="small muted" style={{ margin: '6px 0 12px' }}>One short AI judgement from the answers you gave us. It is not measured data. It is saved and reused for seven days.</p>
          <button className="btn btn-primary" onClick={assess} disabled={busy}>{busy ? 'Thinking…' : 'Assess whether YouTube suits us'}</button>{busy && <Thinking kind="youtube" compact />}<ErrorBox error={error} />
        </div>) : (
        <div className="grid g-8-4" style={{ gap: 48, alignItems: 'start' }}>
          <div className="stack" style={{ gap: 32 }}>
            <section><div className="row spread"><h2 className="h3">Assessment</h2><span className="src src-ai">AI-suggested · from what you told us</span></div>
              <div className="grid g-3" style={{ gap: 14, marginTop: 12 }}>{CHOICES.map(([k, t, d]) => (
                <div key={k} className="panel" style={r.recommendation === k ? { background: 'var(--lime)', borderWidth: 2 } : undefined}>
                  <span className={`chip ${r.recommendation === k ? 'chip-dark' : 'chip-soft'}`}>{r.recommendation === k ? 'Suggested' : 'Option'}</span>
                  <h3 className="h3" style={{ marginTop: 8 }}>{t}</h3><p className="small muted" style={{ marginTop: 4 }}>{d}</p></div>))}</div>
              <p style={{ marginTop: 16, fontSize: 17 }}>{r.summary}</p></section>
            <section><h2 className="h3">Why</h2><ul className="stack-s" style={{ marginTop: 8 }}>{r.reasons.map((x, i) => <li key={i}>{x}</li>)}</ul></section>
            {r.firstSteps?.length > 0 && <section><h2 className="h3">First steps</h2><ol style={{ margin: '8px 0 0 18px' }}>{r.firstSteps.map((x, i) => <li key={i} style={{ marginTop: 4 }}>{x}</li>)}</ol></section>}
          </div>
          <aside className="stack" style={{ gap: 20 }}>
            {r.limitations?.length > 0 && <div className="callout"><b>Limits.</b><ul style={{ margin: '6px 0 0 18px' }}>{r.limitations.map((x, i) => <li key={i} className="small">{x}</li>)}</ul></div>}
            <div className="panel"><h2 className="h3">Already have a channel?</h2><p className="small muted" style={{ margin: '6px 0 10px' }}>Add it and we'll analyse it.</p><Link className="btn" to={`${base}/details`}>Add a YouTube channel</Link></div>
          </aside>
        </div>)}
    </div>
  )
}

export default function YouTube() {
  const profile = useProfile()
  const base = `/app/profiles/${profile.id}`
  const hasChannel = (profile.channels || []).length > 0
  const { loading, data, error } = useLoad(() => (hasChannel ? api.get(`/profiles/${profile.id}/channel-analyses`) : Promise.resolve([])), [profile.id, hasChannel])
  const { run, busy, error: runError } = useRun()
  if (!hasChannel) return <NoChannel profile={profile} />
  if (loading) return <Loading />
  if (error) return <div className="wrap"><ErrorBox error={error} /></div>
  const finished = (data || []).filter((a) => a.status === 'COMPLETED' || a.status === 'PARTIAL')
  const start = () => run('channel', `/profiles/${profile.id}/channel-analyses`, (r) => `${base}/channels/${r.analysisId}`)
  if (finished.length === 0) {
    return (
      <div className="wrap" style={{ padding: '44px 0 60px' }}>
        <div className="row" style={{ marginBottom: 14 }}><span className="chip chip-soft">YouTube</span><Status state="NOT_YET_ANALYSED" /></div>
        <h1 className="title">Let's look at <em>your channel.</em></h1>
        <p className="lead" style={{ margin: '18px 0 28px', maxWidth: '62ch' }}>We read your channel's public data: recent videos, Shorts and long-form separately, how often you post and what you cover. We can't see watch time or retention unless you authorise it.</p>
        <button className="btn btn-primary" onClick={start} disabled={!!busy}>{busy ? 'Starting…' : 'Analyse the channel'}</button><ErrorBox error={runError} />
      </div>)
  }
  return <ChannelReport id={profile.id} analysisId={finished[0].analysisId} />
}
