import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../lib/api.js'
import { ErrorBox } from '../components/ui.jsx'
import { useProfile } from '../components/ProfileShell.jsx'

function Field({ id, label, hint, children }) {
  return <div className="field"><label htmlFor={id}>{label}</label>{children}{hint && <p className="hint">{hint}</p>}</div>
}

/** Add or change what Growbie knows. A blank field leaves the saved value as it is. */
export default function Details() {
  const p = useProfile()
  const nav = useNavigate()
  const b = p.businessDetails || {}
  const launch = p.profileType === 'NEW_BUSINESS'
  const [f, setF] = useState({ website: p.websiteUrl || '', domain: b.currentDomain || '', youtube: p.channels?.[0]?.channelUrl || '', google: b.googleBusinessUrl || '',
    timeline: b.launchTimeline || '', category: b.category || '', description: b.description || '', products: (b.productsServices || []).join(', '),
    audience: p.targetAudience || '', location: p.marketLocation || '', goal: p.primaryGoal || '' })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value })
  const save = async (e) => {
    e.preventDefault(); setBusy(true); setError(null)
    try {
      await api.put(`/profiles/${p.id}`, {
        websiteUrl: f.website.trim() || null, currentDomain: f.domain.trim() || null,
        youtubeChannelUrl: f.youtube.trim() && f.youtube.trim() !== p.channels?.[0]?.channelUrl ? f.youtube.trim() : null,
        googleBusinessUrl: f.google.trim() || null, launchTimeline: f.timeline.trim() || null, category: f.category.trim() || null,
        description: f.description.trim() || null, productsServices: f.products.split(/[\n,]/).map((x) => x.trim()).filter(Boolean),
        targetAudience: f.audience.trim() || null, marketLocation: f.location.trim() || null, primaryGoal: f.goal.trim() || null })
      p.reloadProfile(); nav(`/app/profiles/${p.id}`)
    } catch (err) { setError(err); setBusy(false) }
  }
  return (
    <div className="wrap" style={{ padding: '44px 0 60px', maxWidth: 760 }}>
      <p className="eyebrow">Business details</p>
      <h1 className="display" style={{ fontSize: 'clamp(32px,4.4vw,50px)', margin: '12px 0 8px' }}>About <em>{p.displayName}.</em></h1>
      <p className="muted">Add what you have and the analyses adapt. Changing details never re-runs anything by itself.</p>
      <form onSubmit={save} className="panel" style={{ marginTop: 24 }}>
        <h2 className="h3">Online presence</h2>
        <Field id="w" label="Website address" hint="We read the public pages only."><input id="w" className="input" value={f.website} onChange={set('website')} placeholder="https://" inputMode="url" /></Field>
        <Field id="dm" label="Domain, if different from your website"><input id="dm" className="input" value={f.domain} onChange={set('domain')} /></Field>
        <Field id="y" label="YouTube channel" hint="Optional. For example https://www.youtube.com/@yourname"><input id="y" className="input" value={f.youtube} onChange={set('youtube')} placeholder="https://www.youtube.com/@" inputMode="url" /></Field>
        <Field id="g" label="Google Business Profile link" hint="Optional. Saved for your records; we can't read it yet."><input id="g" className="input" value={f.google} onChange={set('google')} inputMode="url" /></Field>
        {launch && <Field id="t" label="When do you plan to open?"><input id="t" className="input" value={f.timeline} onChange={set('timeline')} /></Field>}
        <h2 className="h3" style={{ marginTop: 32 }}>The business</h2>
        <Field id="c" label="Kind of business"><input id="c" className="input" value={f.category} onChange={set('category')} /></Field>
        <Field id="d" label="Description"><textarea id="d" className="input" value={f.description} onChange={set('description')} /></Field>
        <Field id="p" label="What you sell or offer" hint="Separate with commas."><textarea id="p" className="input" value={f.products} onChange={set('products')} /></Field>
        <Field id="a" label="Who it is for"><textarea id="a" className="input" value={f.audience} onChange={set('audience')} /></Field>
        <Field id="l" label="Location"><input id="l" className="input" value={f.location} onChange={set('location')} /></Field>
        <Field id="go" label="Main goal"><input id="go" className="input" value={f.goal} onChange={set('goal')} /></Field>
        <ErrorBox error={error} />
        <div className="row" style={{ marginTop: 24, gap: 12 }}><button className="btn btn-primary" disabled={busy}>{busy ? 'Saving…' : 'Save'}</button><button type="button" className="btn btn-quiet" onClick={() => nav(`/app/profiles/${p.id}`)}>Cancel</button></div>
      </form>
    </div>
  )
}
