import { useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { api, ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'

/** Where to go after signing in: back to the page that asked, or home. Only same-site paths. */
function useReturnPath() {
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from
  return from && from.startsWith('/') && !from.startsWith('//') ? from : '/'
}

export function LoginPage() {
  const { user, signIn } = useAuth()
  const navigate = useNavigate()
  const returnTo = useReturnPath()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  const login = useMutation({
    mutationFn: () => api.login({ email, password }),
    onSuccess: (response) => {
      signIn(response)
      navigate(returnTo, { replace: true })
    },
  })

  if (user) return <Navigate to={returnTo} replace />

  const submit = (e: FormEvent) => {
    e.preventDefault()
    login.mutate()
  }

  return (
    <form className="card form-page" onSubmit={submit} noValidate>
      <h1>Sign in</h1>
      {login.isError && (
        <p className="alert" role="alert">
          {login.error.message}
        </p>
      )}
      <div className="field">
        <label className="field__label" htmlFor="email">
          Email
        </label>
        <input id="email" className="input" type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
      </div>
      <div className="field">
        <label className="field__label" htmlFor="password">
          Password
        </label>
        <input
          id="password"
          className="input"
          type="password"
          autoComplete="current-password"
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
      </div>
      <button className="button" type="submit" disabled={login.isPending || !email || !password}>
        {login.isPending ? 'Signing in…' : 'Sign in'}
      </button>
      <p className="muted">
        New here? <Link to="/register">Create an account</Link>
      </p>
    </form>
  )
}

export function RegisterPage() {
  const { user, signIn } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState({ displayName: '', email: '', password: '', accountType: 'USER' as 'USER' | 'OWNER' })

  const register = useMutation({
    mutationFn: () => api.register(form),
    onSuccess: (response) => {
      signIn(response)
      navigate(form.accountType === 'OWNER' ? '/my-listings' : '/', { replace: true })
    },
  })

  if (user) return <Navigate to="/" replace />

  const errors = register.error instanceof ApiError ? register.error.fieldErrors : {}
  const set = (key: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) => setForm({ ...form, [key]: e.target.value })
  const tooShort = form.password.length > 0 && form.password.length < 8

  return (
    <form
      className="card form-page"
      noValidate
      onSubmit={(e) => {
        e.preventDefault()
        register.mutate()
      }}
    >
      <h1>Create an account</h1>
      {register.isError && Object.keys(errors).length === 0 && (
        <p className="alert" role="alert">
          {register.error.message}
        </p>
      )}
      <fieldset className="segmented" style={{ border: 0, padding: 0, margin: 0 }}>
        <legend className="field__label" style={{ marginBottom: 6 }}>
          I want to
        </legend>
        <label>
          <span>
            <input type="radio" name="accountType" value="USER" checked={form.accountType === 'USER'} onChange={set('accountType')} />{' '}
            <strong>Find a home</strong>
          </span>
          <span className="muted">Search and save favorites</span>
        </label>
        <label>
          <span>
            <input type="radio" name="accountType" value="OWNER" checked={form.accountType === 'OWNER'} onChange={set('accountType')} />{' '}
            <strong>Rent out</strong>
          </span>
          <span className="muted">Publish your listings</span>
        </label>
      </fieldset>
      <Field id="displayName" label="Your name" error={errors.displayName}>
        <input id="displayName" className="input" autoComplete="name" maxLength={60} value={form.displayName} onChange={set('displayName')} />
      </Field>
      <Field id="email" label="Email" error={errors.email}>
        <input id="email" className="input" type="email" autoComplete="email" value={form.email} onChange={set('email')} />
      </Field>
      <Field id="password" label="Password (at least 8 characters)" error={errors.password ?? (tooShort ? 'Use at least 8 characters' : undefined)}>
        <input
          id="password"
          className="input"
          type="password"
          autoComplete="new-password"
          maxLength={72}
          value={form.password}
          onChange={set('password')}
        />
      </Field>
      <button className="button" type="submit" disabled={register.isPending || tooShort}>
        {register.isPending ? 'Creating account…' : 'Create account'}
      </button>
      <p className="muted">
        Already have an account? <Link to="/login">Sign in</Link>
      </p>
    </form>
  )
}

export function Field({ id, label, error, children }: { id: string; label: string; error?: string; children: React.ReactNode }) {
  return (
    <div className="field">
      <label className="field__label" htmlFor={id}>
        {label}
      </label>
      {children}
      {error && (
        <span className="field__error" id={`${id}-error`}>
          {error}
        </span>
      )}
    </div>
  )
}
