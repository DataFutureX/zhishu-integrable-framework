const DEFAULT_WIDTH = 310
const DEFAULT_HEIGHT = 155
export const SLIDE_CAPTCHA_BLOCK_SIZE = 42
export const SLIDE_CAPTCHA_BLOCK_RADIUS = 9

export interface SlideCaptchaGenerateOptions {
  width?: number
  height?: number
  slideX: number
  sliderY: number
  blockSize?: number
}

export interface SlideCaptchaGenerateResult {
  backgroundImage: string
  sliderImage: string
  width: number
  height: number
  blockSize: number
  /** 滑块图相对 sliderY 的向上偏移（用于与背景缺口对齐） */
  sliderImageOffsetY: number
}

const randomInt = (min: number, max: number) => Math.floor(Math.random() * (max - min + 1)) + min

const randomColor = () => {
  const hue = randomInt(0, 359)
  const saturation = randomInt(45, 75)
  const lightness = randomInt(40, 65)
  return `hsl(${hue}, ${saturation}%, ${lightness}%)`
}

/** 拼图块路径（顶部、右侧凸起），(x,y) 为方块主体左上角 */
const drawPuzzlePath = (
  ctx: CanvasRenderingContext2D,
  x: number,
  y: number,
  size: number,
  radius = SLIDE_CAPTCHA_BLOCK_RADIUS,
) => {
  const half = size / 2
  ctx.beginPath()
  ctx.moveTo(x, y)
  ctx.lineTo(x + half - radius, y)
  ctx.arc(x + half, y - radius, radius, Math.PI, 0, false)
  ctx.lineTo(x + size, y)
  ctx.lineTo(x + size, y + half - radius)
  ctx.arc(x + size + radius, y + half, radius, -Math.PI / 2, Math.PI / 2, false)
  ctx.lineTo(x + size, y + size)
  ctx.lineTo(x, y + size)
  ctx.closePath()
}

/** 拼图完整外接矩形：宽/高 = blockSize + 2*radius，顶部需预留 2*radius */
const getPuzzleLayout = (blockSize: number) => {
  const padding = SLIDE_CAPTCHA_BLOCK_RADIUS * 2
  return {
    canvasWidth: blockSize + padding,
    canvasHeight: blockSize + padding,
    pathX: 0,
    pathY: padding,
    imageOffsetY: padding,
  }
}

const loadImage = (src: string) =>
  new Promise<HTMLImageElement>((resolve, reject) => {
    const image = new Image()
    image.onload = () => resolve(image)
    image.onerror = () => reject(new Error('验证码图片加载失败'))
    image.src = src
  })

const toImageSrc = (value: string) => {
  if (!value) return ''
  if (value.startsWith('data:')) return value
  return `data:image/png;base64,${value}`
}

const paintRandomBackground = (ctx: CanvasRenderingContext2D, width: number, height: number) => {
  const gradient = ctx.createLinearGradient(0, 0, width, height)
  gradient.addColorStop(0, randomColor())
  gradient.addColorStop(0.55, randomColor())
  gradient.addColorStop(1, randomColor())
  ctx.fillStyle = gradient
  ctx.fillRect(0, 0, width, height)

  for (let i = 0; i < 18; i += 1) {
    ctx.fillStyle = `rgba(255, 255, 255, ${Math.random() * 0.18})`
    ctx.beginPath()
    ctx.arc(randomInt(0, width), randomInt(0, height), randomInt(8, 36), 0, Math.PI * 2)
    ctx.fill()
  }

  for (let i = 0; i < 10; i += 1) {
    ctx.strokeStyle = `rgba(255, 255, 255, ${Math.random() * 0.25 + 0.08})`
    ctx.lineWidth = randomInt(1, 3)
    ctx.beginPath()
    ctx.moveTo(randomInt(0, width), randomInt(0, height))
    ctx.lineTo(randomInt(0, width), randomInt(0, height))
    ctx.stroke()
  }

  ctx.fillStyle = 'rgba(0, 0, 0, 0.06)'
  for (let i = 0; i < 1200; i += 1) {
    ctx.fillRect(randomInt(0, width), randomInt(0, height), 1, 1)
  }
}

