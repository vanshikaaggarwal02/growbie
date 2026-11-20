import { Link } from 'react-router-dom'

export default function Landing() {
  return (
    <>

  <section className="hero">
    <div className="wrap grid g-7-5" style={{ alignItems: 'center', gap: '56px' }}>
      <div>
        <p className="tagline">You've got the business. We've got your back.</p>
        <h1 className="display">Find what to fix first, <em>with proof.</em></h1>
        <p className="lead">Growbie checks your website, how ready it is for AI search, and your social media presence, starting with YouTube. It quotes the evidence, ranks what to do next and drafts the wording. You approve every word. Nothing is published for you.</p>
        <div className="row" style={{ marginTop: '30px', gap: '16px' }}>
          <Link className="btn btn-primary" to="/signin">Start a diagnosis</Link>
          <a className="btn btn-quiet" href="#loop">How it works</a>
        </div>
        <p className="small muted" style={{ marginTop: '18px' }}>Built on live data from Google PageSpeed, the YouTube Data API and GoDaddy. No sales, traffic or AI-search results are ever made up.</p>
      </div>
      <div className="hero-art" aria-label="Example of a finding and its suggested fix">
        <div className="receipt high">
          <div className="receipt-body">
            <div className="receipt-head"><span className="sev sev-high">High</span><span className="src src-observed">Observed</span></div>
            <p className="receipt-title">The homepage has no main heading</p>
            <div className="receipt-quote"><span className="where">Found on example.com</span><span className="quote"><span className="hl">SINGLE_H1: FAIL - no h1 heading was found</span></span></div>
            <p className="small muted">Without one, neither visitors nor search engines get a clear statement of what the page offers.</p>
          </div>
        </div>
        <div className="ba">
          <div className="now"><h4>Now</h4><p className="text"><del>Example Domain</del></p></div>
          <div className="new"><h4>Suggested</h4><p className="text">Bare Botanics | Plant-Based Skincare for Sensitive Skin</p></div>
        </div>
      </div>
    </div>
  </section>

  <section className="band band-2" id="loop">
    <div className="wrap">
      <p className="eyebrow">The loop</p>
      <h2 className="h2" style={{ margin: '12px 0 34px', maxWidth: '20ch' }}>Diagnose, recommend, execute, <em>measure.</em></h2>
      <div className="loop-grid">
        <div><div className="big">1</div><h3>Diagnose</h3><p>Reads up to 10 pages of your site, checks your domain and AI-search readiness, and looks at your YouTube channel if you have one.</p><p className="proof">14 readiness checks · Google PageSpeed · YouTube public data</p></div>
        <div><div className="big">2</div><h3>Recommend</h3><p>Ranks the changes by impact and effort. Every item points at the line of evidence it rests on.</p><p className="proof">Priority comes from a fixed rule, not a hidden score</p></div>
        <div><div className="big">3</div><h3>Execute</h3><p>Drafts titles, page copy, hooks and scripts from your own facts. You pick a version. You publish it.</p><p className="proof">Never publishes, buys or edits anything for you</p></div>
        <div><div className="big">4</div><h3>Measure</h3><p>Run it again and see what is fixed, what is still there, and what is new. No claim that a change caused a result.</p><p className="proof">Checks that pass or fail, not predictions</p></div>
      </div>
    </div>
  </section>

  <section className="band band-dark" id="proof">
    <div className="wrap">
      <p className="eyebrow">Why you can trust it</p>
      <h2 className="h2" style={{ margin: '12px 0 10px', maxWidth: '30ch' }}>We quote it, or <em>we don't say it.</em></h2>
      <p className="lead" style={{ color: '#D5E3D6' }}>Every finding is tagged with where it came from. A finding whose evidence cannot be found in your own pages or data is removed before you see it.</p>
      <div className="badge-row">
        <div><span className="src src-observed">Observed</span><p>Measured from your public pages, PageSpeed or YouTube data.</p></div>
        <div><span className="src src-user">You told us</span><p>Typed in by you. Used, but never presented as verified.</p></div>
        <div><span className="src src-ai">AI-suggested</span><p>A judgement or suggestion from AI, worded as a likelihood. Never as a fact.</p></div>
        <div><span className="src src-na">Unavailable</span><p>Data we cannot get, such as watch time. Shown struck through, never as zero.</p></div>
      </div>
    </div>
  </section>

  <section className="band" id="start">
    <div className="wrap">
      <p className="eyebrow">Where are you starting?</p>
      <h2 className="h2" style={{ margin: '12px 0 30px' }}>Two starting points, <em>one honest method.</em></h2>
      <div className="paths">
        <Link to="/signin"><span className="chip chip-soft tag">Already running</span><h3>My business is already running</h3><ul><li>Website audit with the evidence quoted</li><li>Domain and AI-search readiness checks</li><li>YouTube analysis, if you have a channel</li><li>One roadmap, ranked by priority</li></ul></Link>
        <Link to="/signin"><span className="chip chip-soft tag">Planning to launch</span><h3>I'm planning to launch</h3><ul><li>Your digital foundation, step by step</li><li>Domain ideas with real availability and price</li><li>Whether YouTube is worth your time yet</li><li>A launch roadmap that grows with you</li></ul></Link>
      </div>
    </div>
  </section>

  <section className="band band-2">
    <div className="wrap grid g-5-7" style={{ gap: '56px' }}>
      <div><p className="eyebrow">What you will not get</p><h2 className="h2" style={{ marginTop: '12px' }}>Things we <em>refuse</em> to do.</h2></div>
      <ul className="never">
        <li>Invented traffic, sales or follower numbers</li>
        <li>Promises of reach, rankings or growth</li>
        <li>Claims your own facts do not support</li>
        <li>Posting, editing or buying anything for you</li>
        <li>Guesses about watch time or retention</li>
        <li>Advice to change a domain without the risks</li>
      </ul>
    </div>
  </section>
    </>
  )
}
