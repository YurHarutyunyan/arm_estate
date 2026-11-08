import { createContext, useContext } from 'react'
import type { AuthResponse, Profile } from '../api/types'

export interface AuthState {
  user: Profile | null
  isOwner: boolean
  signIn: (response: AuthResponse) => void
  signOut: () => void
}

export const AuthContext = createContext<AuthState | null>(null)

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside AuthProvider')
  return context
}
