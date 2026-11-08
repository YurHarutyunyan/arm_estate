import { Link } from 'react-router-dom'

export function NotFoundPage() {
  return (
    <section className="not-found">
      <div className="not-found__code" aria-hidden="true">
        404
      </div>
      <h1>Page not found</h1>
      <p className="muted">This page or listing doesn’t exist, or it was removed.</p>
      <Link className="button" to="/">
        Back to search
      </Link>
    </section>
  )
}
