import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api } from '../lib/api.js'
import { ErrorBox, Loading, Thinking, useLoad, usePoll } from '../components/ui.jsx'
import { AiTag, Source, fmtDate, fmtNum, splitSummary } from '../components/bits.jsx'

const round1 = (n) => (n == null ? null : Math.round(n * 10) / 10)
const compact = (n) => new Intl.NumberFormat(undefined, { notation: 'compact', maximumFractionDigits: 2 }).format(n)
const dur = (s) => (s == null ? 'unavailable' : s >= 3600 ? `${Math.floor(s / 3600)}h ${Math.round((s % 3600) / 60)}m` : `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`)
const FORMAT_NAME = { SHORT: 'Short', LONG_FORM: 'Long-form', MID_LENGTH: '61 to 180 s' }

/** A value, or a struck-through "unavailable" tag: a missing number is never shown as zero. */
function Val({ v, unit = '', digits }) {
  if (v == null) return <span className="src src-na">Unavailable</span>
  const n = digits != null ? Number(v).toLocaleString(undefined, { maximumFractionDigits: digits }) : Number(v).toLocaleString()
  return <b>{n}{unit}</b>
}

function FormatCard({ title, tag, count, sampleCount, f, best, weakest, basisNote }) {
  return (
    <section className="panel" aria-label={title}>
      <div className="row spread"><h3 className="h3">{title}</h3><span className="chip chip-soft">{tag}</span></div>
      <dl className="kv" style={{ marginTop: 10 }}>
        <dt>On the whole channel</dt><dd>{count == null ? <span className="src src-na">Unavailable</span> : <><b>{fmtNum(count)}</b> video{count === 1 ? '' : 's'}</>}</dd>
        <dt>In this sample</dt><dd><b>{sampleCount}</b> video{sampleCount === 1 ? '' : 's'}</dd>
        <dt>Median views</dt><dd><Val v={f?.medianViews} /></dd>
        <dt>Median views per day</dt><dd><Val v={f?.medianViewsPerDay != null ? Math.round(f.medianViewsPerDay) : null} /></dd>
        <dt>Likes per 100 views</dt><dd><Val v={f?.likesPer100Views} digits={1} /></dd>
        <dt>Comments per 100 views</dt><dd><Val v={f?.commentsPer100Views} digits={2} /></dd>
        <dt>Best by views per day</dt><dd>{best ? <>{best.title} <span className="muted">({Math.round(best.viewsPerDay).toLocaleString()}/day)</span></> : <span className="src src-na">Not enough data</span>}</dd>
        <dt>Weakest by views per day</dt><dd>{weakest ? <>{weakest.title} <span className="muted">({Math.round(weakest.viewsPerDay).toLocaleString()}/day)</span></> : <span className="src src-na">Not enough data</span>}</dd>
      </dl>
      {sampleCount === 0 && <p className="small muted">None of the videos in this sample are this format{basisNote}.</p>}
    </section>
  )
}

function CadenceStrip({ videos, cad }) {
  const n = cad?.videos || 0
  const recent = [...videos].sort((a, b) => new Date(b.publishedAt) - new Date(a.publishedAt)).slice(0, n)
  if (recent.length < 2) return null
  const now = Date.now()
  const ago = recent.map((v) => (now - new Date(v.publishedAt)) / 86400000)
  const span = Math.max(...ago, 1)
  const x = (d) => 630 - (d / span) * 610
  return (
    <svg viewBox="0 0 640 110" role="img" aria-label={`${recent.length} uploads over the last ${Math.round(span)} days`} style={{ marginTop: 12, width: '100%', height: 'auto' }}>
      <line x1="10" y1="70" x2="630" y2="70" stroke="#17261C" strokeWidth="2" />
      {[0, 0.5, 1].map((t) => <line key={t} x1={630 - t * 610} y1="70" x2={630 - t * 610} y2="80" stroke="#17261C" strokeWidth="1.5" />)}
      <g fontSize="12" fontWeight="700" fill="#55675A" fontFamily="Hanken Grotesk, sans-serif">
        <text x="10" y="98" textAnchor="start">{Math.round(span)} days ago</text><text x="630" y="98" textAnchor="end">today</text></g>
      {recent.map((v, i) => <circle key={v.videoId} cx={x(ago[i])} cy={v.format === 'SHORT' ? 48 : 58} r="6.5" fill={v.format === 'SHORT' ? '#C8F169' : '#17261C'} stroke="#17261C" strokeWidth="1.5"><title>{v.title}</title></circle>)}
      <g fontSize="11" fontWeight="700" fill="#44564A" fontFamily="Hanken Grotesk, sans-serif"><circle cx="18" cy="16" r="5.5" fill="#C8F169" stroke="#17261C" strokeWidth="1.5" /><text x="28" y="20">Short</text><circle cx="86" cy="16" r="5.5" fill="#17261C" /><text x="96" y="20">Long-form</text></g>
    </svg>
  )
}

