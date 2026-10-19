/** Local Web document and authenticated HTTP forwarding for the application window. */
import { request as httpRequest, type IncomingMessage } from 'node:http'
import { request as httpsRequest } from 'node:https'
import { readFile } from 'node:fs/promises'
import { extname, resolve, sep } from 'node:path'

const MIME: Readonly<Record<string, string>> = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8', '.svg': 'image/svg+xml', '.json': 'application/json',
  '.woff2': 'font/woff2', '.png': 'image/png', '.ico': 'image/x-icon',
}
const BOOT = '<script>globalThis.__DSH_BOOT_READY__ = Promise.withResolvers()</script>'

/**
 * Read an application-owned static asset; the index waits for asynchronous Host injections.
 * @param request - Local application request.
 * @param root - Packaged Web dist directory.
 * @returns Static response, or a missing/invalid path response.
 */
export async function serveWebDocument(request: Request, root: string): Promise<Response> {
  if (!['GET', 'HEAD'].includes(request.method)) return new Response(null, { status: 405 })
  const url = new URL(request.url)
  let pathname: string
  try { pathname = decodeURIComponent(url.pathname) } catch { return new Response(null, { status: 400 }) }
  const target = resolve(root, '.' + (pathname === '/' ? '/index.html' : pathname))
  const directory = resolve(root)
  if (!target.startsWith(directory + sep)) return new Response(null, { status: 403 })
  let body: Buffer
  try { body = await readFile(target) } catch (error) {
    if ((error as NodeJS.ErrnoException).code === 'ENOENT') return new Response(null, { status: 404 })
    throw error
  }
  const content = pathname === '/' || pathname === '/index.html'
    ? body.toString().replace('<head>', '<head>' + BOOT) : new Uint8Array(body)
  return new Response(request.method === 'HEAD' ? null : content, {
    headers: { 'content-type': MIME[extname(target)] ?? 'application/octet-stream' },
  })
}

/**
 * Request options for a main-process call to the owned Host.
 * The gate session is sent on `Cookie`. `Origin` and `Sec-Fetch-Site` are removed:
 * Chromium may mark this fetch cross-site, and the Host refuses that marker before it reads the session.
 * @param cookie - `name=value` session held by the main process.
 * @param init - caller method, body, and signal.
 * @returns the same options with Host session headers applied.
 */
export function hostSessionRequestInit(cookie: string, init?: RequestInit): RequestInit {
  const headers = new Headers(init?.headers)
  headers.delete('origin')
  headers.delete('sec-fetch-site')
  const plain: Record<string, string> = {}
  for (const [key, value] of headers) plain[key] = value
  plain.cookie = cookie
  return { ...init, headers: plain }
}

/**
 * Fetch the owned Host through Node.
 * Electron's fetch discards a caller-supplied `Cookie` header, so the gate session must be sent here.
 * `redirect: 'manual'` returns the 3xx response. `redirect: 'error'` rejects it. `follow` replays a GET to `Location`.
 * @param input - absolute Host URL.
 * @param init - method, headers, body, redirect mode, and abort signal. The body must be a string or bytes.
 * @returns the Host response. A manual redirect is returned rather than followed.
 */
export function hostFetch(input: string, init: RequestInit = {}): Promise<Response> {
  return hostFetchAt(input, init, 5)
}

/** @param remainingRedirects - follows left before a `follow` redirect is refused. */
function hostFetchAt(input: string, init: RequestInit, remainingRedirects: number): Promise<Response> {
  const url = new URL(input)
  const outgoing = outgoingHeaders(init.headers)
  const body = init.body
  if (body != null && typeof body !== 'string' && !(body instanceof Uint8Array)) {
    return Promise.reject(new Error('desktop host fetch: body must be a string or bytes'))
  }
  const signal = init.signal
  if (signal?.aborted) {
    const reason = signal.reason
    return Promise.reject(reason instanceof Error ? reason : new Error('desktop host fetch: aborted'))
  }
  const transport = url.protocol === 'https:' ? httpsRequest : httpRequest
  return new Promise((resolvePromise, reject) => {
    const req = transport(url, { method: init.method ?? 'GET', headers: outgoing }, (incoming) => {
      const status = incoming.statusCode ?? 0
      const mode = init.redirect ?? 'follow'
      if (status >= 300 && status < 400 && mode !== 'manual') {
        incoming.resume()
        if (mode === 'error' || remainingRedirects <= 0 || typeof incoming.headers.location !== 'string') {
          reject(new Error('desktop host fetch: redirect refused'))
          return
        }
        const next = { ...init, method: 'GET' }
        delete next.body
        resolvePromise(hostFetchAt(new URL(incoming.headers.location, url).href, next, remainingRedirects - 1))
        return
      }
      void readIncoming(incoming).then((bytes) => {
        const responseHeaders = new Headers()
        for (const [key, value] of Object.entries(incoming.headers)) {
          if (typeof value === 'string') responseHeaders.set(key, value)
          else if (Array.isArray(value)) for (const item of value) responseHeaders.append(key, item)
        }
        const payload = new ArrayBuffer(bytes.byteLength)
        new Uint8Array(payload).set(bytes)
        resolvePromise(new Response(payload, { status, headers: responseHeaders }))
      }, reject)
    })
    const abort = (): void => {
      const reason = signal?.reason
      req.destroy(reason instanceof Error ? reason : new Error('desktop host fetch: aborted'))
    }
    signal?.addEventListener('abort', abort, { once: true })
    req.once('error', reject)
    req.once('close', () => { signal?.removeEventListener('abort', abort) })
    if (typeof body === 'string' || body instanceof Uint8Array) req.end(body)
    else req.end()
  })
}

