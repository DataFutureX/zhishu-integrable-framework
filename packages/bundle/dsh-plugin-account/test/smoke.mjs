/**
 * Host-level smoke test for dsh-plugin-account.
 *
 * It runs the real plugin against a minimal stand-in for the host: an HTTP
 * server whose named routes are the ones the plugin registered, an index
 * fallback that answers only when `authorizeIndex` allows it, and an `/api`
 * door that answers only when `requestRejection` admits the request.
 *
 * Run with: node test/smoke.mjs
 */

import assert from 'node:assert/strict'
import { createServer, request as httpRequest } from 'node:http'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { apply } from '../index.js'
import { SessionStore } from '../lib/session.js'

const failures = []
let checks = 0
function check(label, fn) {
  checks += 1
  try {
    fn()
    console.log(`ok   ${label}`)
  } catch (error) {
    failures.push(`${label}: ${error?.message ?? error}`)
    console.log(`FAIL ${label}: ${error?.message ?? error}`)
  }
}

const workdir = mkdtempSync(join(tmpdir(), 'dsh-account-smoke-'))
const routes = new Map()
const disposers = []

const webServer = {
  protocol: 'http:',
  register(route) {
    if (routes.has(route.path)) throw new Error(`duplicate route ${route.path}`)
    routes.set(route.path, route.handler)
    return () => routes.delete(route.path)
  },
}

/** Stands in for HostConnectionService: same two doors the plugin takes over. */
class FakeConnection {
  constructor() {
    this.operator = { kind: 'operator' }
  }

  authorizeIndex(req, res) {
    res.writeHead(401, { 'content-type': 'text/plain; charset=utf-8' })
    res.end('dsh web authentication required; reopen the URL printed by dsh web.\n')
    return false
  }

  requestRejection(request) {
    if (request.headers.host === 'evil.example') return 403
    return request.headers.cookie?.includes('dsh-auth') === true ? undefined : 401
  }
}

const connection = new FakeConnection()

const ctx = {
  webServer,
  connection,
  logger: { info() {}, warn(error) { console.error('[warn]', error?.message ?? error) } },
  effect(fn) {
    disposers.push(fn())
  },
}

apply(ctx, {
  users: [{ username: 'admin', password: 'admin123' }],
  allowLaunchToken: false,
  sessionFile: join(workdir, 'session-secret'),
  sessionDays: 7,
})

const server = createServer((req, res) => {
  const url = new URL(req.url ?? '/', 'http://dsh.invalid')
  const route = routes.get(url.pathname)
  if (route !== undefined) {
    route(req, res)
    return
  }
  if (url.pathname.startsWith('/api/')) {
    const rejection = connection.requestRejection(req)
    if (rejection !== undefined) {
      res.writeHead(rejection)
      res.end(rejection === 401 ? 'unauthorized' : 'forbidden')
      return
    }
    res.writeHead(200, { 'content-type': 'text/plain' })
    res.end('api-ok')
    return
  }
  // The frontend-static fallback seat: the index renders only when admitted.
  if (connection.authorizeIndex(req, res)) {
    res.writeHead(200, { 'content-type': 'text/html; charset=utf-8' })
    res.end('<!DOCTYPE html><html><head></head><body>__DSH_BOOT__</body></html>')
  }
})

await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
const origin = `http://127.0.0.1:${server.address().port}`

/** One browser-like request that never follows redirects. */
async function call(path, { method = 'GET', headers = {}, body, host } = {}) {
  const requestHeaders = { host: host ?? new URL(origin).host, ...headers }
  const response = await fetch(`${origin}${path}`, { method, headers: requestHeaders, body, redirect: 'manual' })
  const text = await response.text()
  return { status: response.status, headers: response.headers, text }
}

function cookieOf(response) {
  const raw = response.headers.get('set-cookie')
  return raw === null ? undefined : raw.split(';', 1)[0]
}

/** One raw request, so the Host header can be spoofed (fetch would rewrite it). */
function rawGet(path, host, cookie) {
  const port = server.address().port
  return new Promise((resolve, reject) => {
    const request = httpRequest({
      host: '127.0.0.1',
      port,
      path,
      method: 'GET',
      headers: { host, ...(cookie === undefined ? {} : { cookie }) },
    }, (response) => {
      let text = ''
      response.setEncoding('utf8')
      response.on('data', (chunk) => { text += chunk })
      response.on('end', () => resolve({ status: response.statusCode, text }))
    })
    request.on('error', reject)
    request.end()
  })
}

