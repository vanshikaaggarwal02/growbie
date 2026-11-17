import { Link } from 'react-router-dom'
import { api } from '../lib/api.js'
import { ErrorBox, Loading, useLoad } from '../components/ui.jsx'

export const TYPE_LABEL = { NEW_BUSINESS: 'Planning to launch', EXISTING_BUSINESS: 'Already running', YOUTUBE_CREATOR: 'Earlier channel profile' }

export default function Profiles() {
  const { loading, data, error } = useLoad(() => api.get('/profiles'), [])
  return (
    <div className="band" style={{ paddingTop: 48 }}>
      <div className="wrap" style={{ maxWidth: 820 }}>
        <div className="row spread" style={{ marginBottom: 24 }}>
          <h1 className="display" style={{ fontSize: 'clamp(34px,4.6vw,52px)' }}>Your <em>businesses.</em></h1>
          <Link className="btn btn-primary" to="/app/new">Add a business</Link>
        </div>
        {loading && <Loading />}
        <ErrorBox error={error} />
        {data && data.length === 0 && (
          <div className="panel"><h2 className="h3">Nothing here yet</h2>
            <p className="muted" style={{ margin: '6px 0 16px' }}>Tell us about a business that's running or one you're planning to launch, and we'll look at its digital presence.</p>
            <Link className="btn btn-primary" to="/app/new">Add your first business</Link></div>)}
        {data && data.map((p) => (
          <Link key={p.id} className="profile-row" to={`/app/profiles/${p.id}`}>
            <div><h2>{p.displayName}</h2><p className="small muted">{p.websiteUrl || 'No website yet'}</p></div>
            <span className="chip chip-soft">{TYPE_LABEL[p.profileType]}</span>
          </Link>))}
      </div>
    </div>
  )
}