/**
 * Copy request headers without the forbidden-header filter.
 * A `Headers` instance may drop `Cookie`; a plain map is copied as given.
 * @param headers - caller headers.
 * @returns lower-cased names sent on the Node request.
 */
function outgoingHeaders(headers: HeadersInit | undefined): Record<string, string> {
  const outgoing: Record<string, string> = {}
  if (headers == null) return outgoing
  if (typeof headers === 'object' && !Array.isArray(headers) && !(headers instanceof Headers)) {
    for (const [key, value] of Object.entries(headers)) outgoing[key.toLowerCase()] = value
    return outgoing
  }
  const parsed = new Headers(headers)
  for (const [key, value] of parsed) outgoing[key] = value
  return outgoing
}

/** @param incoming - Host response whose body is still unread. @returns the full body. */
function readIncoming(incoming: IncomingMessage): Promise<Uint8Array> {
  return new Promise((resolvePromise, reject) => {
    const chunks: Buffer[] = []
    incoming.on('data', (chunk: Buffer) => { chunks.push(chunk) })
    incoming.once('end', () => { resolvePromise(new Uint8Array(Buffer.concat(chunks))) })
    incoming.once('error', reject)
  })
}

/**
 * Exchange the Host launch URL for an authority-bound browser cookie.
 * @param url - Authenticated URL reported by the owned Host process.
 * @returns Cookie header for requests forwarded to that Host.
 */
export async function authenticateWebHost(url: string): Promise<string> {
  const cookie = await tryAuthenticateWebHost(url)
  if (cookie === undefined) throw new Error('Desktop Host authentication failed')
  return cookie
}

/**
 * Exchange the Host launch URL for an authority-bound browser cookie when the
 * native token door still admits it. A Host behind a login gate answers with a
 * redirect to its login page and no cookie, which reports as `undefined`.
 * @param url - Authenticated URL reported by the owned Host process.
 * @returns Cookie header for requests forwarded to that Host, or undefined
 * when the caller must complete a login instead.
 */
export async function tryAuthenticateWebHost(url: string): Promise<string | undefined> {
  const response = await fetch(url, { redirect: 'manual' })
  const cookie = response.headers.get('set-cookie')
  await response.body?.cancel()
  if (response.status !== 303 || cookie === null) return undefined
  const end = cookie.indexOf(';')
  return end < 0 ? cookie : cookie.slice(0, end)
}

/**
 * Response headers not relayed to the renderer. `set-cookie` would hand the
 * Host's authentication cookie to the page's cookie jar, which the shell owns
 * instead; the rest describe the Node `fetch` connection (its encoding, length,
 * and hop-by-hop transport), which Chromium never sees.
 */
const WITHHELD_RESPONSE_HEADERS = [
  'set-cookie',
  'content-encoding', 'content-length',
  'transfer-encoding', 'connection', 'keep-alive', 'te', 'trailer', 'upgrade', 'proxy-authenticate', 'proxy-authorization',
]

/** Host routes whose responses carry immutable cache headers keyed by a per-process revision. */
const PLUGIN_BUNDLE_PATH = /^\/plugins\//u

/**
 * Forward local application requests to its authenticated Host, preserving streaming and cancellation.
 * Plugin bundle responses lose their `cache-control` for `no-store`: the Host marks them immutable
 * under a revision that changes every launch, so Chromium's disk cache would only accumulate bundles
 * no later launch can reuse.
 * @param request - Request from the application origin.
 * @param host - Owned Host URL.
 * @param cookie - Host-issued authentication cookie.
 * @returns Host response without connection-level headers.
 */
export async function forwardWebRequest(
  request: Request,
  host: string,
  cookie: string,
  onUnauthorized?: () => void,
): Promise<Response> {
  const source = new URL(request.url)
  const origin = request.headers.get('origin')
  if (origin !== null && origin !== 'dsh-app://app') return new Response(null, { status: 403 })
  const target = new URL(host)
  target.pathname = source.pathname
  target.search = source.search
  const headers = new Headers(request.headers)
  for (const name of ['host', 'origin', 'cookie', 'sec-fetch-site']) headers.delete(name)
  headers.set('cookie', cookie)
  const init = { method: request.method, headers, body: request.body, signal: request.signal, duplex: 'half', redirect: 'manual' as const }
  const response = await fetch(target, init)
  if (response.status === 401 && onUnauthorized !== undefined) onUnauthorized()
  const outgoing = new Headers(response.headers)
  for (const name of WITHHELD_RESPONSE_HEADERS) outgoing.delete(name)
  if (PLUGIN_BUNDLE_PATH.test(source.pathname)) outgoing.set('cache-control', 'no-store')
  return new Response(response.body, { status: response.status, headers: outgoing })
}
