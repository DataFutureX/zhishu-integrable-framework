/**
 * dsh-plugin-account — a username/password front door for the DeepSeek Harness
 * Web UI.
 *
 * DSH's stock browser login is a process launch token printed in the startup
 * URL, exchanged once for a signed cookie; a browser without it receives a
 * plain-text `401`. This plugin replaces that front door with an HTML login
 * form and its own signed session cookie:
 *
 *   GET  /account/login        login form
 *   POST /account/login        verify credentials, mint the session cookie
 *   GET  /account/logout       clear the session cookie
 *   GET  /account/api/session  current session as JSON
 *
 * It takes over two methods of the `connection` service:
 *
 *   authorizeIndex    — an index request without a valid session redirects to
 *                       the login form instead of rendering the app or 401ing;
 *   requestRejection  — a request whose session is valid is admitted without
 *                       DSH's own cookie. The Host/Origin fence still runs
 *                       first, so a 403 is never overridden.
 *
 * Credentials are configuration; the first version hardcodes admin/admin123 in
 * `cordis.patch.yml`. Nothing here requires a client bundle or a rebuild.
 *
 * While `allowLaunchToken` is false this plugin's session is the ONLY
 * credential: a stock `dsh-auth-*` cookie minted before the gate existed is
 * rejected on every door, and logout expires it too.
 *
 * Reviewed 2026-10-15: fixes for the stock-cookie pass-through, the
 * dot-segment open redirect in `next`, config-fallback, limiter growth, and
 * oversized-body handling.
 */

import { Buffer } from 'node:buffer'
import { createHash } from 'node:crypto'
import { loginErrorText, renderLoginPage } from './lib/login-page.js'
import {
  cookieAuthority,
  defaultSecretFile,
  headerValue,
  loadOrCreateSecret,
  secureEquals,
  SessionStore,
} from './lib/session.js'

/** Stable cordis plugin name. */
export const name = 'dsh-plugin-account'

/** Services required before the gate can be installed. */
export const inject = ['connection', 'webServer']

const LOGIN_PATH = '/account/login'
const LOGOUT_PATH = '/account/logout'
const SESSION_PATH = '/account/api/session'
const MAX_BODY_BYTES = 16 * 1024
const HTML_MIME = 'text/html; charset=utf-8'
const JSON_MIME = 'application/json; charset=utf-8'
const DEFAULT_USERS = Object.freeze([{ username: 'admin', password: 'admin123' }])

/** Read the validated plugin options. */
function resolveOptions(config) {
  const raw = config ?? {}
  const configured = raw.users
  // An explicitly configured list must be usable: a typo'd key or an empty
  // array fails the load loudly instead of silently opening admin/admin123.
  if (configured !== undefined && configured !== null && (!Array.isArray(configured) || configured.length === 0)) {
    throw new Error('dsh-plugin-account: config.users must be a non-empty array of { username, password }; remove the key to accept the built-in default')
  }
  const usingDefaultUsers = configured === undefined || configured === null
  const users = usingDefaultUsers
    ? DEFAULT_USERS.map(user => ({ ...user }))
    : configured.map((user, index) => {
      const username = typeof user?.username === 'string' ? user.username : ''
      const password = typeof user?.password === 'string' ? user.password : ''
      if (username.length === 0 || password.length === 0) {
        throw new Error(`dsh-plugin-account: users[${String(index)}] needs a non-empty username and password`)
      }
      return { username, password }
    })
  const positive = (value, fallback) =>
    (typeof value === 'number' && Number.isFinite(value) && value > 0 ? value : fallback)
  const rawPublicBase = typeof raw.publicBasePath === 'string' ? raw.publicBasePath.trim() : ''
  // A mount prefix becomes a Location and a form action, so only a clean,
  // relative path segment list is accepted — never a backslash, control
  // character, or a second leading slash a browser could read as another host.
  if (rawPublicBase !== '' && !/^\/(?:[A-Za-z0-9._~-]+\/?)*$/u.test(rawPublicBase)) {
    throw new Error(`dsh-plugin-account: publicBasePath ${JSON.stringify(rawPublicBase)} is not a clean absolute mount prefix such as /ui`)
  }
  const publicBasePath = rawPublicBase.replace(/\/+$/u, '')
  return {
    users,
    usingDefaultUsers,
    publicBasePath,
    allowLaunchToken: raw.allowLaunchToken === true,
    sessionDays: positive(raw.sessionDays, 7),
    sessionFile: typeof raw.sessionFile === 'string' && raw.sessionFile.length > 0
      ? raw.sessionFile
      : defaultSecretFile(),
    cookieName: typeof raw.cookieName === 'string' && raw.cookieName.length > 0
      ? raw.cookieName
      : 'dsh-account',
    title: typeof raw.title === 'string' && raw.title.trim().length > 0
      ? raw.title
      : '登录 DeepSeek Harness',
    displayName: typeof raw.displayName === 'string' && raw.displayName.trim().length > 0
      ? raw.displayName.trim()
      : '自定义账号',
    maxAttempts: Math.max(1, Math.floor(positive(raw.maxAttempts, 5))),
    lockoutSeconds: positive(raw.lockoutSeconds, 30),
  }
}

