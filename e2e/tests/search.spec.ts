import { expect, test } from '@playwright/test'

test.describe('search', () => {
  test('shows seeded homes with facet counts', async ({ page }) => {
    await page.goto('/')

    await expect(page.getByRole('heading', { name: /^\d[\d,]* homes?$/ })).toBeVisible()
    const filters = page.getByRole('complementary', { name: 'Filters' })
    await expect(filters.getByRole('group', { name: 'District' })).toBeVisible()
    await expect(filters.getByRole('checkbox', { name: /Kentron/ })).toBeVisible()
    await expect(page.getByRole('link', { name: /per month/ }).first()).toBeVisible()
  })

  test('finds listings despite a typo and highlights the match', async ({ page }) => {
    await page.goto('/')
    const search = page.getByRole('combobox', { name: 'Search listings' })

    await search.fill('sunny balkony')
    await search.press('Enter')

    await expect(page).toHaveURL(/q=sunny\+balkony/)
    const firstResult = page.getByRole('link', { name: /per month/ }).first()
    await expect(firstResult.locator('mark').filter({ hasText: /balcony/i })).toBeVisible()
  })

  test('filters live in the URL and survive a reload', async ({ page }) => {
    await page.goto('/')
    const total = async () => Number((await page.getByRole('heading', { name: /homes?$/ }).textContent())!.replace(/\D/g, ''))
    const before = await total()

    // The checkbox follows the URL, which updates one render later, so click and wait for the state
    await page.getByRole('checkbox', { name: /^Kentron/ }).click()
    await expect(page.getByRole('checkbox', { name: /^Kentron/ })).toBeChecked()
    await expect(page).toHaveURL(/district=KENTRON/)
    await expect.poll(total).toBeLessThan(before)
    const kentronOnly = await total()

    await page.reload()
    await expect(page.getByRole('checkbox', { name: /^Kentron/ })).toBeChecked()
    await expect.poll(total).toBe(kentronOnly)
  })

  test('other districts keep their counts while one is selected', async ({ page }) => {
    await page.goto('/?district=KENTRON')

    const arabkir = page.getByRole('checkbox', { name: /^Arabkir/ })
    await expect(arabkir).not.toBeChecked()
    // The label includes the count, which must be above zero
    await expect(page.locator('label', { has: arabkir }).locator('.check__count')).not.toHaveText('0')
  })

  test('autocomplete is usable with the keyboard', async ({ page }) => {
    await page.goto('/')
    const search = page.getByRole('combobox', { name: 'Search listings' })

    await search.fill('spacious')
    const options = page.getByRole('option')
    await expect(options.first()).toBeVisible()
    await expect(search).toHaveAttribute('aria-expanded', 'true')

    await search.press('ArrowDown')
    await expect(options.first()).toHaveAttribute('aria-selected', 'true')
    const title = (await options.first().locator('span').first().textContent())!
    await search.press('Enter')

    await expect(page).toHaveURL(/\/listings\/[0-9a-f]+$/)
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(title)

    await page.goBack()
    await page.getByRole('combobox', { name: 'Search listings' }).fill('spacious')
    await expect(page.getByRole('option').first()).toBeVisible()
    await page.keyboard.press('Escape')
    await expect(page.getByRole('listbox')).toBeHidden()
  })

  test('loading more results appends the next page without duplicates', async ({ page }) => {
    await page.goto('/?sort=PRICE_ASC')
    const cards = page.getByRole('link', { name: /per month/ })
    await expect(cards).toHaveCount(18)

    await page.getByRole('button', { name: 'Show more homes' }).click()
    await expect(cards).toHaveCount(36)

    const names = await cards.evaluateAll((links) => links.map((l) => l.getAttribute('href')))
    expect(new Set(names).size).toBe(names.length)
  })

  test('unknown pages and listings show a 404 page', async ({ page }) => {
    await page.goto('/no-such-page')
    await expect(page.getByRole('heading', { name: 'Page not found' })).toBeVisible()

    await page.goto('/listings/000000000000000000000000')
    await expect(page.getByRole('heading', { name: 'Page not found' })).toBeVisible()
  })
})
