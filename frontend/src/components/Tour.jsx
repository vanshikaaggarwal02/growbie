import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useAuth } from '../lib/auth.jsx'
import { STEPS } from '../lib/tourContent.js'

export const TOUR_EVENT = 'growbie:tour'

/**
 * A short guided tour that moves through the real screens. It opens by itself the first time someone
 * reaches a business, can be skipped at any point, and can be replayed from the Tour button.
 */
export default function Tour() {
  const { id } = useParams()
  const nav = useNavigate()
  const { user } = useAuth()
  const key = `growbie_tour_done_${user?.id}`
  const [step, setStep] = useState(null)
  const panel = useRef(null)
  const base = `/app/profiles/${id}`

  const go = useCallback((i) => {
    setStep(i)
    const p = STEPS[i].path
    if (p !== null) nav(p ? `${base}/${p}` : base)
  }, [base, nav])

  const close = useCallback(() => { localStorage.setItem(key, '1'); setStep(null) }, [key])

  useEffect(() => {
    if (localStorage.getItem(key)) return undefined
    const t = setTimeout(() => go(0), 800)
    return () => clearTimeout(t)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, id])

  useEffect(() => {
    const h = () => go(0)
    window.addEventListener(TOUR_EVENT, h)
    return () => window.removeEventListener(TOUR_EVENT, h)
  }, [go])

  // Mark the section being explained in the top bar.
  useEffect(() => {
    const links = [...document.querySelectorAll('.appnav a')]
    links.forEach((a) => a.classList.remove('tour-hl'))
    if (step !== null && STEPS[step].nav) links.find((a) => a.textContent.trim() === STEPS[step].nav)?.classList.add('tour-hl')
    if (step !== null) panel.current?.focus()
    return () => links.forEach((a) => a.classList.remove('tour-hl'))
  }, [step])

  if (step === null) return null
  const s = STEPS[step]
  const last = step === STEPS.length - 1
  return (
    <div className="tour" role="dialog" aria-label="Guided tour" tabIndex={-1} ref={panel}
      onKeyDown={(e) => { if (e.key === 'Escape') close() }}>
      <p className="tour-count">Step {step + 1} of {STEPS.length}</p>
      <h2 className="tour-title">{s.title}</h2>
      <p className="tour-body" aria-live="polite">{s.body}</p>
      <div className="tour-dots" aria-hidden="true">{STEPS.map((_, i) => <i key={i} className={i === step ? 'on' : ''} />)}</div>
      <div className="tour-actions">
        {step > 0 && <button type="button" className="btn btn-sm btn-quiet" style={{ color: 'var(--paper)' }} onClick={() => go(step - 1)}>Back</button>}
        {!last && <button type="button" className="btn btn-sm btn-primary" onClick={() => go(step + 1)}>Next</button>}
        {last && <button type="button" className="btn btn-sm btn-primary" onClick={close}>Done</button>}
        {!last && <button type="button" className="btn btn-sm btn-quiet" style={{ color: 'var(--paper)', marginLeft: 'auto' }} onClick={close}>Skip tour</button>}
      </div>
    </div>
  )
}