const loginForm = new URLSearchParams({ username: 'admin', password: 'admin123' })
const badForm = new URLSearchParams({ username: 'admin', password: 'nope' })

const anonymousIndex = await call('/')
check('anonymous index request redirects to the login form', () => {
  assert.equal(anonymousIndex.status, 303)
  assert.equal(anonymousIndex.headers.get('location'), '/account/login')
})

const loginPage = await call('/account/login')
check('login page renders a password form', () => {
  assert.equal(loginPage.status, 200)
  assert.match(loginPage.text, /<input id="password" name="password" type="password"/u)
  assert.match(loginPage.text, /name="next" value="\/"/u)
  assert.doesNotMatch(loginPage.text, /undefined/u)
})
check('login page keeps a real Origin for the same-origin form submission', () => {
  assert.equal(loginPage.headers.get('referrer-policy'), 'strict-origin')
})

const tokenAttempt = await call('/?token=launch-token-value')
check('launch token is ignored and still redirects to the login form', () => {
  assert.equal(tokenAttempt.status, 303)
  assert.equal(tokenAttempt.headers.get('location'), '/account/login')
})

const rejected = await call('/account/login', {
  method: 'POST',
  headers: { 'content-type': 'application/x-www-form-urlencoded' },
  body: badForm.toString(),
})
check('a wrong password returns to the form with an error', () => {
  assert.equal(rejected.status, 303)
  assert.equal(rejected.headers.get('location'), '/account/login?error=bad-credentials')
  assert.equal(cookieOf(rejected), undefined)
})

