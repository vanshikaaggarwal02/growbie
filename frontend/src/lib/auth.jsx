import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { api, setUnauthorizedHandler, tokenStore } from './api.js'

const AuthContext = createContext(null)
const USER_KEY = 'lm_user'

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    try { return tokenStore.get() ? JSON.parse(localStorage.getItem(USER_KEY)) : null } catch { return null }
  })

  const signOut = useCallback(() => {
    tokenStore.clear()
    localStorage.removeItem(USER_KEY)
    setUser(null)
  }, [])

  useEffect(() => { setUnauthorizedHandler(signOut) }, [signOut])

  const accept = (res) => {
    tokenStore.set(res.token)
    localStorage.setItem(USER_KEY, JSON.stringify(res.user))
    setUser(res.user)
  }

  const value = useMemo(() => ({
    user,
    signOut,
    login: async (email, password) => accept(await api.post('/auth/login', { email, password })),
    register: async (email, password, displayName) => accept(await api.post('/auth/register', { email, password, displayName })),
  }), [user, signOut])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export const useAuth = () => useContext(AuthContext)
