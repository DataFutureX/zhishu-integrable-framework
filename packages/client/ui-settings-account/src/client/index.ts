/** Custom account settings registration, Desktop renderer only. */
import type { Context } from '@deepseek-ai/cordis'
import type {} from '@deepseek-ai/dsh-client-locale/client'
import type {} from '@deepseek-ai/dsh-client-ui-settings/client'
import type {} from '@deepseek-ai/dsh-client-ui-renderer/client'
import { CustomAccountMenu } from './CustomAccountMenu.tsx'
import { CustomAccountSection, type CustomAccountInjected, type CustomAccountSnapshot } from './CustomAccountSection.tsx'
import { en, zh, type AccountKey } from './locales.ts'

export type { CustomAccountInjected, CustomAccountSnapshot, CustomAccountSectionProps } from './CustomAccountSection.tsx'
export type { CustomAccountMenuProps } from './CustomAccountMenu.tsx'
export type { AccountKey } from './locales.ts'

declare module '@deepseek-ai/dsh-client-ui-slots' {
  interface LocaleNamespaceMap { 'settings.account': AccountKey }
}

/** Services required by account settings. */
export const inject = ['slots', 'locale']

/** One `/account/api/session` response, narrowed to the fields the UI reads. */
interface SessionPayload {
  readonly authenticated: boolean
  readonly user: { readonly username: string; readonly displayName: string } | null
}

function parseSession(value: unknown): SessionPayload | undefined {
  if (typeof value !== 'object' || value === null) return undefined
  const authenticated = (value as { authenticated?: unknown }).authenticated
  const user = (value as { user?: unknown }).user
  if (typeof authenticated !== 'boolean') return undefined
  if (user === null) return { authenticated, user: null }
  // The gate's `user` payload changed from a bare username string to
  // `{ username, displayName }`; accept both so a client and Host that have
  // not been rebuilt together still agree on who is signed in.
  if (typeof user === 'string' && user.length > 0) {
    return { authenticated, user: { username: user, displayName: user } }
  }
  if (typeof user !== 'object') return undefined
  const username = (user as { username?: unknown }).username
  const displayName = (user as { displayName?: unknown }).displayName
  if (typeof username !== 'string' || typeof displayName !== 'string') return undefined
  return { authenticated, user: { username, displayName } }
}

/**
 * Register the custom account section and launcher, both driven by the login
 * gate's session endpoint. @param ctx - client plugin context.
 */
export function apply(ctx: Context): void {
  if (!('dshDesktop' in globalThis)) return
  ctx.effect(() => ctx.locale.register('settings.account', { en, zh }), 'account: dictionaries')
  const t = ctx.locale.bind('settings.account')
  const listeners = new Set<() => void>()
  let snapshot: CustomAccountSnapshot = { status: 'loading', authenticated: false, username: null, displayName: null }
  const publish = (next: CustomAccountSnapshot): void => {
    snapshot = next
    for (const listener of listeners) listener()
  }
  let generation = 0
  const refresh = async (): Promise<void> => {
    const current = ++generation
    try {
      const response = await fetch('/account/api/session', { headers: { accept: 'application/json' } })
      if (!response.ok) throw new Error(`account session read failed: ${String(response.status)}`)
      const parsed = parseSession(await response.json())
      if (parsed === undefined) throw new Error('account session read failed: invalid payload')
      if (current === generation) {
        publish({
          status: 'ready', authenticated: parsed.authenticated,
          username: parsed.user?.username ?? null,
          displayName: parsed.user?.displayName ?? null,
        })
      }
    } catch (error) {
      if (current === generation) publish({ status: 'error', authenticated: false, username: null, displayName: null })
      ctx.logger.error(error)
    }
  }
  void refresh()
  const signOut = async (): Promise<void> => {
    const bridge = (globalThis as typeof globalThis & { dshDesktop?: { accountSignOut?: () => Promise<void> } }).dshDesktop
    if (bridge?.accountSignOut !== undefined) {
      await bridge.accountSignOut()
      return
    }
    window.location.assign('/account/logout')
  }
  const hooks = {
    account: {
      getSnapshot: () => snapshot,
      subscribe: (listener: () => void) => { listeners.add(listener); return () => { listeners.delete(listener) } },
    },
  }
  const injected = (): CustomAccountInjected => ({ hooks, signOut, refresh })
  ctx.slots.inject('settings.section', () => ctx.slots.register({
    name: 'settings.section', id: 'account', order: -10, label: () => t('nav'),
    locale: 'settings.account', inject: injected,
  }, CustomAccountSection))
  ctx.slots.inject('settings.launcher', () => ctx.slots.register({
    name: 'settings.launcher', locale: 'settings.account', inject: injected,
  }, CustomAccountMenu))
}
