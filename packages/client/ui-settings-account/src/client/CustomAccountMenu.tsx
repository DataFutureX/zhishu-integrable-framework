/** Sidebar account launcher showing the gate session's display name and sign-out. */

import { useEffect, useRef, useState } from 'react'
import { IconSettingsOutlineMedium, Menu } from '@deepseek-ai/dsh-client-ui-primitives'
import type { InjectFace, PropsLocale, PropsRuntime } from '@deepseek-ai/dsh-client-ui-slots'
import type { CustomAccountInjected } from './CustomAccountSection.tsx'
import { AccountAvatar } from './AccountAvatar.tsx'
import { LogoutIcon } from './LogoutIcon.tsx'
import css from './AccountMenu.module.css'

export type CustomAccountMenuProps =
  PropsRuntime<'settings.launcher'> & PropsLocale<'settings.account'> & InjectFace<CustomAccountInjected>

/**
 * Render the sidebar account launcher from the gate session.
 * @param props - launcher geometry, settings navigation, and section copy.
 * @returns the launcher.
 */
export function CustomAccountMenu({
  useAccount, wide, settingsShortcut, settingsOpen, openSettings, signOut, refresh, t,
}: CustomAccountMenuProps) {
  const account = useAccount(value => value)
  const settingsWasOpen = useRef(false)
  useEffect(() => {
    if (settingsOpen && !settingsWasOpen.current) void refresh()
    settingsWasOpen.current = settingsOpen
  }, [refresh, settingsOpen])
  const signedIn = account.status === 'ready' && account.authenticated
  const name = account.status === 'ready' && account.username !== null
    ? account.username
    : account.status === 'ready' && account.displayName !== null
      ? account.displayName
      : t('loading')
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  const trigger = useRef<HTMLButtonElement>(null)
  const requestSignOut = async (): Promise<void> => {
    setBusy(true)
    setOpen(false)
    try { await signOut() } finally { setBusy(false) }
  }
  return (
    <div className={css.root}>
      <Menu open={open} side="top" portal autoFocus className={css.anchor}
        anchor={(
          <button ref={trigger} type="button" className={css.trigger} data-collapsed={!wide} aria-label={t('menu')}
            aria-haspopup="menu" aria-expanded={open} onClick={() => { setOpen(value => !value) }}>
            <span className={css.avatar}><AccountAvatar /></span>
            {wide && <span className={css.label}>{name}</span>}
          </button>
        )}
        items={[
          { id: 'settings', label: t('settings'), icon: <IconSettingsOutlineMedium size={16} />,
            ...(settingsShortcut === undefined ? {} : { shortcut: settingsShortcut }) },
          ...(signedIn ? [{ id: 'signout', label: t('signOut'), icon: <LogoutIcon />, disabled: busy }] : []),
        ]}
        onClose={() => { setOpen(false) }}
        onSelect={(id) => {
          if (id === 'settings') { setOpen(false); trigger.current?.focus(); openSettings() }
          else void requestSignOut()
        }} />
    </div>
  )
}