/** Match one submitted credential against every configured user. */
function matchUser(users, username, password) {
  let matched
  for (const user of users) {
    // Both comparisons run for every row: no early exit, no timing oracle.
    const usernameMatches = secureEquals(user.username, username)
    const passwordMatches = secureEquals(user.password, password)
    if (usernameMatches && passwordMatches) matched = user
  }
  return matched
}

/** Keep only a same-origin absolute path, dropping any launch token. */
function safeNext(value, fallback = '/') {
  if (typeof value !== 'string' || value.length === 0) return fallback
  if (!value.startsWith('/') || value.startsWith('//') || value.includes('\\')) return fallback
  let path
  try {
    const url = new URL(value, 'http://dsh.invalid')
    url.searchParams.delete('token')
    path = `${url.pathname}${url.search}`
  } catch {
    return fallback
  }
  // Re-check after WHATWG normalisation: '/..//evil.example/p' resolves to
  // '//evil.example/p', which a browser reads as a protocol-relative URL to
  // another host. Checking only the raw input misses every such dot-segment.
  if (!path.startsWith('/') || path.startsWith('//') || path.includes('\\')) return fallback
  if (/[\u0000-\u001f\u007f]/u.test(path)) return fallback
  return path
}

/** Build the login URL preserving `next` and an error code. */
function loginUrl(next, error, publicBasePath = '') {
  const params = new URLSearchParams()
  if (typeof next === 'string' && next.length > 0 && next !== '/') params.set('next', next)
  if (error !== undefined) params.set('error', error)
  const query = params.toString()
  const path = `${publicBasePath}${LOGIN_PATH}`
  return query.length === 0 ? path : `${path}?${query}`
}

/**
 * The cookie name DSH's own `BrowserAuth` mints for this authority.
 *
 * Logging out expires it as well: a browser that exchanged the launch token
 * before this gate was installed would otherwise keep holding a credential the
 * plugin never issued, and one that still admits requests whenever the plugin
 * is not loaded.
 */
function legacySessionCookieName(headers, secure) {
  const host = headerValue(headers, 'host')
  if (typeof host !== 'string' || host.length === 0) return undefined
  try {
    const authority = secure ? new URL(`https://${host}`).origin : new URL(`http://${host}`).host
    return `dsh-auth-${createHash('sha256').update(authority).digest('base64url')}`
  } catch {
    return undefined
  }
}

/** Reconstruct this request's HTTP(S) origin from the Host header and listener protocol. */
function requestOrigin(headers, secure) {
  const host = headerValue(headers, 'host')
  if (typeof host !== 'string' || host.length === 0) return undefined
  try {
    return new URL(`${secure ? 'https:' : 'http:'}//${host}`).origin
  } catch {
    return undefined
  }
}

