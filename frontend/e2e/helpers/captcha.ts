import type { Page } from '@playwright/test'

type CaptchaPayload = {
  backgroundImage?: string
  sliderImage?: string
  sliderY?: number
  sliderImageOffsetY?: number
}

/** 通过「空洞更暗」算法估算拼图目标 X（原图像素） */
export async function solveSlideX(page: Page, captcha: CaptchaPayload) {
  const bg = captcha.backgroundImage || ''
  const slider = captcha.sliderImage || ''
  const sliderY = Number(captcha.sliderY || 0)
  const offsetY = Number(captcha.sliderImageOffsetY || 0)

  return page.evaluate(
    async ({ bg, slider, sliderY, offsetY }) => {
      const toSrc = (v: string) => (String(v).startsWith('data:') ? v : `data:image/png;base64,${v}`)
      const load = (src: string) =>
        new Promise<HTMLImageElement>((resolve, reject) => {
          const img = new Image()
          img.onload = () => resolve(img)
          img.onerror = reject
          img.src = toSrc(src)
        })

      const bgImg = await load(bg)
      const slImg = await load(slider)
      const bgCanvas = document.createElement('canvas')
      bgCanvas.width = bgImg.width
      bgCanvas.height = bgImg.height
      const bgCtx = bgCanvas.getContext('2d')
      if (!bgCtx) throw new Error('canvas 2d unavailable')
      bgCtx.drawImage(bgImg, 0, 0)
      const bgData = bgCtx.getImageData(0, 0, bgImg.width, bgImg.height).data

      const slCanvas = document.createElement('canvas')
      slCanvas.width = slImg.width
      slCanvas.height = slImg.height
      const slCtx = slCanvas.getContext('2d')
      if (!slCtx) throw new Error('canvas 2d unavailable')
      slCtx.drawImage(slImg, 0, 0)
      const slData = slCtx.getImageData(0, 0, slImg.width, slImg.height).data

      let bestX = 40
      let bestScore = -1
      const maxX = Math.max(bgImg.width - slImg.width, 40)

      for (let x = 20; x <= maxX; x++) {
        let darkness = 0
        let count = 0
        for (let sy = 0; sy < slImg.height; sy++) {
          for (let sx = 0; sx < slImg.width; sx++) {
            const si = (sy * slImg.width + sx) * 4
            if (slData[si + 3] < 120) continue
            const bx = x + sx
            const by = sliderY - offsetY + sy
            if (bx < 0 || by < 0 || bx >= bgImg.width || by >= bgImg.height) continue
            const bi = (by * bgImg.width + bx) * 4
            darkness += 255 - bgData[bi] + (255 - bgData[bi + 1]) + (255 - bgData[bi + 2])
            count++
          }
        }
        if (!count) continue
        const score = darkness / count
        if (score > bestScore) {
          bestScore = score
          bestX = x
        }
      }
      return { bestX, bestScore, bgWidth: bgImg.width }
    },
    { bg, slider, sliderY, offsetY },
  )
}

export async function completeSlideCaptcha(page: Page, captchaPayload: CaptchaPayload | null) {
  const captcha = page.locator('.slide-captcha').first()
  await captcha.waitFor({ state: 'visible', timeout: 15_000 })
  await captcha.locator('.slide-captcha__loading').waitFor({ state: 'hidden', timeout: 15_000 }).catch(() => {})
  const thumb = captcha.locator('.slide-captcha__thumb')
  await thumb.waitFor({ state: 'visible', timeout: 15_000 })
  await page.waitForTimeout(500)

  let targetDisplayX = 200
  if (captchaPayload) {
    const solved = await solveSlideX(page, captchaPayload)
    const wrap = captcha.locator('.slide-captcha__image-wrap')
    const wrapBox = await wrap.boundingBox()
    const scale = wrapBox ? wrapBox.width / solved.bgWidth : 1
    targetDisplayX = Math.round(solved.bestX * scale)
  }

  const box = await thumb.boundingBox()
  if (!box) throw new Error('captcha thumb not found')
  const startX = box.x + box.width / 2
  const startY = box.y + box.height / 2
  const endX = startX + Math.max(targetDisplayX, 40)

  await page.mouse.move(startX, startY)
  await page.mouse.down()
  await page.mouse.move(endX, startY, { steps: 28 })
  await page.mouse.up()

  await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 25_000 })
}