const accepted = await call('/account/login', {
  method: 'POST',
  headers: { 'content-type': 'application/x-www-form-urlencoded' },
  body: loginForm.toString(),
})
const sessionCookie = cookieOf(accepted)
check('a correct password mints an HttpOnly, SameSite=Strict session cookie', () => {
  assert.equal(accepted.status, 303)
  assert.equal(accepted.headers.get('location'), '/')
  assert.ok(sessionCookie?.startsWith('dsh-account=v1.'), `unexpected cookie ${String(sessionCookie)}`)
  const raw = accepted.headers.get('set-cookie')
  assert.match(raw, /HttpOnly/u)
  assert.match(raw, /SameSite=Strict/u)
  assert.match(raw, /Path=\//u)
})

const authedIndex = await call('/', { headers: { cookie: sessionCookie } })
check('the session serves the index', () => {
  assert.equal(authedIndex.status, 200)
  assert.match(authedIndex.text, /__DSH_BOOT__/u)
})

const tampered = `${sessionCookie.slice(0, -2)}xx`
const tamperedIndex = await call('/', { headers: { cookie: tampered } })
check('a tampered cookie is refused', () => {
  assert.equal(tamperedIndex.status, 303)
  assert.equal(tamperedIndex.headers.get('location'), '/account/login')
})

const anonymousApi = await call('/api/session/list', { method: 'POST' })
check('anonymous API calls stay 401', () => assert.equal(anonymousApi.status, 401))

const stockCookieApi = await call('/api/session/list', {
  method: 'POST',
  headers: { cookie: 'dsh-auth-AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA' },
})
check('a stock DSH launch-token cookie no longer admits the API', () => assert.equal(stockCookieApi.status, 401))

const authedApi = await call('/api/session/list', { method: 'POST', headers: { cookie: sessionCookie } })
check('the session admits API calls without a DSH cookie', () => {
  assert.equal(authedApi.status, 200)
  assert.equal(authedApi.text, 'api-ok')
})

check('the Host fence still wins over a valid session (403 preserved)', () => {
  // Called directly: fetch (undici) rewrites the Host header, so the fence has
  // to be exercised through the same door the gateway uses.
  assert.equal(connection.requestRejection({ headers: { host: 'evil.example', cookie: sessionCookie } }), 403)
  assert.equal(connection.requestRejection({ headers: { host: 'evil.example' } }), 403)
})

const crossOrigin = await call('/account/login', {
  method: 'POST',
  headers: { 'content-type': 'application/x-www-form-urlencoded', origin: 'http://evil.example' },
  body: loginForm.toString(),
})
check('cross-origin credential submission is refused', () => assert.equal(crossOrigin.status, 403))

const opaqueOrigin = await call('/account/login', {
  method: 'POST',
  headers: { 'content-type': 'application/x-www-form-urlencoded', origin: 'null', 'sec-fetch-site': 'same-origin' },
  body: loginForm.toString(),
})
check('an opaque Origin: null submission is still refused', () => assert.equal(opaqueOrigin.status, 403))

const sameOriginForm = await call('/account/login', {
  method: 'POST',
  headers: { 'content-type': 'application/x-www-form-urlencoded', origin, 'sec-fetch-site': 'same-origin' },
  body: loginForm.toString(),
})
check('a same-origin form submission carrying Origin is accepted', () => {
  assert.equal(sameOriginForm.status, 303)
  assert.ok(cookieOf(sameOriginForm)?.startsWith('dsh-account=v1.'))
})

const sameSiteLabelled = await call('/account/login', {
  method: 'POST',
  headers: { 'content-type': 'application/x-www-form-urlencoded', origin, 'sec-fetch-site': 'same-site' },
  body: loginForm.toString(),
})
check('a matching Origin outranks a same-site fetch label', () => assert.equal(sameSiteLabelled.status, 303))

const sessionState = await call('/account/api/session', { headers: { cookie: sessionCookie } })
check('the session endpoint reports the signed-in user', () => {
  assert.equal(sessionState.status, 200)
  assert.deepEqual(JSON.parse(sessionState.text), {
    authenticated: true, user: { username: 'admin', displayName: '自定义账号' }, launchTokenAllowed: false,
  })
})

const foreignAuthority = await rawGet('/account/api/session', 'other.example:1234', sessionCookie)
check('a valid cookie does not authenticate another authority', () => {
  assert.equal(foreignAuthority.status, 200)
  assert.deepEqual(JSON.parse(foreignAuthority.text), { authenticated: false, user: null, launchTokenAllowed: false })
})

const shortStore = new SessionStore({ secret: Buffer.alloc(32, 7), cookieName: 'short', maxAgeDays: 0.00001 })
const shortValue = shortStore.issueCookie('admin', 'h:1234', false).split(';', 1)[0].split('=').slice(1).join('=')
check('a freshly issued short session verifies', () => {
  assert.equal(shortStore.verify({ host: 'h:1234', cookie: `short=${shortValue}` }), 'admin')
})
await new Promise(resolve => setTimeout(resolve, 1500))
check('an expired session is refused', () => {
  assert.equal(shortStore.verify({ host: 'h:1234', cookie: `short=${shortValue}` }), undefined)
})

const escapedNext = await call('/account/login', {
  method: 'POST',
  headers: { 'content-type': 'application/x-www-form-urlencoded' },
  body: new URLSearchParams({ username: 'admin', password: 'admin123', next: '//evil.example/x' }).toString(),
})
check('an off-site next value falls back to /', () => {
  assert.equal(escapedNext.status, 303)
  assert.equal(escapedNext.headers.get('location'), '/')
})

for (const hostile of ['/..//evil.example/p', '/.//evil.example/', '/%2e%2e//evil.example/']) {
  const normalized = await call('/account/login', {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ username: 'admin', password: 'admin123', next: hostile }).toString(),
  })
  check(`dot-segment next ${hostile} cannot leave the site`, () => {
    assert.equal(normalized.status, 303)
    assert.equal(normalized.headers.get('location'), '/')
  })
}

const authenticatedNext = await call('/account/login?next=/..//evil.example/p', { headers: { cookie: sessionCookie } })
check('the already-authenticated login redirect cannot leave the site', () => {
  assert.equal(authenticatedNext.status, 303)
  assert.equal(authenticatedNext.headers.get('location'), '/')
})

const oversized = await call('/account/login', {
  method: 'POST',
  headers: { 'content-type': 'application/x-www-form-urlencoded' },
  body: `username=admin&password=${'a'.repeat(20 * 1024)}`,
})
check('an oversized body is refused with 413', () => assert.equal(oversized.status, 413))

const hugeBody = await call('/account/login', {
  method: 'POST',
  headers: { 'content-type': 'application/x-www-form-urlencoded' },
  body: `username=admin&password=${'a'.repeat(2 * 1024 * 1024)}`,
})
check('a multi-megabyte body is drained and still answered with 413', () => assert.equal(hugeBody.status, 413))

