import { chromium } from '@playwright/test'
import { readFileSync, writeFileSync, copyFileSync, unlinkSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const frontendDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const rootDir = path.resolve(frontendDir, '..')
const publicDir = path.join(frontendDir, 'public')
const svg = readFileSync(path.join(rootDir, 'logo.svg'), 'utf8')

async function exportPng(size, outPath) {
  const browser = await chromium.launch()
  const page = await browser.newPage({
    viewport: { width: size, height: size },
    deviceScaleFactor: 1,
  })
  const html = `<!doctype html><html><head><style>
    html,body{margin:0;background:transparent;width:${size}px;height:${size}px;}
    svg{display:block;width:${size}px;height:${size}px;}
  </style></head><body>${svg.replace('<svg', `<svg width="${size}" height="${size}"`)}</body></html>`
  await page.setContent(html, { waitUntil: 'load' })
  await page.screenshot({ path: outPath, omitBackground: true })
  await browser.close()
  console.log('wrote', outPath)
}

function buildIco(images) {
  const count = images.length
  const headerSize = 6 + 16 * count
  let offset = headerSize
  const entries = images.map((image) => {
    const entry = { ...image, offset }
    offset += image.data.length
    return entry
  })
  const buf = Buffer.alloc(offset)
  buf.writeUInt16LE(0, 0)
  buf.writeUInt16LE(1, 2)
  buf.writeUInt16LE(count, 4)
  entries.forEach((entry, index) => {
    const o = 6 + index * 16
    const dim = entry.size >= 256 ? 0 : entry.size
    buf.writeUInt8(dim, o)
    buf.writeUInt8(dim, o + 1)
    buf.writeUInt8(0, o + 2)
    buf.writeUInt8(0, o + 3)
    buf.writeUInt16LE(1, o + 4)
    buf.writeUInt16LE(32, o + 6)
    buf.writeUInt32LE(entry.data.length, o + 8)
    buf.writeUInt32LE(entry.offset, o + 12)
    entry.data.copy(buf, entry.offset)
  })
  return buf
}

const png16 = path.join(publicDir, '_logo-16.png')
const png32 = path.join(publicDir, 'favicon-32.png')
const png48 = path.join(publicDir, '_logo-48.png')
const png512 = path.join(publicDir, 'favicon-square.png')

await exportPng(16, png16)
await exportPng(32, png32)
await exportPng(48, png48)
await exportPng(512, png512)
await exportPng(512, path.join(rootDir, 'logo.png'))

copyFileSync(png32, path.join(publicDir, 'favicon-32.png'))
copyFileSync(png512, path.join(publicDir, 'favicon-square.png'))

const ico = buildIco([
  { size: 16, data: readFileSync(png16) },
  { size: 32, data: readFileSync(png32) },
  { size: 48, data: readFileSync(png48) },
])
writeFileSync(path.join(publicDir, 'favicon.ico'), ico)
unlinkSync(png16)
unlinkSync(png48)
console.log('wrote', path.join(publicDir, 'favicon.ico'))
