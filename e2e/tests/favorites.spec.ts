import { expect, test } from '@playwright/test'
import { auth, createListingViaApi, PASSWORD, registerViaApi, useSession } from './helpers'

test.describe('favorites across accounts', () => {
  test('a deleted listing disappears from people who saved it', async ({ page, request }) => {
    const owner = await registerViaApi(request, 'rent')
    const searcher = await registerViaApi(request, 'find')
    const title = `E2E soon gone ${Date.now()}`
    const { id, etag } = await createListingViaApi(request, owner.token, title)

    await useSession(page, searcher)
    await page.goto(`/listings/${id}`)
    await page.getByRole('button', { name: '♡ Save' }).click()
    await expect(page.getByRole('button', { name: '♥ Saved' })).toBeVisible()
    await page.getByRole('link', { name: 'Favorites' }).click()
    await expect(page.getByRole('link', { name: new RegExp(title) })).toBeVisible()

    expect((await request.delete(`/api/listings/${id}`, { headers: { ...auth(owner.token), 'If-Match': etag } })).status()).toBe(204)

    await page.reload()
    await expect(page.getByRole('heading', { name: 'Nothing saved yet' })).toBeVisible()
    await page.goto(`/listings/${id}`)
    await expect(page.getByRole('heading', { name: 'Page not found' })).toBeVisible()
  })

  test('favorites need an account and return there after signing in', async ({ page, request }) => {
    const searcher = await registerViaApi(request, 'find')

    await page.goto('/favorites')
    await expect(page).toHaveURL(/\/login$/)
    await page.getByLabel('Email').fill(searcher.email)
    await page.getByLabel('Password').fill(PASSWORD)
    await page.getByRole('button', { name: 'Sign in' }).click()

    await expect(page).toHaveURL(/\/favorites$/)
    await expect(page.getByRole('heading', { name: 'Saved homes' })).toBeVisible()
  })
})
