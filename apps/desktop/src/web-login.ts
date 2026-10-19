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
  const hit = cookies.find(cookie => cookie.name === SESSION_COOKIE_NAME && cookie.value.length > 0)
  return hit === undefined ? undefined : `${hit.name}=${hit.value}`
}

/**
 * Use a stored gate cookie only when this Host accepts it.
 * The cookie is bound to `host:port`, while the browser jar is not, so a cookie from
 * the previous listen port must be dropped and replaced by a login on this port.
 * @param input - stored cookie, login prompt, jar removal, and a probe of the current Host. The prompt receives the rejected cookie so it does not return that same value.
 * @returns a cookie the probe accepted.
 */
export async function resolveHostSessionCookie(input: {
  stored: () => Promise<string | undefined>
  prompt: (rejected: string | undefined) => Promise<string>
  forget: () => Promise<void>
  probe: (cookie: string) => Promise<boolean>
}): Promise<string> {
  const stored = await input.stored()
  if (stored !== undefined && await input.probe(stored)) return stored
  if (stored !== undefined) await input.forget()
  const minted = await input.prompt(stored)
  if (await input.probe(minted)) return minted
  await input.forget()
  throw new Error('desktop welcome: Web authentication failed (303)')
}

/**
 * Open the Host's login page in a modal child window and resolve once its
 * session cookie lands in the default session. A session already stored in
 * that session resolves without prompting.
 * @param hostUrl - running Host URL (its origin selects the login page).
 * @param rejected - a cookie this Host already refused. It is not reused.
 * @returns the `dsh-account` cookie as `name=value`, ready for Host forwarding.
 */
export async function promptWebHostLogin(hostUrl: string, rejected?: string): Promise<string> {
  const existing = await existingWebLoginCookie(hostUrl)
  if (existing !== undefined && existing !== rejected) return existing
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
        .then((next) => { if (next !== undefined && next !== rejected) finish(next) })
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
