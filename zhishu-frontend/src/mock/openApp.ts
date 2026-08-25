import type { GenerateAkSkResult, OpenAppVO } from '@/types/openApp'
import { nowStr } from './utils'

function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T
}

function randomToken(prefix: string): string {
  const hex = Array.from({ length: 16 }, () => Math.floor(Math.random() * 16).toString(16)).join('')
  return `${prefix}${hex}`
}

const seedApps = (): OpenAppVO[] => [
  {
    id: 1,
    code: 'partner-portal',
    name: '伙伴门户',
    status: 'ENABLED',
    allowedScopes: JSON.stringify(['chat', 'knowledges']),
    remark: '演示：伙伴门户调用开放对话与知识问答',
    accessKey: 'ak_demo_portal_8f3a',
    akskGeneratedAt: nowStr(),
    lastUsedAt: nowStr(),
    createTime: nowStr(),
  },
  {
    id: 2,
    code: 'iot-briefing',
    name: '数智简报',
    status: 'ENABLED',
    allowedScopes: JSON.stringify(['chat']),
    remark: '演示：定时拉取 Agent 摘要',
    accessKey: 'ak_demo_iot_2c91',
    akskGeneratedAt: nowStr(),
    lastUsedAt: null,
    createTime: nowStr(),
  },
]

let apps = seedApps()
let nextAppId = 3

export function resetOpenAppDemoState() {
  apps = seedApps()
  nextAppId = 3
}

export function listDemoOpenApps(): OpenAppVO[] {
  return clone(apps)
}

export function getDemoOpenApp(id: number): OpenAppVO | undefined {
  const found = apps.find((item) => item.id === id)
  return found ? clone(found) : undefined
}

export function createDemoOpenApp(body: Record<string, unknown>): OpenAppVO {
  const scopes = Array.isArray(body.allowedScopes) ? body.allowedScopes : []
  const created: OpenAppVO = {
    id: nextAppId++,
    code: String(body.code || `app-${Date.now()}`),
    name: String(body.name || '演示应用'),
    status: 'ENABLED',
    allowedScopes: JSON.stringify(scopes),
    remark: String(body.remark || ''),
    accessKey: null,
    akskGeneratedAt: null,
    lastUsedAt: null,
    createTime: nowStr(),
  }
  apps.unshift(created)
  return clone(created)
}

export function updateDemoOpenApp(id: number, body: Record<string, unknown>): OpenAppVO | undefined {
  const found = apps.find((item) => item.id === id)
  if (!found) return undefined
  if (body.code != null) found.code = String(body.code)
  if (body.name != null) found.name = String(body.name)
  if (body.remark != null) found.remark = String(body.remark)
  if (Array.isArray(body.allowedScopes)) {
    found.allowedScopes = JSON.stringify(body.allowedScopes)
  }
  return clone(found)
}

export function deleteDemoOpenApp(id: number): boolean {
  const before = apps.length
  apps = apps.filter((item) => item.id !== id)
  return apps.length < before
}

export function generateDemoAkSk(id: number): GenerateAkSkResult | undefined {
  const found = apps.find((item) => item.id === id)
  if (!found) return undefined
  found.accessKey = randomToken('ak_demo_')
  found.akskGeneratedAt = nowStr()
  return { accessKey: found.accessKey, secretKey: randomToken('sk_demo_') }
}

export function regenerateDemoSk(id: number): GenerateAkSkResult | undefined {
  const found = apps.find((item) => item.id === id)
  if (!found || !found.accessKey) return undefined
  found.akskGeneratedAt = nowStr()
  return { accessKey: found.accessKey, secretKey: randomToken('sk_demo_') }
}

export function updateDemoOpenAppScopes(id: number, scopes: string[]): OpenAppVO | undefined {
  const found = apps.find((item) => item.id === id)
  if (!found) return undefined
  found.allowedScopes = JSON.stringify(scopes)
  return clone(found)
}

export function updateDemoOpenAppStatus(id: number, status: string): OpenAppVO | undefined {
  const found = apps.find((item) => item.id === id)
  if (!found) return undefined
  found.status = status
  return clone(found)
}