export function ChannelReport({ id, analysisId }) {
  const nav = useNavigate()
  const { loading, data, error, reload } = useLoad(() => api.get(`/channel-analyses/${analysisId}`), [analysisId])
  const running = data && (data.status === 'QUEUED' || data.status === 'RUNNING')
  usePoll(() => api.get(`/channel-analyses/${analysisId}`), running, reload)
  const [busy, setBusy] = useState(false)
  const [rerunError, setRerunError] = useState(null)
  const rerun = async () => {
    setBusy(true); setRerunError(null)
    try { const r = await api.post(`/profiles/${id}/channel-analyses`); nav(`/app/profiles/${id}/channels/${r.analysisId}`) }
    catch (e) { setRerunError(e) } finally { setBusy(false) }
  }

  if (loading && !data) return <Loading />
  if (error) return <div className="wrap"><ErrorBox error={error} /></div>
  const a = data
  if (running) return <Thinking kind="channel" title="Reading the channel's public data" />
  if (a.status === 'FAILED') return <div className="wrap state"><h1 className="h2">The analysis didn't finish</h1>
    <ErrorBox error={{ message: a.warnings?.[0] || 'Something went wrong while reading the channel.' }} />
    <p style={{ marginTop: 16 }} className="row"><button className="btn btn-primary" onClick={rerun} disabled={busy}>Try again</button><Link className="btn" to={`/app/profiles/${id}/channels`}>Analysis history</Link></p>
    <ErrorBox error={rerunError} /></div>

  const ch = a.channel || {}, st = a.stats || {}, ai = a.analysis, cad = st.cadence || {}
  const observed = st.formatBasis === 'YOUTUBE_SHORTS_LIST'
  const fmt = (k) => (st.formats || []).find((f) => f.format === k)
  // Ranked per format from the videos themselves. Videos under 3 days old are left out: they have had no time to collect views.
  const ranked = (k) => a.videos
    .filter((v) => v.format === k && v.views != null && (Date.now() - new Date(v.publishedAt)) / 86400000 >= 3)
    .map((v) => ({ title: v.title, viewsPerDay: v.views / Math.max(1, (Date.now() - new Date(v.publishedAt)) / 86400000) }))
    .sort((x, y) => y.viewsPerDay - x.viewsPerDay)
  const bestOf = (k) => ranked(k)[0]
  const weakestOf = (k) => { const r = ranked(k); return r.length > 1 ? r[r.length - 1] : undefined }
  const head = splitSummary(ai?.summary || ch.title || '')

  return (
    <div className="wrap" style={{ padding: '44px 0 60px' }}>
      <div className="row" style={{ marginBottom: 14 }}>
        <span className="chip chip-soft">YouTube creator</span>{ch.handle && <span className="chip chip-soft">{ch.handle}</span>}
        <span className="chip chip-soft">{st.videosAnalysed} recent videos · {fmtDate(a.createdAt)}</span>
        {a.status === 'PARTIAL' && <span className="chip chip-lime">Written analysis unavailable</span>}
      </div>
      <h1 className="display" style={{ fontSize: 'clamp(30px,4vw,50px)', maxWidth: '24ch', margin: '0 0 18px' }}>{head.head}</h1>
      {head.rest && <p className="lead" style={{ maxWidth: '70ch', margin: '0 0 8px' }}>{head.rest}</p>}
      <div className="row" style={{ marginTop: 12, gap: 10 }}><Link className="btn btn-sm" to={`/app/profiles/${id}/channels`}>Analysis history</Link>
        <button className="btn btn-sm" onClick={rerun} disabled={busy}>{busy ? 'Starting…' : a.status === 'PARTIAL' ? 'Run again for the written analysis' : 'Run a new analysis'}</button></div>
      <ErrorBox error={rerunError} />
      {a.warnings?.length > 0 && <div className="callout" style={{ margin: '16px 0' }}>{a.warnings.join(' ')}</div>}

      <section aria-labelledby="pulse" className="stack" style={{ marginTop: 28 }}>
        <div className="row spread"><h2 id="pulse" className="h3">Channel pulse</h2><span className="src src-observed">Observed · public YouTube data</span></div>
        <div className="stats">
          <div className="stat"><b>{ch.subscribersHidden ? 'Hidden' : ch.subscribers == null ? 'Unavailable' : `≈${compact(ch.subscribers)}`}</b><span>{ch.subscribersHidden ? 'The channel hides its subscriber count.' : 'Subscribers. YouTube rounds this number.'}</span></div>
          <div className="stat"><b>{compact(ch.totalViews)}</b><span>Total views</span></div>
          <div className="stat"><b>{fmtNum(ch.videoCount)}</b><span>Public videos, including Shorts</span></div>
          <div className="stat"><b>{round1(cad.uploadsPerWeek) ?? 'n/a'}</b><span>Uploads per week (Shorts and videos together)</span></div>
          <div className="stat"><b>{round1(cad.daysSinceLatest) ?? 'n/a'}</b><span>Days since the latest upload</span></div>
        </div>
      </section>

      <section style={{ marginTop: 48 }} aria-labelledby="fmt-h">
        <div className="row spread"><h2 id="fmt-h" className="h3">Shorts and long-form videos</h2>
          <span className={`src ${observed ? 'src-observed' : 'src-ai'}`}>{observed ? "Observed · YouTube's Shorts list" : 'Estimated from video length'}</span></div>
        <p className="small muted" style={{ margin: '6px 0 14px', maxWidth: '75ch' }}>
          {observed ? "YouTube keeps a separate list of a channel's Shorts, so each video below is a Short or a long-form video for certain. Both are compared on the same measures."
            : "YouTube's Shorts list wasn't available for this channel, so formats are guessed from length: up to 60 seconds counts as a Short. Videos from 61 to 180 seconds could be either."}
          {' '}Views per day divides views by age, so recent videos aren't unfairly low. It is a ratio, not a verdict on quality.</p>
        {observed ? (
          <div className="grid g-2" style={{ gap: 24, alignItems: 'start' }}>
            <FormatCard title="Shorts" tag="Short" count={ch.shortsCount} sampleCount={fmt('SHORT')?.videos ?? 0} f={fmt('SHORT')} best={bestOf('SHORT')} weakest={weakestOf('SHORT')} basisNote="" />
            <FormatCard title="Long-form videos" tag="Long-form" count={ch.longFormCount} sampleCount={fmt('LONG_FORM')?.videos ?? 0} f={fmt('LONG_FORM')} best={bestOf('LONG_FORM')} weakest={weakestOf('LONG_FORM')} basisNote="" />
          </div>) : (
          <div className="grid g-3" style={{ gap: 20, alignItems: 'start' }}>
            {['SHORT', 'MID_LENGTH', 'LONG_FORM'].map((k) => <FormatCard key={k} title={{ SHORT: '60 seconds or less', MID_LENGTH: '61 to 180 seconds', LONG_FORM: 'Over 180 seconds' }[k]}
              tag="Estimate" count={undefined} sampleCount={fmt(k)?.videos ?? 0} f={fmt(k)} best={bestOf(k)} weakest={weakestOf(k)} basisNote=" by length" />)}
          </div>)}
        <p className="small muted" style={{ marginTop: 10 }}>Likes and comments are shown as unavailable when YouTube didn't return them (for example when the owner hides likes). They are never counted as zero. Watch time, retention and click-through need the owner's own YouTube analytics and are not available here.</p>
      </section>

      <section style={{ marginTop: 48 }}>
        <h2 className="h3">Upload rhythm</h2>
        <p className="small muted" style={{ marginTop: 6 }}>{cad.medianGapDays != null ? `Median gap ${round1(cad.medianGapDays)} days · longest ${round1(cad.longestGapDays)} days · shortest ${round1(cad.shortestGapDays)} days · across the ${cad.videos} most recent uploads.` : 'Not enough uploads to measure a rhythm.'}</p>
        <CadenceStrip videos={a.videos} cad={cad} />
      </section>

      {ai && <>
        {ai.themes?.length > 0 && <section style={{ marginTop: 48 }}><div className="row spread" style={{ marginBottom: 14 }}><h2 className="h3">What the channel keeps coming back to</h2><AiTag /></div>
          <div className="themes">{ai.themes.map((t, i) => <div className="theme" key={i}><h3>{t.name}</h3><p>{t.description}</p>{t.exampleTitles?.[0] && <p className="eg">"{t.exampleTitles[0]}"</p>}</div>)}</div></section>}
        <section className="grid g-2" style={{ gap: 56, marginTop: 48, alignItems: 'start' }}>
          <div className="stack"><div className="row spread"><h2 className="h3">Where to improve</h2><AiTag /></div>
            {(ai.gaps || []).map((g, i) => (
              <article key={i} className="receipt medium"><div className="receipt-body">
                <div className="receipt-head"><Source label={g.basis} /></div>
                <h3 className="receipt-title">{g.title}</h3>
                <div className="receipt-quote"><span className="where">Evidence</span><span className="quote"><span className="hl">{g.evidence}</span></span></div>
                <p className="small">{g.reasoning} {g.suggestion}</p></div></article>))}
          </div>
          <div><div className="row spread"><h2 className="h3">Video ideas <AiTag /></h2><Link className="btn btn-sm btn-primary" to={`/app/profiles/${id}/studio`}>Write titles and hooks</Link></div>
            {(ai.ideas || []).map((d, i) => <div className="idea" key={i}><span className={`chip ${d.format === 'SHORT' ? 'chip-lime' : ''}`}>{d.format === 'SHORT' ? 'Short' : 'Long'}</span><div><h3>{d.title}</h3><p>Hook: {d.hook}</p></div></div>)}</div>
        </section>
        {ai.calendar?.length > 0 && <section style={{ marginTop: 48 }}><div className="row spread" style={{ marginBottom: 12 }}><h2 className="h3">Your next 7 days</h2><AiTag /></div>
          <div className="week">{ai.calendar.map((d) => <div key={d.day}><h4>Day {d.day}</h4>
            <span className={`chip ${d.format === 'SHORT' ? 'chip-lime' : 'chip-soft'}`}>{d.format === 'SHORT' ? 'Short' : d.format === 'LONG_FORM' ? 'Long' : 'Plan'}</span><p style={{ marginTop: 8 }}>{d.task}</p></div>)}</div></section>}
      </>}

      <section style={{ marginTop: 48 }}>
        <h2 className="h3" style={{ marginBottom: 10 }}>Every video in this sample</h2>
        <div className="scroll"><table className="hist">
          <thead><tr><th>Video</th><th>Format</th><th>Published</th><th>Length</th><th>Views</th><th>Likes</th><th>Comments</th></tr></thead>
          <tbody>{a.videos.map((v) => (
            <tr key={v.videoId}><td><a href={`https://www.youtube.com/watch?v=${v.videoId}`} target="_blank" rel="noreferrer">{v.title}</a></td>
              <td><span className={`chip ${v.format === 'SHORT' ? 'chip-lime' : 'chip-soft'}`}>{FORMAT_NAME[v.format] || 'Unknown'}</span></td>
              <td>{fmtDate(v.publishedAt)}</td><td>{dur(v.durationSeconds)}</td>
              <td><Val v={v.views} /></td><td><Val v={v.likes} /></td><td><Val v={v.comments} /></td></tr>))}</tbody>
        </table></div>
      </section>

      <section className="panel panel-sage" style={{ marginTop: 48 }}>
        <h2 className="h3">What public data can't tell us</h2>
        <p className="small" style={{ marginTop: 6, maxWidth: '60ch' }}>These need access to your own YouTube analytics. Until you provide them, we show them as unavailable and never guess.</p>
        <div className="na-grid">{['Watch time', 'Audience retention', 'Impressions', 'Click-through rate', 'Audience demographics', 'Traffic sources', 'Revenue'].map((x) => <span key={x} className="src src-na">{x}</span>)}</div>
        {a.limitations?.length > 0 && <ul style={{ margin: '12px 0 0 18px' }}>{a.limitations.map((l, i) => <li key={i} className="small">{l}</li>)}</ul>}
      </section>
    </div>
  )
}

export default function Channel() {
  const { id, analysisId } = useParams()
  return <ChannelReport id={id} analysisId={analysisId} />
}
