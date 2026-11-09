import { expect, test } from '@playwright/test'
import { auth, createListingViaApi, listingBody, registerViaApi, useSession } from './helpers'

test.describe('security', () => {
  test('pages and assets are served with security headers', async ({ request }) => {
    const page = await request.get('/')
    const html = await page.text()
    const script = html.match(/src="(\/assets\/[^"]+\.js)"/)?.[1]
    expect(script, 'index.html references a hashed script').toBeTruthy()

    // A deep link is answered by the single-page fallback, an asset by the static location
    for (const path of ['/', '/listings/abc', script!]) {
      const headers = (await request.get(path)).headers()
      expect(headers['content-security-policy'], path).toContain("default-src 'self'")
      expect(headers['x-frame-options'], path).toBe('DENY')
      expect(headers['x-content-type-options'], path).toBe('nosniff')
    }
  })

  test('only the owner can change a listing, and only the latest version', async ({ request }) => {
    const owner = await registerViaApi(request, 'rent')
    const otherOwner = await registerViaApi(request, 'rent')
    const searcher = await registerViaApi(request, 'find')
    const { id, etag } = await createListingViaApi(request, owner.token, `E2E guarded ${Date.now()}`)
    const body = listingBody('Taken over', 100)

    // Not signed in, wrong role, wrong owner
    expect((await request.post('/api/listings', { data: body })).status()).toBe(401)
    expect((await request.post('/api/listings', { data: body, headers: auth(searcher.token) })).status()).toBe(403)
    const foreign = await request.put(`/api/listings/${id}`, { data: body, headers: { ...auth(otherOwner.token), 'If-Match': etag } })
    expect(foreign.status()).toBe(403)
    const foreignDelete = await request.delete(`/api/listings/${id}`, { headers: { ...auth(otherOwner.token), 'If-Match': etag } })
    expect(foreignDelete.status()).toBe(403)

    // The owner must say which version they saw
    expect((await request.put(`/api/listings/${id}`, { data: body, headers: auth(owner.token) })).status()).toBe(428)
    const stale = await request.put(`/api/listings/${id}`, { data: body, headers: { ...auth(owner.token), 'If-Match': '"99"' } })
    expect(stale.status()).toBe(412)

    // Nothing above changed it
    expect((await (await request.get(`/api/listings/${id}`)).json()).priceUsd).toBe(650)
    expect((await request.delete(`/api/listings/${id}`, { headers: { ...auth(owner.token), 'If-Match': etag } })).status()).toBe(204)
  })

  test("another owner sees no edit controls on someone else's listing", async ({ page, request }) => {
    const owner = await registerViaApi(request, 'rent')
    const otherOwner = await registerViaApi(request, 'rent')
    const title = `E2E not yours ${Date.now()}`
    const { id, etag } = await createListingViaApi(request, owner.token, title)

    await useSession(page, otherOwner)
    await page.goto(`/listings/${id}`)
    await expect(page.getByRole('heading', { level: 1, name: title })).toBeVisible()
    await expect(page.getByRole('button', { name: '♡ Save' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Edit' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: 'Delete' })).toHaveCount(0)

    await request.delete(`/api/listings/${id}`, { headers: { ...auth(owner.token), 'If-Match': etag } })
  })

  test('forged or tampered tokens and cursors are rejected cleanly', async ({ request }) => {
    const user = await registerViaApi(request, 'find')
    // Same header and claims, different signature
    const [header, claims] = user.token.split('.')
    const forged = `${header}.${claims}.${'A'.repeat(43)}`
    expect((await request.get('/api/me', { headers: auth(forged) })).status()).toBe(401)
    expect((await request.get('/api/me', { headers: auth(user.token) })).status()).toBe(200)

    // A cursor with the right shape but wrong value types is a client error, not a crash
    const cursor = Buffer.from(JSON.stringify({ s: 'NEWEST', a: ['abc', 'x'] })).toString('base64url')
    const response = await request.get(`/api/listings/search?cursor=${cursor}`)
    expect(response.status()).toBe(400)
    expect((await response.json()).detail).toBe('cursor is invalid')
  })
})
