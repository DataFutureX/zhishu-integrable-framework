/** Native login window that collects the Host web gate's session cookie. */

import { BrowserWindow, session } from 'electron'

const SESSION_COOKIE_NAME = 'dsh-account'
const POLL_MILLISECONDS = 200

/** Login URL under the Host origin; the gate serves it without a token. */
export function webLoginUrl(hostUrl: string): string {
  return new URL('/account/login', hostUrl).href
}

/** Read an already-stored gate session for this Host, when one exists. */
export async function existingWebLoginCookie(hostUrl: string): Promise<string | undefined> {
  const cookies = await session.defaultSession.cookies.get({ url: hostUrl, name: SESSION_COOKIE_NAME })
  const hit = cookies.find(cookie => cookie.name === SESSION_COOKIE_NAME)
  return hit === undefined ? undefined : `${hit.name}=${hit.value}`
}

/**
 * Open the Host's login page in a modal child window and resolve once its
 * session cookie lands in the default session. A session already stored in
 * that session resolves without prompting.
 * @param hostUrl - running Host URL (its origin selects the login page).
 * @returns the `dsh-account` cookie as `name=value`, ready for Host forwarding.
 */
export async function promptWebHostLogin(hostUrl: string): Promise<string> {
  const existing = await existingWebLoginCookie(hostUrl)
  if (existing !== undefined) return existing
  const window = new BrowserWindow({
    width: 480,
    height: 620,
    center: true,
    resizable: false,
    minimizable: false,
    maximizable: false,
    fullscreenable: false,
    autoHideMenuBar: true,
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      sandbox: true,
      webSecurity: true,
    },
  })
  return await new Promise<string>((resolve, reject) => {
    let settled = false
    const finish = (value: string | Error): void => {
      if (settled) return
      settled = true
      clearInterval(poll)
      window.removeListener('closed', onClosed)
      if (value instanceof Error) reject(value)
      else resolve(value)
    }
    const poll = setInterval(() => {
      void existingWebLoginCookie(hostUrl)
        .then((next) => { if (next !== undefined) finish(next) })
        .catch((_error: unknown) => {
          // A poll failure keeps the window open; loading or closing it reports the real failure.
        })
    }, POLL_MILLISECONDS)
    const onClosed = (): void => { finish(new Error('Desktop sign-in was cancelled')) }
    window.on('closed', onClosed)
    void window.loadURL(webLoginUrl(hostUrl)).catch((error: unknown) => {
      finish(error instanceof Error ? error : new Error(String(error)))
    })
  }).finally(() => {
    if (!window.isDestroyed()) window.close()
  })
}
