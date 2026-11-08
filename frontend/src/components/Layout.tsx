import { NavLink, Link, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/context'

export function BrandMark() {
  return (
    <svg className="brand__mark" viewBox="0 0 64 64" aria-hidden="true">
      <rect width="64" height="64" rx="18" fill="#0c4234" />
      <path d="M14 34 32 18l18 16v14a2 2 0 0 1-2 2H38V38H26v12H16a2 2 0 0 1-2-2Z" fill="#2ee59d" />
    </svg>
  )
}

export function Layout() {
  const { user, isOwner, signOut } = useAuth()
  const navigate = useNavigate()

  return (
    <>
      <div className="backdrop" aria-hidden="true">
        <div className="backdrop__shape backdrop__shape--a" />
        <div className="backdrop__shape backdrop__shape--b" />
      </div>
      <header className="header">
        <div className="header__inner">
          <Link to="/" className="brand" aria-label="Yerevan Homes, home">
            <BrandMark />
            <span className="brand__name">Yerevan Homes</span>
          </Link>
          <nav className="nav" aria-label="Main">
            <NavLink to="/" end>
              Search
            </NavLink>
            {user && <NavLink to="/favorites">Favorites</NavLink>}
            {isOwner && <NavLink to="/my-listings">My listings</NavLink>}
            {user ? (
              <button
                type="button"
                className="link-button"
                onClick={() => {
                  signOut()
                  navigate('/')
                }}
              >
                Sign out
              </button>
            ) : (
              <>
                <NavLink to="/login">Sign in</NavLink>
                <Link to="/register" className="button">
                  Sign up
                </Link>
              </>
            )}
          </nav>
        </div>
      </header>
      <main className="page" id="main">
        <Outlet />
      </main>
    </>
  )
}