/**
 * Accept a credential submission only from the origin that served the login
 * page. When the browser sends `Origin` — every same-origin POST does — that
 * value is authoritative and both sides are compared as normalized URL
 * origins, so an explicit default port (`:80` / `:443`), letter casing, or a
 * `Sec-Fetch-Site` label of `same-site`/`cross-site` never decide the answer.
 * Requests without `Origin` fall back to the `Sec-Fetch-Site` label, which is
 * what rejects a cross-site top-level logout navigation.
 */
function isSameOrigin(req, secure) {
  const origin = headerValue(req.headers, 'origin')
  if (origin === undefined) {
    const site = headerValue(req.headers, 'sec-fetch-site')
    return !(typeof site === 'string' && site === 'cross-site')
  }
  const expected = requestOrigin(req.headers, secure)
  if (expected === undefined) return false
  try {
    return new URL(origin).origin === expected
  } catch {
    return false
  }
}

/**
 * Buffer a small request body, refusing anything oversized.
 *
 * An oversized body is drained, never destroyed: the caller answers 413 and
 * the client still gets to read that response. Nothing is retained past the
 * limit, so memory stays bounded however large the body is.
 */
function readBody(req, limit = MAX_BODY_BYTES) {
  return new Promise((resolve) => {
    const chunks = []
    let size = 0
    let overflow = false
    let settled = false
    const finish = (value) => {
      if (settled) return
      settled = true
      resolve(value)
    }
    req.on('data', (chunk) => {
      if (settled) return
      size += chunk.length
      if (size > limit) {
        overflow = true
        chunks.length = 0
        return
      }
      chunks.push(chunk)
    })
    req.on('end', () => {
      if (overflow) finish({ ok: false, reason: 'too-large' })
      else finish({ ok: true, text: Buffer.concat(chunks).toString('utf8') })
    })
    req.on('error', () => finish({ ok: false, reason: 'aborted' }))
  })
}

/** Parse a submitted form body. */
function parseSubmission(contentType, text) {
  const mediaType = (contentType ?? '').split(';', 1)[0].trim().toLowerCase()
  if (mediaType === 'application/json') {
    try {
      const parsed = JSON.parse(text)
      if (typeof parsed !== 'object' || parsed === null) return undefined
      return { username: String(parsed.username ?? ''), password: String(parsed.password ?? ''), next: parsed.next }
    } catch {
      return undefined
    }
  }
  if (mediaType === 'application/x-www-form-urlencoded' || mediaType === '') {
    const params = new URLSearchParams(text)
    return { username: params.get('username') ?? '', password: params.get('password') ?? '', next: params.get('next') ?? undefined }
  }
  return undefined
}

/** Per-origin+username failure backoff for credential submission. */
class LoginLimiter {
  constructor(maxAttempts, lockoutSeconds) {
    this.maxAttempts = maxAttempts
    this.baseMilliseconds = lockoutSeconds * 1000
    this.maxEntries = 4096
    this.entries = new Map()
  }

  retryAfterSeconds(key) {
    const entry = this.entries.get(key)
    if (entry === undefined) return 0
    const remaining = entry.until - Date.now()
    return remaining > 0 ? Math.ceil(remaining / 1000) : 0
  }

  fail(key) {
    const now = Date.now()
    const entry = this.entries.get(key) ?? { count: 0, until: 0, lastFailure: 0 }
    entry.count += 1
    entry.lastFailure = now
    if (entry.count >= this.maxAttempts) {
      const step = entry.count - this.maxAttempts
      entry.until = now + Math.min(this.baseMilliseconds * 2 ** step, 15 * 60 * 1000)
    }
    this.entries.set(key, entry)
    if (this.entries.size > this.maxEntries) this.evict()
  }

  succeed(key) {
    this.entries.delete(key)
  }

