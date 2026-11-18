import { Link } from 'react-router-dom'
import { STEPS } from '../lib/tourContent.js'
import { useAuth } from '../lib/auth.jsx'
import { AiTag, Source, Status } from '../components/bits.jsx'

const ALT = {
  overview: 'The overview page: a dark panel with the recommended next step, then three areas and a roadmap',
  seo: 'The SEO and Website page: findings with quoted evidence on the left, checks and PageSpeed on the right',
  geo: 'The GEO page: a checklist of AI-search readiness signals and a note that AI tests have not been run',
  youtube: 'The YouTube page: channel numbers and a comparison of Shorts and long videos',
  plans: 'The Growth Plan page: an ordered list of recommendations with an impact and effort map',
  domains: 'The Domains and names page: a domain checker and name ideas',
  reports: 'The Reports and History page: tabs for each kind of saved analysis',
}

function Shot({ name, alt, caption }) {
  return (
    <figure style={{ margin: 0 }}>
      <img className="guide-shot" src={`/guide/${name}.png`} alt={alt} width="1280" height="860" loading="lazy" />
      {caption && <figcaption className="guide-cap">{caption}</figcaption>}
    </figure>
  )
}

export default function Guide() {
  const { user } = useAuth()
  const start = user ? '/app/new' : '/signin'
  const real = STEPS.filter((s) => s.image)
  return (
    <div className="band" style={{ paddingTop: 48 }}>
      <div className="wrap" style={{ maxWidth: 980 }}>
        <p className="eyebrow">Guide</p>
        <h1 className="display" style={{ fontSize: 'clamp(36px,5vw,60px)', margin: '14px 0 12px' }}>A quick tour of <em>Growbie.</em></h1>
        <p className="lead" style={{ maxWidth: '62ch' }}>Growbie checks your website, how ready it is for AI search, and your YouTube channel if you have one. Then it tells you what to fix first. This guide walks through each page in the order you'll meet them. The screenshots use a public example business, not real customer data.</p>
        <div className="row" style={{ marginTop: 22, gap: 12 }}>
          <Link className="btn btn-primary" to={start}>{user ? 'Add a business' : 'Create an account'}</Link>
          <a className="btn btn-quiet" href="#labels">How to read the labels</a>
        </div>
        <nav className="guide-toc" aria-label="Guide sections">
          <a className="chip chip-soft" href="#add">1 · Add your business</a>
          {real.map((s, i) => <a key={s.path} className="chip chip-soft" href={`#${s.image}`}>{i + 2} · {s.nav}</a>)}
          <a className="chip chip-soft" href="#faq">Questions</a>
        </nav>

        <section id="add" className="guide-step" aria-labelledby="add-h">
          <h2 id="add-h"><span className="n">1</span>Add your business</h2>
          <p className="lead" style={{ maxWidth: '62ch' }}>Sign up, press <b>Add a business</b>, and pick where you are: <b>already running</b> or <b>planning to launch</b>. Three short steps ask about the business, what it has online, and your goal. Skip anything you don't have. Growbie adapts: no website means it helps you set up the basics instead of showing an empty report.</p>
          <Shot name="onboard-1" alt="The start page: two choices, already running or planning to launch" caption="Step one: choose the path that fits." />
          <Shot name="onboard-2" alt="The second onboarding step, asking where the business shows up online, with options to say you don't have a website or channel" caption="Everything online is optional. Tick &quot;I don't have one&quot; and move on." />
        </section>

        {real.map((s, i) => (
          <section key={s.path} id={s.image} className="guide-step" aria-labelledby={`${s.image}-h`}>
            <h2 id={`${s.image}-h`}><span className="n">{i + 2}</span>{s.title}</h2>
            <p className="lead" style={{ maxWidth: '64ch' }}>{s.body}</p>
            <Shot name={s.image} alt={ALT[s.image]} />
            {s.image === 'seo' && <Shot name="seo-domain" alt="The domain assessment: verified facts on one side and an AI-written assessment on the other" caption="The domain assessment keeps measured facts separate from the AI-written verdict." />}
            <div><p className="eyebrow">What to do here</p><ul className="guide-steps-list" style={{ marginTop: 10 }}>{s.doHere.map((d, k) => <li key={k}>{d}</li>)}</ul></div>
          </section>))}

        <section id="labels" className="guide-step" aria-labelledby="labels-h">
          <h2 id="labels-h">How to read the labels</h2>
          <p className="lead" style={{ maxWidth: '62ch' }}>Every piece of information says where it came from, so you can decide how much to trust it.</p>
          <dl className="grid g-2" style={{ gap: '18px 40px' }}>
            <div><Source label="OBSERVED" /><p style={{ marginTop: 8 }}>Measured from public pages, Google PageSpeed or YouTube's public data.</p></div>
            <div><Source label="USER_PROVIDED" /><p style={{ marginTop: 8 }}>Typed in by you. Used, but never presented as verified.</p></div>
            <div><AiTag /><p style={{ marginTop: 8 }}>Written by an AI from the measured evidence. Always a suggestion, never a fact.</p></div>
            <div><Source label="UNAVAILABLE" /><p style={{ marginTop: 8 }}>We could not get it, such as YouTube watch time. It is shown struck through and never counted as zero.</p></div>
          </dl>
          <p className="lead" style={{ maxWidth: '62ch', marginTop: 8 }}>Each area also shows one of five states: <Status state="ANALYSED" /> <Status state="NOT_YET_ANALYSED" /> <Status state="NOT_PROVIDED" /> <Status state="NOT_APPLICABLE" /> <Status state="UNAVAILABLE" />. Missing information is never treated as a failure.</p>
        </section>

        <section id="faq" className="guide-step" aria-labelledby="faq-h">
          <h2 id="faq-h">Questions</h2>
          <dl className="faq">
            <dt>Does Growbie change my website or buy anything?</dt><dd>No. It never publishes, edits, buys a domain or changes any setting for you. You make every change yourself.</dd>
            <dt>What if I don't have a website yet?</dt><dd>That's fine. The SEO page becomes a short guide to the basics, and GEO waits until there's a site to read. Nothing is shown as failed.</dd>
            <dt>What if I don't have a YouTube channel?</dt><dd>YouTube is optional. You can ask for a short assessment of whether it would suit your business.</dd>
            <dt>Why do some things say "unavailable"?</dt><dd>Some information isn't public. For example, YouTube only shares watch time with the channel owner. We'd rather say so than guess.</dd>
            <dt>Can I run an analysis again?</dt><dd>Yes, any time. Each run is saved separately, and Reports &amp; History shows what changed.</dd>
            <dt>Who do I ask for help?</dt><dd>Write to <a href="mailto:support@growbie.in">support@growbie.in</a> or use the <Link to="/contact">contact form</Link>.</dd>
          </dl>
          <div className="row" style={{ marginTop: 24 }}><Link className="btn btn-primary" to={start}>{user ? 'Add a business' : 'Get started'}</Link></div>
        </section>
      </div>
    </div>
  )
}
