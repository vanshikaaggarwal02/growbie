import { Navigate, Outlet, Route, Routes } from 'react-router-dom'
import Layout from './components/Layout.jsx'
import ProfileShell from './components/ProfileShell.jsx'
import { useAuth } from './lib/auth.jsx'
import SignIn from './pages/SignIn.jsx'
import Contact from './pages/Contact.jsx'
import Guide from './pages/Guide.jsx'
import ForgotPassword from './pages/ForgotPassword.jsx'
import ResetPassword from './pages/ResetPassword.jsx'
import Start from './pages/Start.jsx'
import Profiles from './pages/Profiles.jsx'
import Landing from './pages/Landing.jsx'
import Overview from './pages/Overview.jsx'
import Seo from './pages/Seo.jsx'
import Geo from './pages/Geo.jsx'
import YouTube from './pages/YouTube.jsx'
import Plans from './pages/Plans.jsx'
import Plan from './pages/Plan.jsx'
import Reports from './pages/Reports.jsx'
import Details from './pages/Details.jsx'
import Audit from './pages/Audit.jsx'
import AuditCompare from './pages/AuditCompare.jsx'
import Channel from './pages/Channel.jsx'
import Studio from './pages/Studio.jsx'
import Launch from './pages/Launch.jsx'

function Private() {
  const { user } = useAuth()
  return user ? <Outlet /> : <Navigate to="/signin" replace />
}

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<Landing />} />
        <Route path="/signin" element={<SignIn />} />
        <Route path="/contact" element={<Contact />} />
        <Route path="/guide" element={<Guide />} />
        <Route path="/forgot-password" element={<ForgotPassword />} />
        <Route path="/reset-password" element={<ResetPassword />} />
        <Route element={<Private />}>
          <Route path="/app" element={<Profiles />} />
          <Route path="/app/new" element={<Start />} />
        </Route>
      </Route>
      <Route element={<Private />}>
        <Route path="/app/profiles/:id" element={<ProfileShell />}>
          <Route index element={<Overview />} />
          <Route path="seo" element={<Seo />} />
          <Route path="geo" element={<Geo />} />
          <Route path="youtube" element={<YouTube />} />
          <Route path="plans" element={<Plans />} />
          <Route path="plans/:planId" element={<Plan />} />
          <Route path="reports" element={<Reports />} />
          <Route path="details" element={<Details />} />
          <Route path="audits/compare" element={<AuditCompare />} />
          <Route path="audits/:auditId" element={<Audit />} />
          <Route path="channels/:analysisId" element={<Channel />} />
          <Route path="studio" element={<Studio />} />
          <Route path="launch" element={<Launch />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