  /**
   * Bound the table: five unauthenticated POSTs per key would otherwise grow
   * it without limit. Live locks survive ordinary pressure; only an entirely
   * locked-out table falls back to dropping the least recently active keys.
   */
  evict() {
    const now = Date.now()
    const idleMilliseconds = 15 * 60 * 1000
    for (const [key, entry] of this.entries) {
      if (entry.until <= now && now - entry.lastFailure > idleMilliseconds) this.entries.delete(key)
    }
    if (this.entries.size <= this.maxEntries) return
    const target = Math.floor(this.maxEntries * 0.75)
    const oldestFirst = [...this.entries.entries()].sort((a, b) => a[1].lastFailure - b[1].lastFailure)
    const releasable = oldestFirst.filter(([, entry]) => entry.until <= now)
    for (const [key] of (releasable.length >= this.entries.size - target ? releasable : oldestFirst)) {
      if (this.entries.size <= target) break
      this.entries.delete(key)
    }
  }
}

function send(res, status, headers, body) {
  const payload = body === undefined ? undefined : Buffer.from(body, 'utf8')
  res.writeHead(status, payload === undefined ? headers : { ...headers, 'content-length': payload.byteLength })
  res.end(payload)
}

function sendHtml(res, body) {
  send(res, 200, {
    'content-type': HTML_MIME,
    'cache-control': 'no-store',
    // `no-referrer` makes browsers submit the login form with `Origin: null`,
    // which `isSameOrigin` refuses. `strict-origin` keeps a real Origin for
    // the same-origin POST while never leaking path or query cross-origin.
    'referrer-policy': 'strict-origin',
    'x-frame-options': 'DENY',
    'x-content-type-options': 'nosniff',
  }, body)
}

function sendJson(res, body) {
  send(res, 200, { 'content-type': JSON_MIME, 'cache-control': 'no-store' }, JSON.stringify(body))
}