const jsonLogin = await call('/account/login', {
  method: 'POST',
  headers: { 'content-type': 'application/json' },
  body: JSON.stringify({ username: 'admin', password: 'admin123' }),
})
check('JSON credential submission works too', () => {
  assert.equal(jsonLogin.status, 303)
  assert.ok(cookieOf(jsonLogin)?.startsWith('dsh-account=v1.'))
})

const locked = []
for (let attempt = 0; attempt < 6; attempt += 1) {
  locked.push(await call('/account/login', {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ username: 'brute', password: 'x' }).toString(),
  }))
}
check('repeated failures start backing off', () => {
  assert.equal(locked.at(-1).headers.get('location'), '/account/login?error=locked')
  assert.equal(locked[0].headers.get('location'), '/account/login?error=bad-credentials')
})

const logout = await call('/account/logout', { headers: { cookie: sessionCookie } })
check('logout expires the session cookie', () => {
  assert.equal(logout.status, 303)
  assert.equal(logout.headers.get('location'), '/account/login?error=signed-out')
  assert.match(logout.headers.get('set-cookie'), /Max-Age=0/u)
})
check('logout also expires the stock DSH cookie', () => {
  const cookies = logout.headers.getSetCookie?.() ?? [logout.headers.get('set-cookie') ?? '']
  assert.ok(cookies.some(value => value.startsWith('dsh-auth-')), `cookies: ${cookies.join(' | ')}`)
})

const crossSiteLogout = await call('/account/logout', {
  headers: { cookie: sessionCookie, 'sec-fetch-site': 'cross-site' },
})
check('a cross-site logout is refused', () => assert.equal(crossSiteLogout.status, 403))

for (const dispose of disposers.reverse()) dispose()
check('disposing restores the original connection doors', () => {
  assert.equal(connection.authorizeIndex({ url: '/', headers: { host: '127.0.0.1' } }, { writeHead() {}, end() {} }), false)
})
const restored = await call('/')
check('the restored door answers like stock DSH again', () => assert.equal(restored.status, 401))

/** A second, route-free context: configuration and launch-token semantics only. */
function bareHarness() {
  const connection = new FakeConnection()
  const ctx = {
    webServer: { protocol: 'http:', register() { return () => {} } },
    connection,
    logger: { info() {}, warn() {} },
    effect(fn) { fn() },
  }
  return { ctx, connection }
}

const launchTokenHarness = bareHarness()
apply(launchTokenHarness.ctx, {
  users: [{ username: 'admin', password: 'admin123' }],
  allowLaunchToken: true,
  sessionFile: join(workdir, 'secret-launch-token'),
})
check('allowLaunchToken: true keeps the stock cookie as a credential', () => {
  assert.equal(launchTokenHarness.connection.requestRejection({ headers: { host: '127.0.0.1:1', cookie: 'dsh-auth=x' } }), undefined)
  assert.equal(launchTokenHarness.connection.requestRejection({ headers: { host: '127.0.0.1:1' } }), 401)
})

const strictHarness = bareHarness()
apply(strictHarness.ctx, { users: [{ username: 'admin', password: 'admin123' }], sessionFile: join(workdir, 'secret-strict') })
check('the default drops the stock cookie even though the stock door admits it', () => {
  assert.equal(strictHarness.connection.requestRejection({ headers: { host: '127.0.0.1:1', cookie: 'dsh-auth=x' } }), 401)
})

check('an empty users array fails the load instead of defaulting', () => {
  assert.throws(() => apply(bareHarness().ctx, { users: [] }), /non-empty array/u)
})
check('a malformed users value fails the load', () => {
  assert.throws(() => apply(bareHarness().ctx, { users: 'admin' }), /non-empty array/u)
})
check('a user row missing a password fails the load', () => {
  assert.throws(() => apply(bareHarness().ctx, { users: [{ username: 'admin' }] }), /non-empty username and password/u)
})
check('a backslash mount prefix fails the load', () => {
  assert.throws(() => apply(bareHarness().ctx, { publicBasePath: '\\evil.example' }), /publicBasePath/u)
})
check('a header-injecting mount prefix fails the load', () => {
  assert.throws(() => apply(bareHarness().ctx, { publicBasePath: 'x\r\nX-Injected: 1' }), /publicBasePath/u)
})

await new Promise(resolve => server.close(resolve))
rmSync(workdir, { recursive: true, force: true })

console.log(`\n${checks - failures.length}/${checks} checks passed`)
if (failures.length > 0) {
  console.error(failures.join('\n'))
  process.exitCode = 1
}
