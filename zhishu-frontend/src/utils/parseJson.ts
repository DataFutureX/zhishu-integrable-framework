/**
 * 解析 JSON 并将超出安全整数范围的大整数转为字符串
 */
export function parseJsonWithBigInt<T = unknown>(text: string): T | null {
  try {
    const processed = text.replace(
      /:\s*(-?\d{16,})/g,
      (_match, numberStr: string) => {
        const num = Number(numberStr)
        if (num > Number.MAX_SAFE_INTEGER || num < Number.MIN_SAFE_INTEGER) {
          return `:"${numberStr}"`
        }
        return _match
      },
    )
    return JSON.parse(processed) as T
  } catch {
    try {
      return JSON.parse(text) as T
    } catch {
      return null
    }
  }
}
