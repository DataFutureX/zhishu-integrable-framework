/** 将 ASCII 字符串转为十六进制（大写，无分隔符） */
export function stringToHex(value: string): string {
  return Array.from(value)
    .map((char) => char.charCodeAt(0).toString(16).padStart(2, '0'))
    .join('')
    .toUpperCase()
}

/** 十六进制转字节数组 */
export function hexToBytes(hex: string): Uint8Array | null {
  const normalized = hex.replace(/\s+/g, '').toUpperCase()
  if (!normalized || normalized.length % 2 !== 0 || !/^[0-9A-F]+$/.test(normalized)) {
    return null
  }

  const bytes = new Uint8Array(normalized.length / 2)
  for (let i = 0; i < normalized.length; i += 2) {
    bytes[i / 2] = Number.parseInt(normalized.slice(i, i + 2), 16)
  }
  return bytes
}

/** 将十六进制字符串解码为 ASCII，无法解码时返回 null */
export function hexToString(hex: string): string | null {
  const bytes = hexToBytes(hex)
  if (!bytes) return null
  return Array.from(bytes, (code) => String.fromCharCode(code)).join('')
}

/** 将十六进制解码为 UTF-8 文本 */
export function hexToUtf8String(hex: string): string | null {
  const bytes = hexToBytes(hex)
  if (!bytes) return null
  try {
    return new TextDecoder('utf-8', { fatal: true }).decode(bytes)
  } catch {
    return null
  }
}

/** 十六进制分组显示，如 41542B5645 */
export function formatHexDisplay(hex: string): string {
  const normalized = hex.replace(/\s+/g, '').toUpperCase()
  if (!normalized || normalized.length % 2 !== 0 || !/^[0-9A-F]+$/.test(normalized)) {
    return hex
  }
  return normalized.match(/.{1,2}/g)?.join(' ') ?? hex
}

/** 尝试将十六进制或原始文本格式化为可读内容 */
export function formatRemoteConfigPayload(payload: string): string {
  const trimmed = payload.trim()
  if (!trimmed) return ''

  const utf8 = hexToUtf8String(trimmed)
  if (utf8) return utf8

  const decoded = hexToString(trimmed)
  if (decoded && /^[\x20-\x7E\r\n\t]+$/.test(decoded)) {
    return decoded
  }
  return trimmed
}

/** AT 指令转十六进制，自动补全 \r\n 结尾 */
export function atCommandToHex(command: string): string {
  const trimmed = command.trim()
  if (!trimmed) return ''
  const withSuffix = /\r\n$/.test(trimmed) ? trimmed : `${trimmed}\r\n`
  return stringToHex(withSuffix)
}
