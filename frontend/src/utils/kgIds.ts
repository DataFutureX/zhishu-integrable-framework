type KgIdSource = {
  bizId?: string | number | null
  projectId?: string | number | null
  properties?: Record<string, unknown> | null
}

function asId(value: unknown): string {
  if (value == null || value === '') return ''
  return String(value)
}

export function resolveKgBizId(node: KgIdSource): string {
  const direct = asId(node.bizId)
  if (direct) return direct
  return asId(node.properties?.bizId)
}

export function resolveKgProjectId(node: KgIdSource): string {
  const direct = asId(node.projectId)
  if (direct) return direct
  return asId(node.properties?.projectId)
}
