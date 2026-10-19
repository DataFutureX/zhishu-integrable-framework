// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import { Welcome } from '../src/client/WelcomePage.tsx'
import { en, resolveDesktopLocale, type DesktopLocale } from '../src/locale.ts'
import type { WelcomeApi } from '../src/welcome-api.ts'

function api(locale: DesktopLocale, overrides: Partial<WelcomeApi> = {}): WelcomeApi & {
  analytics: ReturnType<typeof vi.fn>
  saveApiKey: ReturnType<typeof vi.fn>
  skip: ReturnType<typeof vi.fn>
} {
  return {
    ...locale,
    analytics: vi.fn(async () => {}),
    analyticsEnabled: async () => true,
    saveApiKey: vi.fn(async () => ({ ok: true as const })),
    skip: vi.fn(async () => {}),
    ...overrides,
  } as WelcomeApi & { analytics: ReturnType<typeof vi.fn>; saveApiKey: ReturnType<typeof vi.fn>; skip: ReturnType<typeof vi.fn> }
}

afterEach(() => { cleanup(); vi.restoreAllMocks() })

it.each([resolveDesktopLocale('en'), resolveDesktopLocale('zh-CN')])('renders the entry with API-key and set-up-later actions', (locale) => {
  const view = api(locale)
  render(<Welcome api={view} />)
  expect(screen.getByRole('button', { name: locale.messages.welcomeApiKey })).toBeTruthy()
  expect(screen.getByRole('button', { name: locale.messages.welcomeKeyLater })).toBeTruthy()
  expect(view.analytics).toHaveBeenCalledWith('auth_page_view', {})
})

it('saves a valid API key through the backend', async () => {
  const view = api(resolveDesktopLocale('en'))
  render(<Welcome api={view} />)
  fireEvent.click(screen.getByRole('button', { name: en.welcomeApiKey }))
  fireEvent.change(screen.getByLabelText(en.welcomeKeyPlaceholder), { target: { value: 'sk-example' } })
  fireEvent.click(screen.getByRole('button', { name: en.welcomeKeySave }))
  expect(view.saveApiKey).toHaveBeenCalledExactlyOnceWith('sk-example')
})

it('refuses an empty or malformed API key without calling the backend', async () => {
  const view = api(resolveDesktopLocale('en'))
  render(<Welcome api={view} />)
  fireEvent.click(screen.getByRole('button', { name: en.welcomeApiKey }))
  expect(screen.getByRole('button', { name: en.welcomeKeySave }).hasAttribute('disabled')).toBe(true)
  fireEvent.change(screen.getByLabelText(en.welcomeKeyPlaceholder), { target: { value: 'SK="x"' } })
  fireEvent.click(screen.getByRole('button', { name: en.welcomeKeySave }))
  expect(screen.getByRole('alert').textContent).toBe(en.welcomeKeyInvalid)
  expect(view.saveApiKey).not.toHaveBeenCalled()
})

it('enters the workspace without a key through the skip action', async () => {
  const view = api(resolveDesktopLocale('en'))
  render(<Welcome api={view} />)
  fireEvent.click(screen.getByRole('button', { name: en.welcomeKeyLater }))
  expect(view.skip).toHaveBeenCalledOnce()
})
