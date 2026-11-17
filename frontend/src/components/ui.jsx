import { useEffect, useState } from 'react'

export const Loading = ({ children = 'Loading…' }) => <div className="state wrap" role="status"><span className="spinner" />{children}</div>
export const ErrorBox = ({ error }) => error ? <div className="err" role="alert">{error.message || String(error)}</div> : null

/** Fetch helper with loading/error state. */
export function useLoad(fn, deps) {
  const [state, setState] = useState({ loading: true, data: null, error: null })
  const [tick, setTick] = useState(0)
  useEffect(() => {
    let live = true
    setState((s) => ({ ...s, loading: true, error: null }))
    fn().then((data) => live && setState({ loading: false, data, error: null }))
      .catch((error) => live && setState({ loading: false, data: null, error }))
    return () => { live = false }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick])
  return { ...state, reload: () => setTick((t) => t + 1) }
}

/** Poll a job until it leaves QUEUED/RUNNING. `fetcher` returns an object with `status`. */
export function usePoll(fetcher, active, onDone, ms = 2500) {
  useEffect(() => {
    if (!active) return undefined
    let live = true
    const t = setInterval(async () => {
      try {
        const r = await fetcher()
        if (live && r.status !== 'QUEUED' && r.status !== 'RUNNING') { clearInterval(t); onDone(r) }
      } catch { /* keep polling; next tick may succeed */ }
    }, ms)
    return () => { live = false; clearInterval(t) }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [active])
}

const MESSAGES = {
  audit: ['Reading your pages…', 'Running Google PageSpeed on a phone…', 'Checking your domain…', 'Seeing how AI tools would read your site…', 'Writing up what we found, with the evidence quoted…'],
  channel: ['Reading your channel…', 'Sorting Shorts from long-form videos…', 'Looking at how often you post…', 'Spotting what you keep coming back to…', 'Drafting ideas and a seven-day plan…'],
  plan: ['Gathering everything we know…', 'Weighing impact against effort…', 'Putting your roadmap in order…', 'Checking each step has its evidence…'],
  studio: ['Reading your facts…', 'Trying a few wordings…', "Making sure nothing is claimed that you didn't say…"],
  names: ['Thinking of names…', 'Saying them out loud…', "Checking what's free on GoDaddy…"],
  youtube: ['Reading about your business…', 'Thinking about how well video would fit…', 'Weighing it honestly…'],
}

/** A friendly waiting state for slow AI work. The messages describe the task; there is no fake progress bar. */
export function Thinking({ kind = 'audit', title, compact = false }) {
  const msgs = MESSAGES[kind] || MESSAGES.audit
  const [secs, setSecs] = useState(0)
  useEffect(() => { const t = setInterval(() => setSecs((s) => s + 1), 1000); return () => clearInterval(t) }, [])
  const msg = msgs[Math.floor(secs / 3) % msgs.length]
  return (
    <div className={`thinking${compact ? ' thinking-compact' : ' wrap'}`} role="status" aria-live="polite">
      <svg className="mate" viewBox="0 0 120 124" width={compact ? 64 : 120} height={compact ? 66 : 124} aria-hidden="true">
        <ellipse className="mate-shadow" cx="60" cy="116" rx="26" ry="5" fill="#17261C" opacity="0.14" />
        <g className="mate-body">
          <path d="M60 30 V14" stroke="#17261C" strokeWidth="3.5" strokeLinecap="round" fill="none" />
          <ellipse className="leaf leaf-l" cx="50" cy="13" rx="9" ry="5" fill="#C8F169" stroke="#17261C" strokeWidth="2.5" transform="rotate(-25 50 13)" />
          <ellipse className="leaf leaf-r" cx="70" cy="11" rx="9" ry="5" fill="#C8F169" stroke="#17261C" strokeWidth="2.5" transform="rotate(25 70 11)" />
          <rect x="26" y="30" width="68" height="68" rx="22" fill="#17261C" />
          <circle className="mate-eye" cx="46" cy="60" r="7" fill="#C8F169" />
          <circle className="mate-eye" cx="74" cy="60" r="7" fill="#C8F169" />
          <path d="M50 77 Q60 86 70 77" stroke="#C8F169" strokeWidth="3.5" strokeLinecap="round" fill="none" />
        </g>
        <g fill="#C8F169" stroke="#17261C" strokeWidth="2">
          <path className="spark s1" d="M10 40 l3 -8 l3 8 l8 3 l-8 3 l-3 8 l-3 -8 l-8 -3 z" />
          <path className="spark s2" d="M104 34 l2.5 -6 l2.5 6 l6 2.5 l-6 2.5 l-2.5 6 l-2.5 -6 l-6 -2.5 z" />
          <path className="spark s3" d="M100 88 l2 -5 l2 5 l5 2 l-5 2 l-2 5 l-2 -5 l-5 -2 z" />
        </g>
      </svg>
      <div className="thinking-text">
        {title && <p className="thinking-title">{title}</p>}
        <p className="thinking-msg" key={msg}>{msg}</p>
        <div className="thinking-bar" aria-hidden="true"><i /></div>
        <p className="small muted">{secs < 40 ? 'This usually takes under a minute.' : 'Still working. Bigger sites and busy moments take a little longer.'}</p>
      </div>
    </div>
  )
}
