import { useSearchParams } from 'react-router-dom'
import { useProfile } from '../components/ProfileShell.jsx'
import AuditHistory from './AuditHistory.jsx'
import ChannelHistory from './ChannelHistory.jsx'
import PlanHistory from './PlanHistory.jsx'

const TABS = [['seo', 'SEO & Website'], ['youtube', 'YouTube'], ['plans', 'Growth plans']]

/** Every analysis is saved as its own record. Nothing here is overwritten. */
export default function Reports() {
  const profile = useProfile()
  const [qs, setQs] = useSearchParams()
  const tab = TABS.some(([k]) => k === qs.get('tab')) ? qs.get('tab') : 'seo'
  return (
    <div className="wrap" style={{ padding: '44px 0 60px' }}>
      <div className="row" style={{ marginBottom: 14 }}><span className="chip chip-soft">Reports &amp; History</span></div>
      <h1 className="title">What changed since <em>last time.</em></h1>
      <p className="lead" style={{ margin: '18px 0 28px', maxWidth: '62ch' }}>Every analysis is saved as its own record, with its findings and wording. Compare the latest with an earlier one to see what is fixed, what is still there and what is new. GEO and domain results are saved with each website audit.</p>
      <div className="row" style={{ marginBottom: 24, gap: 8 }}>
        {TABS.map(([k, label]) => <button key={k} className={`chip ${tab === k ? 'chip-dark' : 'chip-soft'}`} aria-pressed={tab === k} style={{ cursor: 'pointer', font: 'inherit', fontWeight: 700 }} onClick={() => setQs({ tab: k })}>{label}</button>)}
      </div>
      {tab === 'seo' && (profile.websiteUrl ? <AuditHistory embedded /> : <p className="muted">No website address yet, so there are no audits. Add one in the business details.</p>)}
      {tab === 'youtube' && ((profile.channels || []).length ? <ChannelHistory embedded /> : <p className="muted">No YouTube channel was given, so there are no analyses. That's fine: YouTube is optional.</p>)}
      {tab === 'plans' && <PlanHistory embedded />}
    </div>
  )
}
