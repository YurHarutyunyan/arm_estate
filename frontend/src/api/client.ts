import type { AuthResponse, Listing, ListingInput, Profile, SearchResult, Suggestions } from './types'

/** An RFC 9457 problem response from the API. */
export class ApiError extends Error {
  readonly status: number
  readonly fieldErrors: Record<string, string>

  constructor(status: number, detail: string, fieldErrors: Record<string, string> = {}) {
    super(detail)
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

let token: string | null = null
let onUnauthorized: () => void = () => {}

export function setToken(value: string | null) {
  token = value
}

/** Called when the API rejects our token, e.g. after it expired. */
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler
}

interface RequestOptions {
  method?: string
  body?: unknown
  headers?: Record<string, string>
  signal?: AbortSignal
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<{ data: T; response: Response }> {
  const headers: Record<string, string> = { Accept: 'application/json', ...options.headers }
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'
  if (token) headers.Authorization = `Bearer ${token}`

  const response = await fetch(path, {
    method: options.method ?? 'GET',
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
    signal: options.signal,
  })

  if (!response.ok) {
    let detail = 'Something went wrong. Please try again.'
    let fieldErrors: Record<string, string> = {}
    try {
      const problem = await response.json()
      detail = problem.detail ?? detail
      fieldErrors = problem.errors ?? {}
    } catch {
      // Not JSON, e.g. a proxy error page
    }
    if (response.status === 401 && token) onUnauthorized()
    throw new ApiError(response.status, detail, fieldErrors)
  }

  const data = response.status === 204 ? (undefined as T) : ((await response.json()) as T)
  return { data, response }
}

export interface SearchParams {
  q?: string
  district?: string[]
  rooms?: string[]
  buildingType?: string[]
  minPrice?: string
  maxPrice?: string
  furnished?: string
  petsAllowed?: string
  sort?: string
  size?: number
  cursor?: string
}

function toQuery(params: SearchParams) {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === '' || value === null) continue
    if (Array.isArray(value)) value.forEach((v) => query.append(key, v))
    else query.set(key, String(value))
  }
  return query.toString()
}

export const api = {
  search: (params: SearchParams, signal?: AbortSignal) =>
    request<SearchResult>(`/api/listings/search?${toQuery(params)}`, { signal }).then((r) => r.data),

  suggest: (q: string, signal?: AbortSignal) =>
    request<Suggestions>(`/api/listings/suggest?${toQuery({ q })}`, { signal }).then((r) => r.data),

  /** Returns the listing together with its ETag, needed to edit or delete it. */
  listing: async (id: string, signal?: AbortSignal) => {
    const { data, response } = await request<Listing>(`/api/listings/${encodeURIComponent(id)}`, { signal })
    return { listing: data, etag: response.headers.get('ETag') ?? '' }
  },

  createListing: (input: ListingInput) =>
    request<Listing>('/api/listings', { method: 'POST', body: input }).then((r) => r.data),

  updateListing: (id: string, etag: string, input: ListingInput) =>
    request<Listing>(`/api/listings/${encodeURIComponent(id)}`, {
      method: 'PUT',
      body: input,
      headers: { 'If-Match': etag },
    }).then((r) => r.data),

  deleteListing: (id: string, etag: string) =>
    request<void>(`/api/listings/${encodeURIComponent(id)}`, { method: 'DELETE', headers: { 'If-Match': etag } }),

  register: (body: { email: string; password: string; displayName: string; accountType: 'USER' | 'OWNER' }) =>
    request<AuthResponse>('/api/auth/register', { method: 'POST', body }).then((r) => r.data),

  login: (body: { email: string; password: string }) =>
    request<AuthResponse>('/api/auth/login', { method: 'POST', body }).then((r) => r.data),

  me: () => request<Profile>('/api/me').then((r) => r.data),

  myListings: () => request<Listing[]>('/api/me/listings').then((r) => r.data),

  favorites: () => request<Listing[]>('/api/me/favorites').then((r) => r.data),

  isFavorite: (id: string) =>
    request<void>(`/api/me/favorites/${encodeURIComponent(id)}`)
      .then(() => true)
      .catch((e: unknown) => {
        if (e instanceof ApiError && e.status === 404) return false
        throw e
      }),

  addFavorite: (id: string) => request<void>(`/api/me/favorites/${encodeURIComponent(id)}`, { method: 'PUT' }),

  removeFavorite: (id: string) => request<void>(`/api/me/favorites/${encodeURIComponent(id)}`, { method: 'DELETE' }),
}
