import { describe, expect, it } from 'vitest'
import { formatDateTime, formatFileSize, isRainfallElement } from '@/utils/format'

describe('formatDateTime', () => {
  it('formats a fixed date with default pattern', () => {
    const result = formatDateTime(new Date('2026-07-21T14:30:05'))
    expect(result).toBe('2026-07-21 14:30:05')
  })
})

describe('formatFileSize', () => {
  it('formats zero and kilobytes', () => {
    expect(formatFileSize(0)).toBe('0 B')
    expect(formatFileSize(1024)).toBe('1.00 KB')
  })
})

describe('isRainfallElement', () => {
  it('detects rainfall by code or name', () => {
    expect(isRainfallElement({ elementCode: 'rain_1h', elementName: '小时雨量' })).toBe(true)
    expect(isRainfallElement({ elementCode: 'temp', elementName: '温度' })).toBe(false)
    expect(isRainfallElement(null)).toBe(false)
  })
})
