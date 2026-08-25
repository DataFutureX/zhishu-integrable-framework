import { get, post } from '@/utils/request'

/** 滑动验证码响应 */
export interface CaptchaResponse {
  captchaId: string
  backgroundImage: string
  sliderImage: string
  sliderY: number
  /** 缺口横坐标（若后端返回则优先用于前端随机绘图） */
  slideX?: number
  /** 前端生成滑块图时的 Y 轴偏移，用于与背景缺口对齐 */
  sliderImageOffsetY?: number
}

/** 滑动验证码校验请求 */
export interface CaptchaVerifyRequest {
  captchaId: string
  slideX: number
}

/** 滑动验证码校验响应 */
export interface CaptchaVerifyResponse {
  captchaToken: string
}

/** RSA 公钥响应 */
export interface PublicKeyResponse {
  keyId: string
  publicKey: string
  algorithm?: string
  expireSeconds?: number
}

/**
 * 获取滑动验证码
 * GET /api/v1/auth/captcha
 */
export const getCaptchaApi = () => {
  return get<CaptchaResponse>('/auth/captcha')
}

/**
 * 校验滑动验证码
 * POST /api/v1/auth/captcha/verify
 */
export const verifyCaptchaApi = (data: CaptchaVerifyRequest) => {
  return post<CaptchaVerifyResponse>('/auth/captcha/verify', data)
}

/**
 * 获取登录 RSA 公钥
 * GET /api/v1/auth/public-key
 */
export const getPublicKeyApi = () => {
  return get<PublicKeyResponse>('/auth/public-key')
}

/**
 * 退出登录（服务端吊销 Token）
 * POST /api/v1/auth/logout
 */
export const logoutApi = () => {
  return post('/auth/logout')
}

/** SSO 换票请求 */
export interface SsoExchangeRequest {
  ticket: string
  redirect?: string
}

/** SSO 换票响应 */
export interface SsoExchangeResponse {
  token: string
  expiration: number
  redirect: string
}

/**
 * 伙伴 Ticket 换取云起业务 JWT
 * POST /api/v1/auth/sso/exchange
 */
export const exchangeSsoTicketApi = (data: SsoExchangeRequest) => {
  // 换票失败的 401 表示票据无效，不是会话过期，勿触发全局登出
  return post<SsoExchangeResponse>('/auth/sso/exchange', data, { skipErrorMessage: true })
}
