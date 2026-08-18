const FAVICON_LINK_ID = 'app-dynamic-favicon'
const FAVICON_SIZE = 64

let currentTask: Promise<void> | null = null

const getOrCreateFaviconLink = () => {
  let link = document.getElementById(FAVICON_LINK_ID) as HTMLLinkElement | null
  if (!link) {
    link = document.createElement('link')
    link.id = FAVICON_LINK_ID
    link.rel = 'icon'
    link.type = 'image/png'
    document.head.appendChild(link)
  }
  return link
}

const loadImage = (url: string) =>
  new Promise<HTMLImageElement>((resolve, reject) => {
    const image = new Image()
    image.crossOrigin = 'anonymous'
    image.onload = () => resolve(image)
    image.onerror = () => reject(new Error(`图标加载失败: ${url}`))
    image.src = url
  })

const renderSquareFavicon = (image: HTMLImageElement, size = FAVICON_SIZE) => {
  const canvas = document.createElement('canvas')
  canvas.width = size
  canvas.height = size
  const context = canvas.getContext('2d')
  if (!context) return ''

  context.clearRect(0, 0, size, size)
  const scale = Math.min(size / image.width, size / image.height)
  const width = image.width * scale
  const height = image.height * scale
  const offsetX = (size - width) / 2
  const offsetY = (size - height) / 2
  context.drawImage(image, offsetX, offsetY, width, height)
  return canvas.toDataURL('image/png')
}

/**
 * 将任意比例图标适配为方形 favicon，避免浏览器标签页横向挤压变形
 */
export const updateBrowserFavicon = (iconUrl?: string) => {
  if (!iconUrl) return Promise.resolve()

  const task = (async () => {
    try {
      const image = await loadImage(iconUrl)
      const dataUrl = renderSquareFavicon(image)
      if (!dataUrl) return

      const link = getOrCreateFaviconLink()
      link.type = 'image/png'
      link.setAttribute('sizes', `${FAVICON_SIZE}x${FAVICON_SIZE}`)
      link.href = dataUrl
    } catch (error) {
      console.warn('更新浏览器图标失败:', error)
    }
  })()

  currentTask = task
  return task
}

export const waitBrowserFaviconUpdate = () => currentTask ?? Promise.resolve()
