import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { api, setToken, setUnauthorizedHandler } from '../api/client'
import type { AuthResponse, Profile } from '../api/types'

const STORAGE_KEY = 'yerevan-homes.session'

interface Session {
  token: string
  expiresAt: string
  user: Profile
}

interface AuthState {
  user: Profile | null
  isOwner: boolean
  signIn: (response: AuthResponse) => void
  signOut: () => void
}

const AuthContext = createContext<AuthState | null>(null)

function loadSession(): Session | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const session = JSON.parse(raw) as Session
    if (new Date(session.expiresAt).getTime() <= Date.now()) return null
    return session
  } catch {
    return null
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [session, setSession] = useState<Session | null>(() => {
    const loaded = loadSession()
    setToken(loaded?.token ?? null)
    return loaded
  })

  const signOut = useCallback(() => {
    setToken(null)
    setSession(null)
    try {
      localStorage.removeItem(STORAGE_KEY)
    } catch {
      // Storage may be unavailable (private mode); the in-memory session is already cleared
    }
    queryClient.removeQueries({ queryKey: ['me'] })
  }, [queryClient])

  const signIn = useCallback(
    (response: AuthResponse) => {
      const next: Session = { token: response.token, expiresAt: response.expiresAt, user: response.user }
      setToken(next.token)
      setSession(next)
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(next))
      } catch {
        // Session still works for this tab
      }
      queryClient.removeQueries({ queryKey: ['me'] })
    },
    [queryClient],
  )

  // Sign out automatically when the token expires or the API rejects it
  useEffect(() => {
    setUnauthorizedHandler(signOut)
    if (!session) return
    const ms = new Date(session.expiresAt).getTime() - Date.now()
    const timer = window.setTimeout(signOut, Math.max(ms, 0))
    return () => window.clearTimeout(timer)
  }, [session, signOut])

  const value = useMemo<AuthState>(
    () => ({
      user: session?.user ?? null,
      isOwner: !!session?.user.roles.some((r) => r === 'OWNER' || r === 'ADMIN'),
      signIn,
      signOut,
    }),
    [session, signIn, signOut],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside AuthProvider')
  return context
}

// Re-exported so pages can call the API without importing two modules
export { api }