/** The plugin's HTTP surface. */
function createHandlers(state, options, isSecure) {
  const limiter = new LoginLimiter(options.maxAttempts, options.lockoutSeconds)
  // Every location the browser follows carries the deployment's mount prefix;
  // route paths stay origin-root because a mount proxy strips the prefix.
  const toBrowser = (path) => `${options.publicBasePath}${path}`

  const serveLogin = (req, res) => {
    const url = new URL(req.url ?? '/', 'http://dsh.invalid')
    const next = safeNext(url.searchParams.get('next'), '/')
    if (state.sessions.isAuthenticated(req.headers)) {
      send(res, 303, { 'cache-control': 'no-store', location: toBrowser(next), 'referrer-policy': 'no-referrer' })
      return
    }
    sendHtml(res, renderLoginPage({
      action: toBrowser(LOGIN_PATH),
      next,
      error: loginErrorText(url.searchParams.get('error')),
      title: options.title,
    }))
  }

  const submitLogin = async (req, res) => {
    if (!isSameOrigin(req, isSecure())) {
      const observed = {
        origin: headerValue(req.headers, 'origin'),
        host: headerValue(req.headers, 'host'),
        site: headerValue(req.headers, 'sec-fetch-site'),
      }
      state.onError(new Error(
        'dsh-plugin-account: rejected cross-origin login'
        + ` (origin=${String(observed.origin)}`
        + `, host=${String(observed.host)}`
        + `, sec-fetch-site=${String(observed.site)})`,
      ))
      // Echo the observed values in the body so the reason is visible from the
      // browser's own Network panel when the host log is not reachable.
      send(res, 403, { 'content-type': 'text/plain; charset=utf-8', 'cache-control': 'no-store' },
        `cross-origin login is not allowed (origin=${String(observed.origin)}`
        + `, host=${String(observed.host)}`
        + `, sec-fetch-site=${String(observed.site)})\n`)
      return
    }
    const body = await readBody(req)
    const fallbackNext = safeNext(new URL(req.url ?? '/', 'http://dsh.invalid').searchParams.get('next'), '/')
    if (!body.ok) {
      send(res, body.reason === 'too-large' ? 413 : 400, { 'cache-control': 'no-store' }, '')
      return
    }
    const submission = parseSubmission(headerValue(req.headers, 'content-type'), body.text)
    if (submission === undefined) {
      send(res, 415, { 'content-type': 'text/plain; charset=utf-8', 'cache-control': 'no-store' }, 'unsupported content type\n')
      return
    }
    const next = safeNext(submission.next, fallbackNext)
    const key = `${req.socket?.remoteAddress ?? 'unknown'}|${submission.username}`
    if (limiter.retryAfterSeconds(key) > 0) {
      send(res, 303, { 'cache-control': 'no-store', location: loginUrl(next, 'locked', options.publicBasePath), 'referrer-policy': 'no-referrer' })
      return
    }
    const user = matchUser(options.users, submission.username, submission.password)
    if (user === undefined) {
      limiter.fail(key)
      send(res, 303, { 'cache-control': 'no-store', location: loginUrl(next, 'bad-credentials', options.publicBasePath), 'referrer-policy': 'no-referrer' })
      return
    }
    limiter.succeed(key)
    const authority = cookieAuthority(req.headers)
    if (authority === undefined) {
      send(res, 400, { 'cache-control': 'no-store' }, 'missing Host header\n')
      return
    }
    send(res, 303, {
      'cache-control': 'no-store',
      location: toBrowser(next),
      'referrer-policy': 'no-referrer',
      'set-cookie': state.sessions.issueCookie(user.username, authority, isSecure()),
    })
  }

  return {
    login(req, res) {
      if (req.method === 'GET' || req.method === 'HEAD') {
        serveLogin(req, res)
        return
      }
      if (req.method === 'POST') {
        void submitLogin(req, res).catch((error) => {
          state.onError(error)
          if (!res.headersSent) send(res, 500, { 'cache-control': 'no-store' }, 'login failure\n')
          else res.end()
        })
        return
      }
      send(res, 405, { allow: 'GET, HEAD, POST', 'cache-control': 'no-store' }, '')
    },
    logout(req, res) {
      if (req.method !== 'GET' && req.method !== 'POST') {
        send(res, 405, { allow: 'GET, POST', 'cache-control': 'no-store' }, '')
        return
      }
      if (!isSameOrigin(req, isSecure())) {
        send(res, 403, { 'content-type': 'text/plain; charset=utf-8', 'cache-control': 'no-store' }, 'cross-origin logout is not allowed\n')
        return
      }
      const secure = isSecure()
      const cookies = [state.sessions.expireCookie(secure)]
      const legacy = legacySessionCookieName(req.headers, secure)
      if (legacy !== undefined) {
        cookies.push(`${legacy}=; Max-Age=0; Path=/; Expires=${new Date(0).toUTCString()}; HttpOnly; SameSite=Strict${secure ? '; Secure' : ''}`)
      }
      send(res, 303, {
        'cache-control': 'no-store',
        location: loginUrl('/', 'signed-out', options.publicBasePath),
        'referrer-policy': 'no-referrer',
        'set-cookie': cookies,
      })
    },
    session(req, res) {
      if (req.method !== 'GET' && req.method !== 'HEAD') {
        send(res, 405, { allow: 'GET, HEAD', 'cache-control': 'no-store' }, '')
        return
      }
      const user = state.sessions.verify(req.headers)
      sendJson(res, {
        authenticated: user !== undefined,
        user: user === undefined ? null : { username: user, displayName: options.displayName },
        launchTokenAllowed: options.allowLaunchToken,
      })
    },
  }
}

/** Redirect an unauthenticated index request to the login form. */
function redirectToLogin(req, res, publicBasePath) {
  const url = new URL(req.url ?? '/', 'http://dsh.invalid')
  url.searchParams.delete('token')
  const next = safeNext(`${url.pathname}${url.search}`, '/')
  send(res, 303, {
    'cache-control': 'no-store',
    location: loginUrl(next, undefined, publicBasePath),
    'referrer-policy': 'no-referrer',
  })
}

