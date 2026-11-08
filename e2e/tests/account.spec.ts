import { expect, test } from '@playwright/test'
import { signUp } from './helpers'

test.describe('accounts and favorites', () => {
  test('save a home, see it in favorites, then remove it', async ({ page }) => {
    await signUp(page, 'find')
    await page.goto('/')
    const card = page.getByRole('link', { name: /per month/ }).first()
    const href = (await card.getAttribute('href'))!
    await card.click()

    const save = page.getByRole('button', { name: '♡ Save' })
    await save.click()
    await expect(page.getByRole('button', { name: '♥ Saved' })).toHaveAttribute('aria-pressed', 'true')

    await page.getByRole('link', { name: 'Favorites' }).click()
    await expect(page.locator(`a[href="${href}"]`)).toBeVisible()

    await page.locator(`a[href="${href}"]`).click()
    await page.getByRole('button', { name: '♥ Saved' }).click()
    await page.getByRole('link', { name: 'Favorites' }).click()
    await expect(page.getByRole('heading', { name: 'Nothing saved yet' })).toBeVisible()
  })

  test('saving while signed out asks to sign in and comes back', async ({ page }) => {
    await page.goto('/')
    await page.getByRole('link', { name: /per month/ }).first().click()
    const listingUrl = page.url()

    await page.getByRole('button', { name: /Sign in to save/ }).click()
    await expect(page).toHaveURL(/\/login$/)

    // Use a fresh account, then sign in from the redirect
    const email = await signUp(page, 'find')
    await page.getByRole('button', { name: 'Sign out' }).click()
    await page.goto(listingUrl)
    await page.getByRole('button', { name: /Sign in to save/ }).click()
    await page.getByLabel('Email').fill(email)
    await page.getByLabel('Password').fill('correct horse battery')
    await page.getByRole('button', { name: 'Sign in' }).click()

    await expect(page).toHaveURL(listingUrl)
    await expect(page.getByRole('button', { name: '♡ Save' })).toBeVisible()
  })

  test('wrong password shows an error and stays signed out', async ({ page }) => {
    await page.goto('/login')
    await page.getByLabel('Email').fill('nobody@example.com')
    await page.getByLabel('Password').fill('not the password')
    await page.getByRole('button', { name: 'Sign in' }).click()

    await expect(page.getByRole('alert')).toHaveText('Invalid email or password')
    await expect(page.getByRole('link', { name: 'Sign in' })).toBeVisible()
  })

  test('signing up validates fields', async ({ page }) => {
    await page.goto('/register')
    await page.getByLabel(/^Password/).fill('short')

    await expect(page.getByText('Use at least 8 characters')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Create account' })).toBeDisabled()
  })
})
