import { useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { api } from '../lib/api.js'
import { ErrorBox, Loading, Thinking, useLoad } from '../components/ui.jsx'
import { AiTag } from '../components/bits.jsx'

const TYPES = {
  HOMEPAGE_COPY: { label: 'Homepage copy', biz: true, page: true },
  META_TAGS: { label: 'Meta title and description', biz: true, page: true },
  PRODUCT_DESCRIPTION: { label: 'Product description', biz: true, product: true },
  YT_TITLE: { label: 'Video titles', yt: true, topic: true },
  YT_DESCRIPTION: { label: 'Video description', yt: true, topic: true },
  YT_HOOK: { label: 'Hooks', yt: true, topic: true },
  YT_SCRIPT: { label: 'Script outline', yt: true, topic: true },
}

export default function Studio() {
  const { id } = useParams()
  const [qs] = useSearchParams()
  const profile = useLoad(() => api.get(`/profiles/${id}`), [id])
  const [type, setType] = useState(null)
  const [f, setF] = useState({ tone: '', topic: '', productName: '', pageUrl: '', originalText: '' })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  const [result, setResult] = useState(null)
  const [pick, setPick] = useState(0)
  const [copied, setCopied] = useState(false)

  if (profile.loading) return <Loading />
  if (profile.error) return <div className="wrap"><ErrorBox error={profile.error} /></div>
  const legacyCreator = profile.data.profileType === 'YOUTUBE_CREATOR'
  const hasChannel = legacyCreator || (profile.data.channels || []).length > 0
  const types = Object.entries(TYPES).filter(([, t]) => (legacyCreator ? t.yt : t.biz || (hasChannel && t.yt)))
  const cur = type || types[0][0]
  const spec = TYPES[cur]
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value })

  const generate = async (e) => {
    e.preventDefault()
    setBusy(true); setError(null); setCopied(false)
    const body = { contentType: cur, tone: f.tone.trim() || null, topic: f.topic.trim() || null,
      productName: f.productName.trim() || null, pageUrl: f.pageUrl.trim() || null,
      originalText: f.originalText.trim() || null, recommendationId: qs.get('rec') || null }
    try { const r = await api.post(`/profiles/${id}/generated-content`, body); setResult(r); setPick(r.selectedVariant ? r.selectedVariant - 1 : 0) }
    catch (err) { setError(err) } finally { setBusy(false) }
  }

  const variants = result?.variants || []
  const v = variants[pick]
  const text = v ? v.parts.map((p) => `${p.name}: ${p.text}`).join('\n') : ''
  const approve = async () => {
    setError(null)
    try { setResult(await api.patch(`/generated-content/${result.id}`, { status: 'APPROVED', selectedVariant: pick + 1 })) } catch (err) { setError(err) }
  }
  const copy = async () => { await navigator.clipboard.writeText(v.parts.map((p) => p.text).join('\n')); setCopied(true) }

  return (
    <div className="wrap" style={{ padding: '44px 0 60px' }}>
      <div className="row" style={{ marginBottom: 14 }}><span className="chip chip-soft">Content studio</span>{qs.get('rec') && <span className="chip chip-soft">Carrying out a plan item</span>}</div>
      <h1 className="display" style={{ fontSize: 'clamp(30px,4vw,50px)', maxWidth: '20ch', margin: '0 0 14px' }}>Ready-to-use wording, <em>from your own facts.</em></h1>
      <p className="lead" style={{ margin: '0 0 28px' }}>Pick a version, change anything you like, and paste it in yourself. Growbie never publishes for you.</p>

      <nav className="type-tabs" aria-label="Content type">
        {types.map(([k, t]) => <button key={k} className={k === cur ? 'on' : ''} aria-pressed={k === cur} onClick={() => { setType(k); setResult(null) }}>{t.label}</button>)}
      </nav>

      <form onSubmit={generate} style={{ marginTop: 24, maxWidth: 760 }}>
        <div className="grid g-2" style={{ gap: 16 }}>
          <div className="field" style={{ marginTop: 0 }}><label htmlFor="tone">Tone</label><input id="tone" className="input" maxLength={80} placeholder="for example: calm and clear" value={f.tone} onChange={set('tone')} /></div>
          {spec.page && <div className="field" style={{ marginTop: 0 }}><label htmlFor="pu">Page address</label><input id="pu" className="input" placeholder="https://" value={f.pageUrl} onChange={set('pageUrl')} /></div>}
          {spec.product && <div className="field" style={{ marginTop: 0 }}><label htmlFor="pn">Product name</label><input id="pn" className="input" required maxLength={120} value={f.productName} onChange={set('productName')} /></div>}
          {spec.topic && <div className="field" style={{ marginTop: 0 }}><label htmlFor="tp">Video topic</label><input id="tp" className="input" required maxLength={1000} value={f.topic} onChange={set('topic')} /></div>}
        </div>
        <ErrorBox error={error} />
        <button className="btn btn-primary" style={{ marginTop: 18 }} disabled={busy}>{busy ? 'Writing…' : result ? 'Write again' : 'Write it'}</button>
        {busy && <Thinking kind="studio" compact />}
      </form>

      {result && v && (
        <section style={{ marginTop: 36 }} aria-label="Result">
          <div className="row spread" style={{ marginBottom: 12 }}>
            <div className="tabs" role="tablist" aria-label="Versions">
              {variants.map((x, i) => <button key={i} role="tab" aria-selected={i === pick} className={i === pick ? 'on' : ''} onClick={() => setPick(i)}>{i + 1} · {x.label}</button>)}
            </div>
            <span className="row" style={{ gap: 8 }}><AiTag /><span className="chip chip-soft">{result.status === 'APPROVED' ? 'Approved' : 'Draft'}</span></span>
          </div>
          <div className="ba">
            <div className="now"><h4>Now on your site</h4>{result.original ? <p className="desc"><del>{result.original}</del></p> : <p className="desc muted">Nothing to compare yet.</p>}</div>
            <div className="new"><h4>Suggested</h4>{v.parts.map((p) => <div key={p.name}><p className="field" style={{ margin: 0 }}>{p.name}</p><p className="desc"><span className="hl">{p.text}</span></p></div>)}</div>
          </div>
          {v.reason && <div className="panel panel-sage" style={{ marginTop: 14 }}><p className="eyebrow" style={{ color: 'var(--ink-2)' }}>Why this wording</p><p style={{ marginTop: 6 }}>{v.reason}</p></div>}
          <div className="row" style={{ marginTop: 18, gap: 12 }}>
            <button className="btn btn-primary" onClick={approve}>Approve this version</button>
            <button className="btn" onClick={copy}>{copied ? 'Copied' : 'Copy to clipboard'}</button>
            <span className="small muted">Nothing is published. You paste it into your own site.</span>
          </div>
          {result.notes?.length > 0 && <div style={{ marginTop: 24 }}><h2 className="h3" style={{ marginBottom: 8 }}>Before you publish, check</h2>
            <ul className="checklist2">{result.notes.map((n, i) => <li key={i}>{n}</li>)}</ul></div>}
          <p style={{ marginTop: 24 }}><Link className="btn btn-quiet" to={`/app/profiles/${id}`}>Back to workspace</Link></p>
        </section>)}
    </div>
  )
}
