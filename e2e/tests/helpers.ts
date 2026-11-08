import { expect, type Page } from '@playwright/test'

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
  await page.getByLabel(/^Password/).fill('correct horse battery')
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
