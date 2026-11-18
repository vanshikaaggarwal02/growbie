import { Link } from 'react-router-dom'

export const TAGLINE = "You've got the business. We've got your back."
export const SUPPORT_EMAIL = 'support@growbie.in'

/** A magnifier over rising bars and an upward arrow: looking closely at growth. */
export function LogoMark({ size = 36 }) {
  return (
    <svg className="logo-mark" viewBox="0 0 64 64" width={size} height={size} fill="none" aria-hidden="true">
      <circle cx="27" cy="27" r="19" stroke="#1E2A50" strokeWidth="4.6" />
      <path d="M41 41l15 15" stroke="#1E2A50" strokeWidth="6.6" strokeLinecap="round" />
      <g fill="#76B82A"><rect x="15" y="30" width="6" height="10" rx="1.5" /><rect x="24" y="25" width="6" height="15" rx="1.5" /><rect x="33" y="19" width="6" height="21" rx="1.5" /></g>
      <path d="M12 33l12-9 7 4 17-17" stroke="#76B82A" strokeWidth="3.6" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M40 9l12-2-2 12z" fill="#76B82A" />
    </svg>
  )
}

/** Logo and wordmark. The wordmark is set in the app's own typeface so it matches the rest of the design. */
export function Brand({ to = '/' }) {
  return (
    <Link className="brand" to={to} aria-label="Growbie home">
      <LogoMark size={34} /><span className="wordmark">Grow<b>bie</b></span>
    </Link>
  )
}

export function Footer() {
  return (
    <footer className="site-footer"><div className="wrap">
      <div><Brand to="/" /><p className="tagline-sm">{TAGLINE}</p></div>
      <p className="small"><Link to="/guide">Guide</Link> · <Link to="/contact">Contact us</Link> · <a href={`mailto:${SUPPORT_EMAIL}`}>{SUPPORT_EMAIL}</a><br />growbie.in</p>
    </div></footer>
  )
}

