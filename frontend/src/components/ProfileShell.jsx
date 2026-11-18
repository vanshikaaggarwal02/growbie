import { createContext, useContext } from 'react'
import { Link, NavLink, Outlet, useParams } from 'react-router-dom'
import { api } from '../lib/api.js'
import { useAuth } from '../lib/auth.jsx'
import { ErrorBox, Loading, useLoad } from './ui.jsx'
import { Brand, Footer } from './Brand.jsx'
import Tour, { TOUR_EVENT } from './Tour.jsx'

const ProfileContext = createContext(null)
export const useProfile = () => useContext(ProfileContext)

const SECTIONS = [
  ['', 'Overview', true], ['seo', 'SEO & Website'], ['geo', 'GEO'], ['youtube', 'YouTube'], ['plans', 'Growth Plan'], ['launch', 'Domains & names'], ['reports', 'Reports & History'],
]

/** The application frame for one business: brand, the six sections, and the business name. */
export default function ProfileShell() {
  const { id } = useParams()
  const { signOut } = useAuth()
  const { loading, data, error, reload } = useLoad(() => api.get(`/profiles/${id}`), [id])
  const base = `/app/profiles/${id}`
  return (
    <>
      <a className="skip" href="#main">Skip to content</a>
      <header className="rail shell-rail"><div className="wrap">
        <Brand to="/" />
        <nav className="appnav" aria-label="Sections">
          {SECTIONS.map(([path, label, end]) => (
            <NavLink key={label} to={path ? `${base}/${path}` : base} end={end}
              className={({ isActive }) => (isActive ? 'active' : '')} aria-current={undefined}>{label}</NavLink>))}
        </nav>
        <div className="rail-end">
          {data && <Link className="chip-profile" to={`${base}/details`} title="Edit business details"><i></i>{data.displayName}</Link>}
          <button className="btn btn-sm btn-quiet" onClick={() => window.dispatchEvent(new Event(TOUR_EVENT))}>Tour</button>
          <button className="btn btn-sm" onClick={signOut}>Sign out</button>
        </div>
      </div></header>
      <main id="main">
        {loading && <Loading />}
        {error && <div className="wrap state"><ErrorBox error={error} /><p style={{ marginTop: 16 }}><Link className="btn" to="/app">Back to your businesses</Link></p></div>}
        {data && <ProfileContext.Provider value={{ ...data, reloadProfile: reload }}><Outlet /></ProfileContext.Provider>}
      </main>
      <Footer />
      <Tour />
    </>
  )
}
