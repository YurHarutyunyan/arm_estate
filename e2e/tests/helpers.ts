import { expect, type APIRequestContext, type Page } from '@playwright/test'

export const PASSWORD = 'correct horse battery'

export function uniqueEmail(prefix: string) {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.com`
}

/** Signs up through the UI and lands signed in. */
export async function signUp(page: Page, kind: 'find' | 'rent') {
  const email = uniqueEmail(kind === 'rent' ? 'owner' : 'user')
  await page.goto('/register')
  await page.getByRole('radio', { name: kind === 'rent' ? /Rent out/ : /Find a home/ }).check()
  await page.getByLabel('Your name').fill(kind === 'rent' ? 'Test Owner' : 'Test User')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel(/^Password/).fill(PASSWORD)
  await page.getByRole('button', { name: 'Create account' }).click()
  await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible()
  return email
}

/** Fills the listing form with a unique title and publishes it. Returns the title. */
export async function publishListing(page: Page, title: string, price = 777) {
  await page.goto('/listings/new')
  await page.getByLabel('Title').fill(title)
  await page.getByLabel('District').selectOption('ARABKIR')
  await page.getByLabel('Address').fill('Komitas Ave 10')
  await page.getByLabel('Monthly price (USD)').fill(String(price))
  await page.getByLabel('Description').fill('Playwright test listing with a quiet courtyard.')
  await page.getByRole('checkbox', { name: 'balcony' }).check()
  await page.getByRole('button', { name: 'Publish listing' }).click()
  await expect(page.getByRole('heading', { level: 1, name: title })).toBeVisible()
}

export interface Session {
  token: string
  expiresAt: string
  user: { id: string; email: string; displayName: string; roles: string[] }
}

/** Creates an account through the API, without touching the browser. */
export async function registerViaApi(request: APIRequestContext, kind: 'find' | 'rent'): Promise<Session & { email: string }> {
  const email = uniqueEmail(kind === 'rent' ? 'owner' : 'user')
  const response = await request.post('/api/auth/register', {
    data: { email, password: PASSWORD, displayName: 'API user', accountType: kind === 'rent' ? 'OWNER' : 'USER' },
  })
  expect(response.status()).toBe(201)
  return { ...(await response.json()), email }
}

/** Starts the browser signed in as this session, the way the app stores it after signing in. */
export async function useSession(page: Page, session: Session) {
  await page.addInitScript((value) => {
    localStorage.setItem('yerevan-homes.session', value)
  }, JSON.stringify({ token: session.token, expiresAt: session.expiresAt, user: session.user }))
}

export const auth = (token: string) => ({ Authorization: `Bearer ${token}` })

export function listingBody(title: string, priceUsd = 650) {
  return {
    title,
    description: 'Created by the Playwright suite.',
    district: 'AVAN',
    address: 'Khudyakov St 3',
    priceUsd,
    rooms: 2,
    areaSqm: 55,
    floor: 2,
    totalFloors: 9,
    buildingType: 'PANEL',
    amenities: ['balcony'],
    furnished: true,
    petsAllowed: false,
    imageUrl: null,
  }
}

/** Publishes a listing through the API and returns its id and ETag. */
export async function createListingViaApi(request: APIRequestContext, token: string, title: string) {
  const response = await request.post('/api/listings', { data: listingBody(title), headers: auth(token) })
  expect(response.status()).toBe(201)
  return { id: (await response.json()).id as string, etag: response.headers()['etag'] }
}
