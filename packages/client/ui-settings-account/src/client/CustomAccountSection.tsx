/** Custom account settings: display the gate session's display name only. */

import type { HostObservable, InjectFace, PropsLocale, PropsRuntime } from '@deepseek-ai/dsh-client-ui-slots'
import { AccountAvatar } from './AccountAvatar.tsx'
import css from './AccountSection.module.css'

/** Session identity read from the login gate's `/account/api/session`. */
export interface CustomAccountSnapshot {
  readonly status: 'loading' | 'ready' | 'error'
  readonly authenticated: boolean
  readonly username: string | null
  readonly displayName: string | null
}

/** Dependencies injected into the account section and launcher. */
export interface CustomAccountInjected {
  hooks: {
    account: HostObservable<CustomAccountSnapshot>
  }
  /** @returns after the gate session cookie is forgotten and the login page is shown. */
  signOut: () => Promise<void>
  /** @returns after one gate-session re-read. */
  refresh: () => Promise<void>
}

export type CustomAccountSectionProps =
  PropsRuntime<'settings.section'> & PropsLocale<'settings.account'> & InjectFace<CustomAccountInjected>

/**
 * Render the account settings page from the gate session.
 * @param props - slot-delivered hooks and section copy.
 * @returns the account card.
 */
export function CustomAccountSection({ useAccount, t }: CustomAccountSectionProps) {
  const account = useAccount(value => value)
  const name = account.status === 'ready' && account.username !== null
    ? account.username
    : account.status === 'ready' && account.displayName !== null
      ? account.displayName
      : account.status === 'error' ? t('failed') : t('loading')
  return (
    <section className={css.section} aria-label={t('nav')}>
      <div className={css.card}>
        <div className={css.identity}>
          <span className={css.avatar}><AccountAvatar /></span>
          <div className={css.identityCopy}>
            <span className={css.name}>{name}</span>
            {account.status === 'ready' && !account.authenticated
              ? <span className={css.status} role="status">{t('signedOut')}</span>
              : null}
          </div>
        </div>
      </div>
    </section>
  )
}