/** 随机生成滑动验证码背景图与滑块图 */
export const generateSlideCaptchaImages = (
  options: SlideCaptchaGenerateOptions,
): SlideCaptchaGenerateResult => {
  const width = options.width ?? DEFAULT_WIDTH
  const height = options.height ?? DEFAULT_HEIGHT
  const blockSize = options.blockSize ?? SLIDE_CAPTCHA_BLOCK_SIZE
  const { slideX, sliderY } = options
  const layout = getPuzzleLayout(blockSize)

  const backgroundCanvas = document.createElement('canvas')
  backgroundCanvas.width = width
  backgroundCanvas.height = height
  const backgroundCtx = backgroundCanvas.getContext('2d')
  if (!backgroundCtx) {
    throw new Error('无法创建验证码画布')
  }

  paintRandomBackground(backgroundCtx, width, height)

  const blockCanvas = document.createElement('canvas')
  blockCanvas.width = layout.canvasWidth
  blockCanvas.height = layout.canvasHeight
  const blockCtx = blockCanvas.getContext('2d')
  if (!blockCtx) {
    throw new Error('无法创建滑块画布')
  }

  drawPuzzlePath(blockCtx, layout.pathX, layout.pathY, blockSize)
  blockCtx.save()
  blockCtx.clip()
  blockCtx.drawImage(
    backgroundCanvas,
    -slideX,
    -(sliderY - layout.imageOffsetY),
  )
  blockCtx.restore()

  blockCtx.save()
  drawPuzzlePath(blockCtx, layout.pathX, layout.pathY, blockSize)
  blockCtx.strokeStyle = 'rgba(255, 255, 255, 0.85)'
  blockCtx.lineWidth = 2
  blockCtx.lineJoin = 'round'
  blockCtx.stroke()
  blockCtx.restore()

  backgroundCtx.save()
  drawPuzzlePath(backgroundCtx, slideX, sliderY, blockSize)
  backgroundCtx.globalCompositeOperation = 'destination-out'
  backgroundCtx.fill()
  backgroundCtx.globalCompositeOperation = 'source-over'
  drawPuzzlePath(backgroundCtx, slideX, sliderY, blockSize)
  backgroundCtx.strokeStyle = 'rgba(255, 255, 255, 0.35)'
  backgroundCtx.lineWidth = 1
  backgroundCtx.lineJoin = 'round'
  backgroundCtx.stroke()
  backgroundCtx.restore()

  return {
    backgroundImage: backgroundCanvas.toDataURL('image/png').replace(/^data:image\/png;base64,/, ''),
    sliderImage: blockCanvas.toDataURL('image/png').replace(/^data:image\/png;base64,/, ''),
    width,
    height,
    blockSize: layout.canvasWidth,
    sliderImageOffsetY: layout.imageOffsetY,
  }
}

/** 通过模板匹配从后端返回的图片中推断缺口横坐标 */
export const detectSlideX = async (
  backgroundImage: string,
  sliderImage: string,
  sliderY: number,
  width = DEFAULT_WIDTH,
): Promise<number | null> => {
  try {
    const [background, slider] = await Promise.all([
      loadImage(toImageSrc(backgroundImage)),
      loadImage(toImageSrc(sliderImage)),
    ])

    const canvas = document.createElement('canvas')
    canvas.width = background.naturalWidth || width
    canvas.height = background.naturalHeight || DEFAULT_HEIGHT
    const ctx = canvas.getContext('2d', { willReadFrequently: true })
    if (!ctx) return null

    ctx.drawImage(background, 0, 0, canvas.width, canvas.height)
    const backgroundData = ctx.getImageData(0, 0, canvas.width, canvas.height).data

    const sliderCanvas = document.createElement('canvas')
    sliderCanvas.width = slider.naturalWidth
    sliderCanvas.height = slider.naturalHeight
    const sliderCtx = sliderCanvas.getContext('2d', { willReadFrequently: true })
    if (!sliderCtx) return null

    sliderCtx.drawImage(slider, 0, 0)
    const sliderData = sliderCtx.getImageData(0, 0, sliderCanvas.width, sliderCanvas.height).data

    const maxX = canvas.width - sliderCanvas.width
    let bestX = 0
    let bestScore = Number.POSITIVE_INFINITY

    for (let x = 0; x <= maxX; x += 1) {
      let score = 0
      let samples = 0

      for (let py = 0; py < sliderCanvas.height; py += 1) {
        const targetY = sliderY - SLIDE_CAPTCHA_BLOCK_RADIUS * 2 + py
        if (targetY < 0 || targetY >= canvas.height) continue

        for (let px = 0; px < sliderCanvas.width; px += 2) {
          const sliderIndex = (py * sliderCanvas.width + px) * 4
          const alpha = sliderData[sliderIndex + 3]
          if (alpha < 20) continue

          const bgIndex = (targetY * canvas.width + (x + px)) * 4
          const dr = backgroundData[bgIndex] - sliderData[sliderIndex]
          const dg = backgroundData[bgIndex + 1] - sliderData[sliderIndex + 1]
          const db = backgroundData[bgIndex + 2] - sliderData[sliderIndex + 2]
          score += dr * dr + dg * dg + db * db
          samples += 1
        }
      }

      if (samples > 0) {
        const normalized = score / samples
        if (normalized < bestScore) {
          bestScore = normalized
          bestX = x
        }
      }
    }

    return bestScore === Number.POSITIVE_INFINITY ? null : bestX
  } catch {
    return null
  }
}
