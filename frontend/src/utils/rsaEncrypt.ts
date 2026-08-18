import JSEncrypt from 'jsencrypt'

const PEM_HEADER = '-----BEGIN PUBLIC KEY-----'
const PEM_FOOTER = '-----END PUBLIC KEY-----'

/** 将公钥规范化为 PEM 格式 */
export const normalizePublicKey = (publicKey: string): string => {
  const trimmed = publicKey.trim()
  if (trimmed.includes(PEM_HEADER)) {
    return trimmed
  }

  const base64 = trimmed.replace(/\s/g, '')
  const lines = base64.match(/.{1,64}/g) ?? [base64]
  return `${PEM_HEADER}\n${lines.join('\n')}\n${PEM_FOOTER}`
}

const isOaepAlgorithm = (algorithm?: string) => {
  if (!algorithm) return false
  const normalized = algorithm.toUpperCase()
  return normalized.includes('OAEP')
}

const pemToArrayBuffer = (pem: string): ArrayBuffer => {
  const base64 = pem
    .replace(PEM_HEADER, '')
    .replace(PEM_FOOTER, '')
    .replace(/\s/g, '')
  const binary = atob(base64)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i += 1) {
    bytes[i] = binary.charCodeAt(i)
  }
  return bytes.buffer
}

const encryptWithOaep = async (plainText: string, publicKey: string): Promise<string> => {
  const pem = normalizePublicKey(publicKey)
  const cryptoKey = await crypto.subtle.importKey(
    'spki',
    pemToArrayBuffer(pem),
    { name: 'RSA-OAEP', hash: 'SHA-256' },
    false,
    ['encrypt'],
  )

  const encoded = new TextEncoder().encode(plainText)
  const encrypted = await crypto.subtle.encrypt({ name: 'RSA-OAEP' }, cryptoKey, encoded)
  const bytes = new Uint8Array(encrypted)
  let binary = ''
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte)
  })
  return btoa(binary)
}

const encryptWithPkcs1 = (plainText: string, publicKey: string): string => {
  const encryptor = new JSEncrypt()
  encryptor.setPublicKey(normalizePublicKey(publicKey))
  const encrypted = encryptor.encrypt(plainText)
  if (!encrypted) {
    throw new Error('RSA 加密失败')
  }
  return encrypted
}

/**
 * 使用 RSA 公钥加密明文
 * 默认 PKCS#1 v1.5（兼容 Java RSA/ECB/PKCS1Padding）
 */
export const encryptWithRsaPublicKey = async (
  plainText: string,
  publicKey: string,
  algorithm?: string,
): Promise<string> => {
  if (!plainText) {
    throw new Error('待加密内容不能为空')
  }

  if (isOaepAlgorithm(algorithm)) {
    return encryptWithOaep(plainText, publicKey)
  }

  return encryptWithPkcs1(plainText, publicKey)
}
