import { expect, test, type Page } from '@playwright/test'

async function expectNoHorizontalScroll(page: Page) {
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
  expect(overflow).toBeLessThanOrEqual(0)
}

test.describe('phone (375px)', () => {
  test('search page fits the screen and filters open from a button', async ({ page }) => {
    await page.goto('/')
    await expect(page.getByRole('link', { name: /per month/ }).first()).toBeVisible()
    await expectNoHorizontalScroll(page)

    const toggle = page.getByRole('button', { name: /^Filters/ })
    const kentron = page.getByRole('checkbox', { name: /^Kentron/ })
    await expect(kentron).toBeHidden()

    await toggle.click()
    await expect(toggle).toHaveAttribute('aria-expanded', 'true')
    await kentron.click()
    await expect(kentron).toBeChecked()
    await expect(page).toHaveURL(/district=KENTRON/)
    await expect(toggle).toHaveText('Filters (1)')
  })

  test('listing and form pages fit the screen', async ({ page }) => {
    await page.goto('/')
    await page.getByRole('link', { name: /per month/ }).first().click()
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
    await expectNoHorizontalScroll(page)

    await page.goto('/register')
    await expectNoHorizontalScroll(page)
  })

  test('content is fully visible with reduced motion', async ({ browser }) => {
    const context = await browser.newContext({ reducedMotion: 'reduce', viewport: { width: 375, height: 812 } })
    const page = await context.newPage()
    await page.goto('/')

    await expect(page.getByRole('heading', { name: /Find your next home/ })).toBeVisible()
    const animation = await page.locator('.backdrop__shape').first().evaluate((el) => getComputedStyle(el).animationName)
    expect(animation).toBe('none')
    await context.close()
  })
})