/**
 * Take over the two admission doors on the connection service.
 *
 * `delete` on the per-context service proxy removes the own property we added,
 * exposing the prototype method again — the only shape that survives a plugin
 * reload without stacking wrappers.
 *
 * @returns the disposer restoring both doors.
 */
function installGate(ctx, state, options) {
  const connection = ctx.connection
  const originalAuthorizeIndex = connection.authorizeIndex.bind(connection)
  const originalRequestRejection = connection.requestRejection.bind(connection)

  connection.authorizeIndex = (req, res) => {
    if (state.sessions.isAuthenticated(req.headers)) return true
    if (options.allowLaunchToken) {
      const url = new URL(req.url ?? '/', 'http://dsh.invalid')
      if (url.searchParams.has('token')) return originalAuthorizeIndex(req, res)
    }
    redirectToLogin(req, res, options.publicBasePath)
    return false
  }

  connection.requestRejection = (request) => {
    const rejection = originalRequestRejection(request)
    // The Host/Origin fence is not this plugin's to override.
    if (rejection === 403) return rejection
    if (state.sessions.isAuthenticated(request?.headers)) return undefined
    // Only when the launch token is configured as a credential does DSH's own
    // session cookie still admit a request; otherwise this plugin's session is
    // the sole credential, so a cookie minted before the gate existed cannot
    // keep reaching /api, the RPC channels, or the WebSocket mux.
    if (options.allowLaunchToken && rejection === undefined) return undefined
    return rejection ?? 401
  }

  return () => {
    for (const [key, original] of [
      ['authorizeIndex', originalAuthorizeIndex],
      ['requestRejection', originalRequestRejection],
    ]) {
      try {
        delete connection[key]
      } catch {
        // A non-configurable own property falls through to the assignment below.
      }
      // A service with prototype methods is fully restored by the delete; a
      // plain object (hand-built context) only has the captured copy.
      if (typeof connection[key] !== 'function') connection[key] = original
    }
  }
}

/**
 * Install the login gate.
 * @param ctx - host plugin context carrying `connection` and `webServer`.
 * @param config - validated plugin config; absent fields take their defaults.
 */
export function apply(ctx, config) {
  const options = resolveOptions(config)
  const warn = (error) => {
    if (typeof ctx.logger?.warn === 'function') ctx.logger.warn(error)
    else console.warn(String(error?.message ?? error))
  }
  const state = {
    sessions: new SessionStore({
      secret: loadOrCreateSecret(options.sessionFile, warn),
      cookieName: options.cookieName,
      maxAgeDays: options.sessionDays,
    }),
    onError: (error) => warn(error),
  }
  const isSecure = () => ctx.webServer?.protocol === 'https:'
  const handlers = createHandlers(state, options, isSecure)

  const routes = [
    { path: LOGIN_PATH, handler: (req, res) => { handlers.login(req, res) } },
    { path: LOGOUT_PATH, handler: (req, res) => { handlers.logout(req, res) } },
    { path: SESSION_PATH, handler: (req, res) => { handlers.session(req, res) } },
  ]
  for (const route of routes) {
    ctx.effect(
      () => ctx.webServer.register({ kind: 'exact', path: route.path, handler: route.handler }),
      `dsh-plugin-account: ${route.path}`,
    )
  }
  ctx.effect(() => installGate(ctx, state, options), 'dsh-plugin-account: connection gate')

  const usernames = options.users.map(user => user.username).join(', ')
  if (options.usingDefaultUsers && typeof ctx.logger?.warn === 'function') {
    ctx.logger.warn('dsh-plugin-account: config.users is not set — using the built-in admin/admin123 default; set config.users before exposing this host')
  }
  if (typeof ctx.logger?.info === 'function') {
    ctx.logger.info(`dsh-plugin-account: login required at ${options.publicBasePath}${LOGIN_PATH} (users: ${usernames}; launch-token auto-login ${options.allowLaunchToken ? 'enabled' : 'disabled'})`)
  }
}
