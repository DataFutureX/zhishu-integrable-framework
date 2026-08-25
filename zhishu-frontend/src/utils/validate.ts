/**
 * 验证邮箱格式
 */
export const isValidEmail = (email: string): boolean => {
  const regex = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/
  return regex.test(email)
}

/**
 * 验证手机号格式（中国大陆）
 */
export const isValidPhone = (phone: string): boolean => {
  const regex = /^1[3-9]\d{9}$/
  return regex.test(phone)
}

/**
 * 验证密码强度
 * 至少包含字母和数字，长度6-20位
 */
export const isValidPassword = (password: string): boolean => {
  const regex = /^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{6,20}$/
  return regex.test(password)
}

/**
 * 验证是否为空
 */
export const isEmpty = (value: any): boolean => {
  if (value === null || value === undefined) return true
  if (typeof value === 'string') return value.trim().length === 0
  if (Array.isArray(value)) return value.length === 0
  if (typeof value === 'object') return Object.keys(value).length === 0
  return false
}
