import { expect, test } from '@playwright/test'

test('la ruta de inicio presenta el formulario en español aunque la API no responda', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Qué bueno verte' })).toBeVisible()
  await expect(page.getByLabel('Tu nombre')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Iniciar sesión' })).toBeDisabled()
})
