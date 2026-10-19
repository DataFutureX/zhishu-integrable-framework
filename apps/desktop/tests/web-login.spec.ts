import { createServer, type Server } from 'node:http'
import { afterEach, expect, it } from 'vitest'
import { hostFetch, hostSessionRequestInit } from '../src/web-document.ts'
import { resolveHostSessionCookie } from '../src/web-login.ts'

const servers: Server[] = []
afterEach(async () => {
  await Promise.all(servers.splice(0).map(server => new Promise<void>((resolve) => { server.close(() => { resolve() }) })))
})

async function listen(server: Server): Promise<number> {
  servers.push(server)
  await new Promise<void>((resolve, reject) => {
    server.listen(0, '127.0.0.1', () => { resolve() })
    server.once('error', reject)
  })
  const address = server.address()
  return typeof address === 'object' && address !== null ? address.port : 0
}

it('replaces a gate cookie bound to another port before accepting the session', async () => {
  let port = 0
  const accepted = (cookie: string | undefined): boolean => cookie === `dsh-account=127.0.0.1:${String(port)}`
  port = await listen(createServer((request, response) => {
    if (accepted(request.headers.cookie)) {
      response.writeHead(200, { 'content-type': 'text/plain' })
      response.end('ok')
      return
    }
    response.writeHead(303, { location: '/account/login' })
    response.end()
  }))
  const origin = `http://127.0.0.1:${String(port)}`
  const stale = 'dsh-account=127.0.0.1:1'
  const current = `dsh-account=127.0.0.1:${String(port)}`
  const probe = async (cookie: string): Promise<boolean> => {
    const response = await hostFetch(`${origin}/`, hostSessionRequestInit(cookie, { redirect: 'manual' }))
    await response.body?.cancel()
    return response.ok
  }
  expect(await probe(stale)).toBe(false)
  expect(await probe(current)).toBe(true)
  const forgotten: string[] = []
  const cookie = await resolveHostSessionCookie({
    stored: async () => stale,
    prompt: async () => current,
    forget: async () => { forgotten.push('stale') },
    probe,
  })
  expect(cookie).toBe(current)
  expect(forgotten).toEqual(['stale'])
  const admitted = await hostFetch(`${origin}/`, hostSessionRequestInit(cookie, { redirect: 'manual' }))
  expect(admitted.status).toBe(200)
  expect(await admitted.text()).toBe('ok')
})

it('keeps a stored cookie the current Host already accepts', async () => {
  const cookie = await resolveHostSessionCookie({
    stored: async () => 'dsh-account=current',
    prompt: async () => { throw new Error('prompt') },
    forget: async () => { throw new Error('forget') },
    probe: async () => true,
  })
  expect(cookie).toBe('dsh-account=current')
})
