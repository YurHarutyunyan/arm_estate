import type { ReactNode } from 'react'
import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { useAuth } from './auth/context'
import { Layout } from './components/Layout'
import { LoginPage, RegisterPage } from './pages/AuthPages'
import { ListingFormPage } from './pages/ListingFormPage'
import { ListingPage } from './pages/ListingPage'
import { FavoritesPage, MyListingsPage } from './pages/ListPages'
import { NotFoundPage } from './pages/NotFoundPage'
import { SearchPage } from './pages/SearchPage'

function RequireAuth({ owner = false, children }: { owner?: boolean; children: ReactNode }) {
  const { user, isOwner } = useAuth()
  const location = useLocation()
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  if (owner && !isOwner) return <Navigate to="/" replace />
  return children
}

export function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<SearchPage />} />
        <Route path="listings/new" element={<RequireAuth owner><ListingFormPage /></RequireAuth>} />
        <Route path="listings/:id" element={<ListingPage />} />
        <Route path="listings/:id/edit" element={<RequireAuth owner><ListingFormPage /></RequireAuth>} />
        <Route path="favorites" element={<RequireAuth><FavoritesPage /></RequireAuth>} />
        <Route path="my-listings" element={<RequireAuth owner><MyListingsPage /></RequireAuth>} />
        <Route path="login" element={<LoginPage />} />
        <Route path="register" element={<RegisterPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
