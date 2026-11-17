import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api } from '../lib/api.js'
import { ErrorBox } from '../components/ui.jsx'

const PATHS = [
  { key: 'running', n: 1, title: 'My business is already running', text: "You have customers, and maybe a website or a YouTube channel. We'll look at what you already have." },
  { key: 'launch', n: 2, title: "I'm planning to launch", text: "You have an idea or are preparing to open. We'll help you set up your digital foundation first." },
]
const splitList = (s) => s.split(/[\n,]/).map((x) => x.trim()).filter(Boolean)

function Field({ id, label, hint, children }) {
  return <div className="field"><label htmlFor={id}>{label}</label>{children}{hint && <p className="hint">{hint}</p>}</div>
}

/** A field that can be skipped: ticking "I don't have one" clears and disables the input. */
function Optional({ id, label, hint, value, onChange, none, onNone, noneText, placeholder }) {
  return (
    <Field id={id} label={label} hint={hint}>
      <input id={id} className="input" value={value} onChange={onChange} disabled={none} placeholder={placeholder} inputMode="url" />
      <label className="none" style={{ cursor: 'pointer' }}><input type="checkbox" checked={none} onChange={onNone} /> {noneText}</label>
    </Field>
  )
}

export default function Start() {
  const nav = useNavigate()
  const [path, setPath] = useState(null)
  const [step, setStep] = useState(1)
  const [f, setF] = useState({ name: '', category: '', description: '', products: '', audience: '', location: '', website: '', domain: '', youtube: '', google: '', timeline: '', goal: '' })
  const [none, setNone] = useState({ website: false, youtube: false, google: false })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value })
  const toggle = (k) => () => { setNone({ ...none, [k]: !none[k] }); if (!none[k]) setF({ ...f, [k]: '' }) }
  const launch = path === 'launch'

  const submit = async () => {
    setBusy(true); setError(null)
    const body = {
      profileType: launch ? 'NEW_BUSINESS' : 'EXISTING_BUSINESS', displayName: f.name.trim(),
      websiteUrl: f.website.trim() || null, youtubeChannelUrl: f.youtube.trim() || null,
      targetAudience: f.audience.trim() || null, marketLocation: f.location.trim() || null, primaryGoal: f.goal.trim() || null,
      businessDetails: { category: f.category.trim() || null, description: f.description.trim() || null,
        productsServices: splitList(f.products), currentDomain: f.domain.trim() || null,
        googleBusinessUrl: f.google.trim() || null, launchTimeline: launch ? f.timeline.trim() || null : null },
    }
    try { const p = await api.post('/profiles', body); nav(`/app/profiles/${p.id}`) } catch (e) { setError(e); setBusy(false) }
  }

  const canNext = step === 1 ? f.name.trim().length > 0 : true
  return (
    <div className="band" style={{ paddingTop: 48 }}>
      <div className="wrap grid g-7-5" style={{ gap: 64, alignItems: 'start' }}>
        <section aria-labelledby="where">
          <p className="eyebrow">Start here</p>
          <h1 id="where" className="display" style={{ fontSize: 'clamp(36px,5vw,58px)', margin: '14px 0 26px' }}>Tell us about your <em>business.</em></h1>
          {PATHS.map((p) => (
            <button type="button" key={p.key} className={`opt${path === p.key ? ' on' : ''}`} aria-pressed={path === p.key} onClick={() => { setPath(p.key); setStep(1) }}>
              <span className="big">{p.n}</span><div><h2>{p.title}</h2><p>{p.text}</p></div>
              <span className={`chip ${path === p.key ? 'chip-dark' : 'chip-soft'}`}>{path === p.key ? 'Selected' : 'Choose'}</span>
            </button>))}
        </section>

        <section className="panel" aria-label="Business details">
          {!path ? <p className="muted">Choose where you are on the left, and we'll ask only what we need.</p> : <>
            <div className="steps"><span className={step === 1 ? 'on' : ''}>1 · {launch ? 'The idea' : 'The business'}</span><span className={step === 2 ? 'on' : ''}>2 · {launch ? 'What you have so far' : 'Online presence'}</span><span className={step === 3 ? 'on' : ''}>3 · {launch ? 'Launch goal' : 'Your goal'}</span></div>

            {step === 1 && <>
              <h2 className="h3">{launch ? "What's the idea?" : 'About your business'}</h2>
              <Field id="n" label={launch ? 'Proposed business name' : 'Business name'}><input id="n" className="input" required maxLength={160} value={f.name} onChange={set('name')} /></Field>
              <Field id="c" label="What kind of business?" hint="For example: skincare, pottery studio, restaurant."><input id="c" className="input" maxLength={160} value={f.category} onChange={set('category')} /></Field>
              <Field id="d" label="Describe it in a sentence or two"><textarea id="d" className="input" maxLength={4000} value={f.description} onChange={set('description')} /></Field>
              <Field id="p" label="What do you sell or offer?" hint="Separate with commas or new lines."><textarea id="p" className="input" value={f.products} onChange={set('products')} /></Field>
              <Field id="a" label="Who is it for?"><textarea id="a" className="input" maxLength={2000} value={f.audience} onChange={set('audience')} /></Field>
              <Field id="l" label={launch ? 'Where will you operate?' : 'Where are you based or who do you serve?'}><input id="l" className="input" maxLength={160} value={f.location} onChange={set('location')} /></Field>
            </>}

            {step === 2 && <>
              <h2 className="h3">{launch ? `What does ${f.name || 'your business'} have so far?` : `Where does ${f.name || 'your business'} show up online?`}</h2>
              <p className="small muted">{launch ? "Most people planning a launch have very little online. That's a fine place to start. Add what exists; skip the rest." : "Add what you have. Skip the rest; we'll adapt."}</p>
              <Optional id="w" label="Website address" hint="We read the public pages only." placeholder="https://" value={f.website} onChange={set('website')} none={none.website} onNone={toggle('website')} noneText={launch ? 'Not yet' : "I don't have a website"} />
              <Field id="dm" label={launch ? 'A domain you already own or like' : 'Domain, if different from your website'} hint={launch ? 'Optional. We can suggest names later.' : "We'll check it suits the business. We only suggest a change when there's a real reason."}><input id="dm" className="input" value={f.domain} onChange={set('domain')} /></Field>
              <Optional id="y" label="YouTube channel" placeholder="https://www.youtube.com/@" value={f.youtube} onChange={set('youtube')} none={none.youtube} onNone={toggle('youtube')} noneText={"I don't have a channel. Tell me whether I should start one."} />
              <Optional id="g" label="Google Business Profile" placeholder="Link to your profile, if you have one" value={f.google} onChange={set('google')} none={none.google} onNone={toggle('google')} noneText={"I don't have one"} />
              {launch && <Field id="t" label="When do you plan to open?"><input id="t" className="input" maxLength={160} placeholder="For example: in about three months" value={f.timeline} onChange={set('timeline')} /></Field>}
            </>}

            {step === 3 && <>
              <h2 className="h3">{launch ? "What's your main goal for the launch?" : "What's your main goal right now?"}</h2>
              <Field id="g3" label="Your goal" hint="For example: first online orders, more bookings, be found locally."><textarea id="g3" className="input" maxLength={2000} value={f.goal} onChange={set('goal')} /></Field>
              <p className="small muted" style={{ marginTop: 14 }}>Next you'll see your overview. Nothing runs until you choose to run it.</p>
            </>}

            <ErrorBox error={error} />
            <div className="row" style={{ marginTop: 24, gap: 12 }}>
              {step > 1 && <button type="button" className="btn btn-quiet" onClick={() => setStep(step - 1)}>Back</button>}
              {step < 3 ? <button type="button" className="btn btn-primary" disabled={!canNext} onClick={() => setStep(step + 1)}>Continue</button>
                : <button type="button" className="btn btn-primary" disabled={busy} onClick={submit}>{busy ? 'Saving…' : 'Create my overview'}</button>}
            </div>
          </>}
          <p className="small muted" style={{ marginTop: 18 }}><Link to="/app">Cancel</Link></p>
        </section>
      </div>
    </div>
  )
}
