/**
 * 格式化日期时间
 * @param date 日期对象或时间戳
 * @param format 格式字符串，默认 'YYYY-MM-DD HH:mm:ss'
 */
export const formatDateTime = (
  date?: Date | string | number | null,
  format = 'YYYY-MM-DD HH:mm:ss',
): string => {
  if (date == null || date === '') return ''
  const d = new Date(date)
  if (Number.isNaN(d.getTime())) return ''

  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hours = String(d.getHours()).padStart(2, '0')
  const minutes = String(d.getMinutes()).padStart(2, '0')
  const seconds = String(d.getSeconds()).padStart(2, '0')

  return format
    .replace('YYYY', String(year))
    .replace('MM', month)
    .replace('DD', day)
    .replace('HH', hours)
    .replace('mm', minutes)
    .replace('ss', seconds)
}

/**
 * 相对时间：刚刚 / N 分钟前 / N 小时前 / MM-DD HH:mm / YYYY-MM-DD
 * 会话列表、消息列表共用
 */
export const formatRelativeTime = (value?: string | Date | null): string => {
  if (!value) return ''
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) return ''

  const now = new Date()
  const diffMin = Math.floor((now.getTime() - date.getTime()) / 60000)
  if (diffMin < 1) return '刚刚'
  if (diffMin < 60) return `${diffMin} 分钟前`

  const diffHour = Math.floor(diffMin / 60)
  if (diffHour < 24) return `${diffHour} 小时前`

  const mm = String(date.getMonth() + 1).padStart(2, '0')
  const dd = String(date.getDate()).padStart(2, '0')
  const hh = String(date.getHours()).padStart(2, '0')
  const mi = String(date.getMinutes()).padStart(2, '0')
  if (date.getFullYear() === now.getFullYear()) return `${mm}-${dd} ${hh}:${mi}`
  return `${date.getFullYear()}-${mm}-${dd}`
}

/**
 * 格式化文件大小
 * @param bytes 字节数
 */
export const formatFileSize = (bytes: number): string => {
  if (bytes === 0) return '0 B'

  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))

  return `${(bytes / Math.pow(k, i)).toFixed(2)} ${sizes[i]}`
}

/**
 * 防抖函数
 * @param fn 要执行的函数
 * @param delay 延迟时间（毫秒）
 */
export const debounce = <T extends (...args: any[]) => any>(
  fn: T,
  delay = 300,
): ((...args: Parameters<T>) => void) => {
  let timer: ReturnType<typeof setTimeout> | null = null

  return function (this: unknown, ...args: Parameters<T>) {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => {
      fn.apply(this, args)
    }, delay)
  }
}

/**
 * 节流函数
 * @param fn 要执行的函数
 * @param interval 间隔时间（毫秒）
 */
export const throttle = <T extends (...args: any[]) => any>(
  fn: T,
  interval = 300,
): ((...args: Parameters<T>) => void) => {
  let lastTime = 0

  return function (this: unknown, ...args: Parameters<T>) {
    const now = Date.now()
    if (now - lastTime >= interval) {
      lastTime = now
      fn.apply(this, args)
    }
  }
}

/**
 * 深拷贝
 * @param obj 要拷贝的对象
 */
export const deepClone = <T>(obj: T): T => {
  if (obj === null || typeof obj !== 'object') return obj

  if (obj instanceof Date) return new Date(obj.getTime()) as any
  if (obj instanceof Array) return obj.map((item) => deepClone(item)) as any
  if (obj instanceof Object) {
    const clonedObj = {} as T
    for (const key in obj) {
      if (Object.prototype.hasOwnProperty.call(obj, key)) {
        clonedObj[key] = deepClone(obj[key])
      }
    }
    return clonedObj
  }

  return obj
}

/** 判断监测要素是否为雨量类（图表切换柱状图） */
export function isRainfallElement(
  element?: { elementCode: string; elementName: string } | null,
): boolean {
  if (!element) return false
  const code = element.elementCode.toLowerCase()
  const name = element.elementName
  return (
    code.includes('rain') ||
    code.includes('precip') ||
    name.includes('雨量') ||
    name.includes('降雨')
  )
}
