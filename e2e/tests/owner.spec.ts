import { expect, test } from '@playwright/test'
import { publishListing, signUp } from './helpers'

test.describe('owners', () => {
  test('publish, find in search, edit and delete a listing', async ({ page }) => {
    await signUp(page, 'rent')
    const title = `E2E loft ${Date.now()}`
    await publishListing(page, title)

    // MongoDB -> Elasticsearch sync makes it searchable within moments
    await expect(async () => {
      await page.goto(`/?q=${encodeURIComponent(title)}`)
      await expect(page.getByRole('link', { name: new RegExp(title) })).toBeVisible({ timeout: 1_000 })
    }).toPass({ timeout: 15_000 })

    await page.getByRole('link', { name: new RegExp(title) }).click()
    await page.getByRole('link', { name: 'Edit' }).click()
    await page.getByLabel('Monthly price (USD)').fill('888')
    await page.getByRole('button', { name: 'Save changes' }).click()
    await expect(page.getByText('$888')).toBeVisible()

    page.once('dialog', (dialog) => dialog.accept())
    await page.getByRole('button', { name: 'Delete' }).click()
    await expect(page).toHaveURL(/\/my-listings$/)
    await expect(page.getByRole('link', { name: new RegExp(title) })).toHaveCount(0)
  })

  test('two editors at once: the second sees a conflict instead of overwriting', async ({ browser }) => {
    const first = await browser.newPage()
    await signUp(first, 'rent')
    const title = `E2E conflict ${Date.now()}`
    await publishListing(first, title)
    const editUrl = first.url() + '/edit'

    // Same account in a second tab, both open the edit form on the same version
    const storage = await first.context().storageState()
    const secondContext = await browser.newContext({ storageState: storage })
    const second = await secondContext.newPage()
    await first.goto(editUrl)
    await second.goto(editUrl)
    await expect(first.getByLabel('Title')).toHaveValue(title)
    await expect(second.getByLabel('Title')).toHaveValue(title)

    await first.getByLabel('Monthly price (USD)').fill('901')
    await first.getByRole('button', { name: 'Save changes' }).click()
    await expect(first.getByText('$901')).toBeVisible()

    await second.getByLabel('Monthly price (USD)').fill('555')
    await second.getByRole('button', { name: 'Save changes' }).click()
    await expect(second.getByRole('alert')).toContainText('Someone else saved this listing')

    await second.getByRole('button', { name: 'Load the latest version' }).click()
    await expect(second.getByLabel('Monthly price (USD)')).toHaveValue('901')

    // Clean up so test listings don't pile up in the demo data
    await first.goto(editUrl.replace(/\/edit$/, ''))
    first.once('dialog', (dialog) => dialog.accept())
    await first.getByRole('button', { name: 'Delete' }).click()
    await expect(first).toHaveURL(/\/my-listings$/)

    await secondContext.close()
    await first.close()
  })

  test('people who only search cannot open owner pages', async ({ page }) => {
    await signUp(page, 'find')

    await expect(page.getByRole('link', { name: 'My listings' })).toHaveCount(0)
    await page.goto('/listings/new')
    await expect(page).toHaveURL(/\/$/)
  })
})
