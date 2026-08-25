/** 演示滑动验证码尺寸（与前端组件默认展示宽度一致） */
const DEMO_CAPTCHA_WIDTH = 310
const DEMO_CAPTCHA_HEIGHT = 155
const DEMO_SLIDER_SIZE = 50

function toSvgDataUri(svg: string): string {
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
}

/**
 * 生成可正常拖动的演示验证码图（尺寸需足够大，否则 maxSlideX 会为 0）
 */
export function createDemoCaptchaImages(slideX = 160, sliderY = 52) {
  const size = DEMO_SLIDER_SIZE
  const backgroundSvg = `
<svg xmlns="http://www.w3.org/2000/svg" width="${DEMO_CAPTCHA_WIDTH}" height="${DEMO_CAPTCHA_HEIGHT}" viewBox="0 0 ${DEMO_CAPTCHA_WIDTH} ${DEMO_CAPTCHA_HEIGHT}">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0%" stop-color="#1a6bb5"/>
      <stop offset="45%" stop-color="#2f9fd6"/>
      <stop offset="100%" stop-color="#7ec8e8"/>
    </linearGradient>
    <pattern id="wave" width="40" height="20" patternUnits="userSpaceOnUse">
      <path d="M0 10 Q10 0 20 10 T40 10" fill="none" stroke="rgba(255,255,255,0.18)" stroke-width="2"/>
    </pattern>
  </defs>
  <rect width="100%" height="100%" fill="url(#bg)"/>
  <rect width="100%" height="100%" fill="url(#wave)"/>
  <text x="16" y="28" fill="rgba(255,255,255,0.85)" font-size="14" font-family="sans-serif">演示验证 · 拖动滑块完成</text>
  <rect x="${slideX}" y="${sliderY}" width="${size}" height="${size}" rx="6" fill="rgba(0,0,0,0.28)" stroke="rgba(255,255,255,0.35)" stroke-width="1" stroke-dasharray="4 3"/>
</svg>`.trim()

  const sliderSvg = `
<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 ${size} ${size}">
  <defs>
    <linearGradient id="piece" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0%" stop-color="#5eb6ef"/>
      <stop offset="100%" stop-color="#2b7fc0"/>
    </linearGradient>
  </defs>
  <rect x="1" y="1" width="${size - 2}" height="${size - 2}" rx="6" fill="url(#piece)" stroke="#fff" stroke-width="2"/>
  <path d="M14 25h22M25 14v22" stroke="rgba(255,255,255,0.7)" stroke-width="2" stroke-linecap="round"/>
</svg>`.trim()

  return {
    captchaId: `demo-captcha-${Date.now()}`,
    backgroundImage: toSvgDataUri(backgroundSvg),
    sliderImage: toSvgDataUri(sliderSvg),
    sliderY,
    slideX,
    sliderImageOffsetY: 0,
  }
}

export function delay(ms = 120): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

export function nowStr(): string {
  return new Date().toISOString().slice(0, 19).replace('T', ' ')
}

export function daysAgoStr(days: number, hour = 10, minute = 0): string {
  const d = new Date()
  d.setDate(d.getDate() - days)
  d.setHours(hour, minute, 0, 0)
  return d.toISOString().slice(0, 19).replace('T', ' ')
}

export function paginate<T>(
  records: T[],
  pageNum = 1,
  pageSize = 10,
  filter?: (item: T) => boolean,
) {
  const filtered = filter ? records.filter(filter) : [...records]
  const size = pageSize || 10
  const total = filtered.length
  const pages = Math.max(1, Math.ceil(total / size))
  const current = Math.min(Math.max(1, pageNum), pages)
  const start = (current - 1) * size
  return {
    current,
    size,
    total,
    pages,
    records: filtered.slice(start, start + size),
  }
}

export function filterByTimeRange<T extends Record<string, unknown>>(
  records: T[],
  startTime?: string,
  endTime?: string,
  field = 'receiveTime',
) {
  if (!startTime && !endTime) return records
  return records.filter((item) => {
    const raw = item[field]
    if (!raw) return false
    const t = new Date(String(raw)).getTime()
    if (startTime && t < new Date(startTime).getTime()) return false
    if (endTime && t > new Date(endTime).getTime()) return false
    return true
  })
}

export function matchPath(pattern: RegExp, path: string) {
  const m = path.match(pattern)
  if (!m) return null
  const params: Record<string, string> = {}
  m.slice(1).forEach((val, i) => {
    params[`p${i}`] = val
  })
  return { match: m, params }
}

export function deepClone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T
}

export function nextId(counter: { value: number }): number {
  counter.value += 1
  return counter.value
}
