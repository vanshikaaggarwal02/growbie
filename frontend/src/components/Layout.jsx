import { Link, NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../lib/auth.jsx'
import { Brand, Footer } from './Brand.jsx'

export default function Layout() {
  const { user, signOut } = useAuth()
  return (
    <>
      <a className="skip" href="#main">Skip to content</a>
      <header className="rail"><div className="wrap">
        <Brand to="/" />
        <nav className="links" aria-label="Main">
          {user && <NavLink to="/app">Your businesses</NavLink>}
        </nav>
        <div className="rail-end">
          {user ? (<>
            <span className="chip-profile"><i></i>{user.displayName}</span>
            <button className="btn btn-sm" onClick={signOut}>Sign out</button>
          </>) : (<Link className="btn btn-sm btn-primary" to="/signin">Sign in</Link>)}
        </div>
      </div></header>
      <main id="main"><Outlet /></main>
      <Footer />
    </>
  )
}
