// @vitest-environment jsdom
/** Custom account registration and session-driven identity. */
import { afterEach, expect, vi } from 'vitest'
import { createClientTest, type TestClient, webApp } from '@deepseek-ai/dsh-client-test-runtime/src/assembly/index.ts'
import { resolveSlotLabel } from '@deepseek-ai/dsh-client-ui-slots'
import type { CustomAccountInjected } from '../src/client/CustomAccountSection.tsx'

const it = createClientTest({ roster: webApp })

function injected(c: TestClient): CustomAccountInjected {
  const face: object = c.ctx.slots.entries('settings.launcher')[0]!.inject!()
  return face as CustomAccountInjected
}

afterEach(() => { vi.unstubAllGlobals() })

it('keeps the custom account UI inactive in a plain browser', async ({ start }) => {
  const c = await start()
  expect(c.ctx.slots.entries('settings.launcher')).toHaveLength(0)
  expect(c.ctx.slots.entries('settings.section').some(entry => entry.options.id === 'account')).toBe(false)
}, 60_000)

it('registers the section and launcher on Desktop and reads the gate session', async ({ start }) => {
  vi.stubGlobal('dshDesktop', {})
  const fetch = vi.fn(async () => Response.json({
    authenticated: true, user: { username: 'admin', displayName: '自定义账号' }, launchTokenAllowed: false,
  }))
  vi.stubGlobal('fetch', fetch)
  const c = await start()
  const section = c.ctx.slots.entries('settings.section').find(entry => entry.options.id === 'account')!
  expect(section).toBeDefined()
  expect(resolveSlotLabel(section.options.label)).toBe('Account')
  const actions = injected(c)
  await vi.waitFor(() => {
    expect(actions.hooks.account.getSnapshot()).toMatchObject({
      status: 'ready', authenticated: true, username: 'admin', displayName: '自定义账号',
    })
  })
  expect(fetch).toHaveBeenCalledWith('/account/api/session', expect.anything())
}, 60_000)

it('reports a refused session read without crashing the section', async ({ start }) => {
  vi.stubGlobal('dshDesktop', {})
  vi.stubGlobal('fetch', vi.fn(async () => new Response(null, { status: 401 })))
  const c = await start()
  const actions = injected(c)
  await vi.waitFor(() => { expect(actions.hooks.account.getSnapshot().status).toBe('error') })
}, 60_000)
