import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api } from '../lib/api.js'
import { ErrorBox, Loading, Thinking, useLoad } from '../components/ui.jsx'
import { fmtDate } from '../components/bits.jsx'

const AVAIL = { AVAILABLE: ['Available', ''], UNAVAILABLE: ['Not available', 'no'], UNKNOWN: ['Could not check', 'no'], NOT_CHECKED: ['Not checked yet', 'no'] }

export function DomainTable({ rows }) {
  if (!rows?.length) return null
  return (
    <div className="scroll"><table>
      <thead><tr><th>Domain</th><th>Availability</th><th>Price</th><th></th></tr></thead>
      <tbody>{rows.map((r) => {
        const [label, cls] = AVAIL[r.availability] || [r.availability, 'no']
        return (
          <tr key={r.domain}>
            <td><span className="d">{r.domain}</span>{r.origin === 'CURRENT' && <span className="sub">Your current domain</span>}{r.availability !== 'AVAILABLE' && r.availability !== 'NOT_CHECKED' && r.notes?.[0] && <span className="sub">{r.notes[0]}</span>}</td>
            <td><span className={`avail ${cls}`}><i></i>{label}</span>{r.premiumFeeMinor ? <><br /><span className="warn">Premium domain</span></> : null}</td>
            <td>{r.priceDisplay ? <><b>{r.priceDisplay}</b>{r.periodYears ? ` for ${r.periodYears} year${r.periodYears > 1 ? 's' : ''}` : ''}
              {r.premiumFeeDisplay && <span className="sub"><b>plus {r.premiumFeeDisplay} one-time premium fee</b></span>}
              {r.renewalPriceDisplay && <span className="sub">renews at {r.renewalPriceDisplay}</span>}</> : <span className="muted">—</span>}</td>
            <td>{r.availability === 'AVAILABLE' && <a className="btn btn-sm" href={`https://www.godaddy.com/domainsearch/find?domainToCheck=${encodeURIComponent(r.domain)}`} target="_blank" rel="noreferrer">Buy on GoDaddy →</a>}</td>
          </tr>)
      })}</tbody>
    </table></div>
  )
}

export default function Launch() {
  const { id } = useParams()
  const profile = useLoad(() => api.get(`/profiles/${id}`), [id])
  const saved = useLoad(() => api.get(`/profiles/${id}/domains`), [id])
  const [input, setInput] = useState('')
  const [rows, setRows] = useState(null)
  const [note, setNote] = useState(null)
  const [names, setNames] = useState(null)
  const [busy, setBusy] = useState(null)
  const [error, setError] = useState(null)

  const check = async (e) => {
    e.preventDefault(); setBusy('check'); setError(null)
    try {
      const list = input.split(/[\s,]+/).filter(Boolean)
      const r = await api.post(`/profiles/${id}/domain-analyses`, { domains: list, checkAvailability: true })
      setRows(r.results); setNote([r.note, ...(r.rejected || []).map((x) => `${x.input}: ${x.reason}`)].filter(Boolean).join(' '))
      saved.reload()
    } catch (err) { setError(err) } finally { setBusy(null) }
  }
  const ideas = async () => {
    setBusy('names'); setError(null)
    try { setNames(await api.post(`/profiles/${id}/brand-suggestions`, { checkAvailability: true })) } catch (err) { setError(err) } finally { setBusy(null) }
  }

  if (profile.loading) return <Loading />
  if (profile.error) return <div className="wrap"><ErrorBox error={profile.error} /></div>
  const shown = rows || saved.data

  return (
    <div className="wrap" style={{ padding: '44px 0 60px' }}>
      <div className="row" style={{ marginBottom: 14 }}><span className="chip chip-soft">{profile.data.profileType === 'NEW_BUSINESS' ? 'Planning to launch' : 'Already running'}</span><span className="chip chip-soft">Optional</span></div>
      <h1 className="display" style={{ fontSize: 'clamp(30px,4vw,50px)', maxWidth: '22ch', margin: '0 0 28px' }}>Domain <em>finder</em> and name ideas.</h1>
      <div className="callout" style={{ marginBottom: 28 }}><b>You buy it yourself.</b> Growbie never purchases a domain or changes DNS. We check availability and link you to GoDaddy. Prices are indicative; confirm at checkout.</div>

      <section aria-labelledby="dom-h">
        <h2 id="dom-h" className="h2" style={{ marginBottom: 14 }}>Check a <em>domain.</em></h2>
        <form className="finder" onSubmit={check} style={{ marginBottom: 22 }}>
          <input className="input" aria-label="Domain names to check" placeholder="yourname.com, yourname.in" value={input} onChange={(e) => setInput(e.target.value)} required />
          <button className="btn btn-primary" disabled={busy === 'check'}>{busy === 'check' ? 'Checking…' : 'Check availability'}</button>
        </form>
        <ErrorBox error={error} />
        {note && <p className="small muted" style={{ marginBottom: 10 }}>{note}</p>}
        <DomainTable rows={shown} />
        {shown?.some((r) => r.checkedAt) && <p className="small muted" style={{ marginTop: 8 }}>Latest check {fmtDate(shown.find((r) => r.checkedAt).checkedAt)} via GoDaddy. Domains marked "not checked yet" have not been looked up; enter them above to check.</p>}
      </section>

      <section style={{ marginTop: 56 }} aria-labelledby="names-h">
        <div className="row spread" style={{ marginBottom: 14 }}><h2 id="names-h" className="h2">Name <em>ideas.</em></h2><span className="src src-ai">AI-suggested</span></div>
        <button className="btn btn-primary" onClick={ideas} disabled={busy === 'names'}>{busy === 'names' ? 'Thinking of names…' : names ? 'Suggest more' : 'Suggest names for my business'}</button>
        {busy === 'names' && <Thinking kind="names" compact />}
        {names && <>
          <div className="names" style={{ marginTop: 22 }}>
            {names.names.map((n) => (
              <div className="name" key={n.name}><h3>{n.name}</h3><p>{n.tagline}</p>
                {n.domains?.map((d) => <span key={d.domain} className="chip chip-soft" style={{ marginRight: 6 }}>{d.domain} · {(AVAIL[d.availability] || [d.availability])[0].toLowerCase()}</span>)}</div>))}
          </div>
          <p className="callout" style={{ marginTop: 20 }}><b>Not checked:</b> trademark and existing-brand conflicts. Search the trademark register before you commit to any name.</p>
        </>}
      </section>
      <p style={{ marginTop: 32 }}><Link className="btn" to={`/app/profiles/${id}`}>Back to workspace</Link></p>
    </div>
  )
}
