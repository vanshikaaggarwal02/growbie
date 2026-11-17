export const SRC = {
  OBSERVED_PUBLIC_DATA: ['src-observed', 'Observed'],
  OBSERVED: ['src-observed', 'Observed'],
  USER_PROVIDED: ['src-user', 'You told us'],
  AI_DERIVED: ['src-ai', 'AI-suggested'],
  INFERENCE: ['src-ai', 'AI-suggested'],
  UNAVAILABLE: ['src-na', 'Unavailable'],
}
export function Source({ label, text }) {
  const [cls, name] = SRC[label] || SRC.AI_DERIVED
  return <span className={`src ${cls}`}>{text || name}</span>
}

const SEV = { HIGH: 'High', MEDIUM: 'Medium', LOW: 'Low' }
export const Sev = ({ level }) => <span className={`sev sev-${(level || 'low').toLowerCase()}`}>{SEV[level] || level}</span>

/** Marks content an AI call wrote or suggested. Measured results are never given this label. */
export const AiTag = ({ text = 'AI-suggested' }) => <span className="src src-ai">{text}</span>

export const cap = (s) => (s === 'SEO' ? 'SEO' : s ? s.charAt(0) + s.slice(1).toLowerCase().replace(/_/g, ' ') : '')

/** Model summaries can be a paragraph: use the first sentence as the headline and the rest as a lead. */
export function splitSummary(text = '') {
  const m = text.match(/^(.+?[.!?])\s+(.*)$/s)
  return m && m[1].length < 160 ? { head: m[1], rest: m[2] } : { head: text.length > 160 ? text.slice(0, text.lastIndexOf(' ', 150)) + '…' : text, rest: text.length > 160 ? text : '' }
}
export const fmtDate = (iso) => iso ? new Date(iso).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' }) : ''
export const fmtNum = (n) => (n == null ? '—' : Number(n).toLocaleString())

/** The five states an area can be in. Missing information is never a failure and never zero. */
const STATES = {
  ANALYSED: ['st-analysed', 'Analysed'],
  NOT_YET_ANALYSED: ['st-notyet', 'Not yet analysed'],
  NOT_PROVIDED: ['st-notprov', 'Not provided'],
  NOT_APPLICABLE: ['st-na', 'Not applicable'],
  UNAVAILABLE: ['st-unavail', 'Unavailable'],
}
export function Status({ state, label }) {
  const [cls, name] = STATES[state] || STATES.UNAVAILABLE
  return <span className={`st ${cls}`}>{label || name}</span>
}

export const AREA_LABEL = { WEBSITE: 'SEO & Website', GEO: 'GEO', CHANNEL: 'YouTube', DOMAIN: 'SEO & Website · domain', BRAND: 'Digital foundation', LAUNCH: 'Digital foundation' }
export const PATH_LABEL = { RUNNING: 'Already running', LAUNCH: 'Planning to launch' }
