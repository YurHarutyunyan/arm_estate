import { Component, type ErrorInfo, type ReactNode } from 'react'

interface State {
  failed: boolean
}

/** Shows a recovery message instead of a blank page when a component crashes while rendering. */
export class ErrorBoundary extends Component<{ children: ReactNode }, State> {
  state: State = { failed: false }

  static getDerivedStateFromError(): State {
    return { failed: true }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Unexpected error while rendering', error, info.componentStack)
  }

  render() {
    if (!this.state.failed) return this.props.children
    return (
      <section className="not-found" role="alert">
        <h1>Something went wrong</h1>
        <p className="muted">The page hit an unexpected error. Reloading usually fixes it.</p>
        <button type="button" className="button" onClick={() => window.location.reload()}>
          Reload the page
        </button>
      </section>
    )
  }
}
