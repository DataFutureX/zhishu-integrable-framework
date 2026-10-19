/**
 * Mount-prefix test: a reverse proxy that strips `/ui` before forwarding
 * (DSH's documented `--public-url https://host/ui/` deployment).
 *
 * Routes stay origin-root, every browser-facing Location and the form action
 * must carry the prefix.
 *
 * Run with: node test/mount-path.mjs
 */

import assert from 'node:assert/strict'
import { createServer } from 'node:http'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { apply } from '../index.js'

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

const workdir = mkdtempSync(join(tmpdir(), 'dsh-account-mount-'))
const routes = new Map()
const connection = {
  operator: {},
  authorizeIndex(req, res) {
    res.writeHead(401)
    res.end('unauthorized')
    return false
  },
  requestRejection() {
    return 401
  },
}
const ctx = {
  webServer: {
    protocol: 'http:',
    register(route) {
      routes.set(route.path, route.handler)
      return () => routes.delete(route.path)
    },
  },
  connection,
  logger: { info() {}, warn() {} },
  effect(fn) {
    fn()
  },
}
apply(ctx, {
  users: [{ username: 'admin', password: 'admin123' }],
  publicBasePath: '/ui',
  sessionFile: join(workdir, 'secret'),
})

const server = createServer((req, res) => {
  const url = new URL(req.url ?? '/', 'http://dsh.invalid')
  const route = routes.get(url.pathname)
  if (route !== undefined) {
    route(req, res)
    return
  }
  if (connection.authorizeIndex(req, res)) {
    res.writeHead(200)
    res.end('boot')
  }
})
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
const origin = `http://127.0.0.1:${server.address().port}`

async function call(path, { method = 'GET', headers = {}, body } = {}) {
  const response = await fetch(`${origin}${path}`, { method, headers, body, redirect: 'manual' })
  return { status: response.status, headers: response.headers, text: await response.text() }
}

const anonymous = await call('/')
check('index redirects inside the mount', () => {
  assert.equal(anonymous.status, 303)
  assert.equal(anonymous.headers.get('location'), '/ui/account/login')
})

const page = await call('/account/login')
check('the form action carries the mount prefix', () => {
  assert.equal(page.status, 200)
  assert.match(page.text, /action="\/ui\/account\/login"/u)
})

const accepted = await call('/account/login', {
  method: 'POST',
  headers: { 'content-type': 'application/x-www-form-urlencoded' },
  body: 'username=admin&password=admin123',
})
check('a successful login returns the browser to the mounted root', () => {
  assert.equal(accepted.status, 303)
  assert.equal(accepted.headers.get('location'), '/ui/')
})

const rejected = await call('/account/login', {
  method: 'POST',
  headers: { 'content-type': 'application/x-www-form-urlencoded' },
  body: 'username=admin&password=nope',
})
check('a failed login returns to the mounted form', () => {
  assert.equal(rejected.headers.get('location'), '/ui/account/login?error=bad-credentials')
})

const logout = await call('/account/logout')
check('logout returns to the mounted form', () => {
  assert.equal(logout.headers.get('location'), '/ui/account/login?error=signed-out')
})

await new Promise(resolve => server.close(resolve))
rmSync(workdir, { recursive: true, force: true })

console.log(`\n${checks - failures.length}/${checks} checks passed`)
if (failures.length > 0) {
  console.error(failures.join('\n'))
  process.exitCode = 1
}
