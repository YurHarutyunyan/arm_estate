import { expect, test, type Page } from '@playwright/test'

const cards = (page: Page) => page.getByRole('link', { name: /per month/ })

/** Prices on the result cards, in the order shown. */
async function prices(page: Page) {
  const texts = await page.locator('.listing-card__price').allTextContents()
  return texts.map((t) => Number(t.replace(/\D/g, '')))
}

test.describe('filters and sorting', () => {
  test('combined filters only show matching homes', async ({ page }) => {
    await page.goto('/')
    await expect(cards(page).first()).toBeVisible()

    await page.getByRole('checkbox', { name: /^2 rooms/ }).click()
    await expect(page).toHaveURL(/rooms=2/)
    await page.getByRole('checkbox', { name: 'Furnished', exact: true }).click()
    await expect(page).toHaveURL(/furnished=true/)

    // Results update after the URL; wait until every card shown matches both filters
    await expect
      .poll(async () => {
        const metas = await page.locator('.listing-card__meta').allTextContents()
        return metas.length > 0 && metas.every((m) => m.startsWith('2 rooms'))
      })
      .toBe(true)
    for (const card of await cards(page).all()) {
      await expect(card.getByText('Furnished', { exact: true })).toBeVisible()
    }
  })

  test('sorting by price orders the results both ways', async ({ page }) => {
    await page.goto('/')
    await expect(cards(page).first()).toBeVisible()
    const sort = page.getByLabel('Sort by')

    await sort.selectOption('PRICE_ASC')
    await expect(page).toHaveURL(/sort=PRICE_ASC/)
    await expect.poll(async () => {
      const p = await prices(page)
      return p.length > 1 && p.every((v, i) => i === 0 || p[i - 1] <= v)
    }).toBe(true)

    await sort.selectOption('PRICE_DESC')
    await expect(page).toHaveURL(/sort=PRICE_DESC/)
    await expect.poll(async () => {
      const p = await prices(page)
      return p.length > 1 && p.every((v, i) => i === 0 || p[i - 1] >= v)
    }).toBe(true)
  })

  test('a price range keeps every result inside it', async ({ page }) => {
    await page.goto('/?minPrice=600&maxPrice=900&sort=PRICE_ASC')
    await expect(page.getByLabel('Minimum price')).toHaveValue('600')

    await expect.poll(async () => {
      const p = await prices(page)
      return p.length > 0 && p.every((v) => v >= 600 && v <= 900)
    }).toBe(true)
  })

  test('picking a district from autocomplete applies it as a filter', async ({ page }) => {
    await page.goto('/')
    const search = page.getByRole('combobox', { name: 'Search listings' })

    await search.fill('arab')
    await page.getByRole('option', { name: /Arabkir\s*District/ }).click()

    await expect(page).toHaveURL(/district=ARABKIR/)
    await expect(page.getByRole('checkbox', { name: /^Arabkir/ })).toBeChecked()
    await expect(search).toHaveValue('')
    await expect.poll(async () => {
      const metas = await page.locator('.listing-card__meta').allTextContents()
      return metas.length > 0 && metas.every((m) => m.endsWith('Arabkir'))
    }).toBe(true)
  })

  test('a minimum above the maximum is explained instead of searched', async ({ page }) => {
    await page.goto('/?minPrice=2000&maxPrice=1000')

    await expect(page.getByText('Minimum price is higher than maximum price.')).toBeVisible()
    await expect(page.getByText('Minimum is higher than maximum')).toBeVisible()
  })

  test('a search with no matches shows an empty state', async ({ page }) => {
    await page.goto('/?q=qqzzxxwwvv')

    await expect(page.getByRole('heading', { name: 'No homes match' })).toBeVisible()
    await expect(page.getByRole('heading', { name: /^0 homes/ })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Show more homes' })).toHaveCount(0)
  })

  test('Russian and Armenian searches find listings in those languages', async ({ page }) => {
    // A different word form than the seeded titles ("квартира"), so stemming or fuzziness must match it
    await page.goto(`/?q=${encodeURIComponent('квартиры')}`)
    await expect(cards(page).first()).toBeVisible()
    await expect(page.locator('.listing-card__title').first()).toHaveText(/[а-яё]/i)

    await page.goto(`/?q=${encodeURIComponent('բնակարան')}`)
    await expect(cards(page).first()).toBeVisible()
    await expect(page.locator('.listing-card__title').first()).toHaveText(/[Ա-և]/)
  })
})
