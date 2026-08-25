import { get, post, put, del } from '@/utils/request'

// ==================== 认证相关类型 ====================

// 登录请求参数（username/password 为 RSA 加密后的密文）
export interface LoginRequest {
  username: string
  password: string
  captchaToken: string
  keyId: string
}

// 登录响应数据
export interface LoginResponse {
  token: string
  expiration: number
}

// ==================== 用户实体类型 ====================

// 用户实体（完整，包含密码字段 - 仅用于后端返回）
// 注意：id使用string类型以支持后端int64大整数，避免JavaScript精度丢失
export interface UserEntity {
  id: string | number
  username: string
  realName: string
  email: string
  phone: string
  password: string
  role: string
  status: number
  createTime: string
  updateTime: string
}

// 用户 VO（用于列表展示，不包含密码）
// 注意：id使用string|number以兼容不同格式的API响应
export interface UserVO {
  id: string | number
  username: string
  realName: string
  email: string
  phone: string
  role?: string
  roleId?: string | number
  roleName?: string
  status: number
  createTime: string
  updateTime: string
}

// ==================== 分页查询相关类型 ====================

// 用户查询参数（与API文档完全匹配）
export interface UserQueryDTO {
  username?: string
  realName?: string
  phone?: string
  role?: string
  roleId?: string | number
  status?: number
  pageNum?: number
  pageSize?: number
}

// 分页结果
export interface PageResult<T> {
  current: number
  size: number
  total: number
  pages: number
  records: T[]
}

// ==================== 创建/更新相关类型 ====================

// 创建用户请求（与API文档完全匹配）
// 必填字段: username, realName, password, roleId
// 可选字段: email, phone, status(默认0)
export interface UserCreateDTO {
  username: string
  realName: string
  email?: string
  phone?: string
  password: string
  roleId: string | number
  status?: number
}

// 更新用户请求（与API文档完全匹配）
// 必填字段: id（支持string或number以兼容int64）
// 可选字段: realName, email, phone, password, roleId, status
export interface UserUpdateDTO {
  id: string | number
  realName?: string
  email?: string
  phone?: string
  password?: string
  roleId?: string | number
  status?: number
}

// 用户角色信息
export interface UserRoleVO {
  userId: string | number
  roleId: string | number
  roleCode: string
  roleName: string
}

// 用户角色分配请求
export interface UserRoleAssignDTO {
  roleId: string | number
}

/** 修改当前用户密码 */
export interface UserPasswordChangeDTO {
  oldPassword: string
  newPassword: string
}

/** 当前用户更新个人资料 */
export interface UserProfileUpdateDTO {
  realName?: string
  email?: string
  phone?: string
}

/** 管理员重置用户密码 */
export interface UserPasswordResetDTO {
  newPassword: string
}

/** 启用/禁用用户 */
export interface UserStatusUpdateDTO {
  status: number
}

// ==================== API 接口 ====================

/**
 * 用户登录
 * POST /api/v1/auth/login
 * @param data 登录请求参数
 * @returns JWT Token 和过期时间
 */
export const loginApi = (data: LoginRequest) => {
  return post<LoginResponse>('/auth/login', data)
}

/**
 * 获取当前登录用户信息
 * GET /api/v1/users/me
 */
export const getCurrentUserApi = () => {
  return get<UserVO>('/users/me')
}

/**
 * 更新当前用户个人资料
 * PUT /api/v1/users/me
 */
export const updateCurrentProfileApi = (data: UserProfileUpdateDTO) => {
  return put<UserVO>('/users/me', data)
}

/**
 * 获取当前用户信息（兼容旧调用）
 * GET /api/v1/users/{id}
 */
export const getUserInfoApi = (id: string | number) => {
  return get<UserEntity>(`/users/${id}`)
}

/**
 * 根据用户名查询用户（需管理权限，个人中心请用 getCurrentUserApi）
 * GET /api/v1/users/username/{username}
 */
export const getUserByUsernameApi = (username: string) => {
  return get<UserVO>(`/users/username/${username}`)
}

/**
 * 分页查询用户列表
 * GET /api/v1/users/page
 * @param params 查询参数
 * @returns 分页用户列表
 */
export const getUserPageApi = (params: UserQueryDTO) => {
  return get<PageResult<UserVO>>('/users/page', { params })
}

/**
 * 查询用户详情
 * GET /api/v1/users/{id}
 * @param id 用户ID（支持string或number类型）
 * @returns 用户详细信息
 */
export const getUserDetailApi = (id: string | number) => {
  return get<UserEntity>(`/users/${id}`)
}

/**
 * 创建用户
 * POST /api/v1/users
 * @param data 用户创建数据
 * @returns 创建的用户信息
 */
export const createUserApi = (data: UserCreateDTO) => {
  return post<UserEntity>('/users', data)
}

/**
 * 更新用户
 * PUT /api/v1/users
 * @param data 用户更新数据
 * @returns 更新后的用户信息
 */
export const updateUserApi = (data: UserUpdateDTO) => {
  return put<UserEntity>('/users', data)
}

/**
 * 删除用户
 * DELETE /api/v1/users/{id}
 * @param id 用户ID（支持string或number类型）
 * @returns 删除结果
 */
export const deleteUserApi = (id: string | number) => {
  return del(`/users/${id}`)
}

/**
 * 查询用户已分配角色
 * GET /api/v1/users/{id}/role
 */
export const getUserRoleApi = (id: string | number) => {
  return get<UserRoleVO>(`/users/${id}/role`)
}

/**
 * 为用户分配角色
 * PUT /api/v1/users/{id}/role
 */
export const assignUserRoleApi = (id: string | number, data: UserRoleAssignDTO) => {
  return put(`/users/${id}/role`, data)
}

/**
 * 启用/禁用用户
 * PUT /api/v1/users/{id}/status
 */
export const updateUserStatusApi = (id: string | number, data: UserStatusUpdateDTO) => {
  return put(`/users/${id}/status`, data)
}

/**
 * 管理员重置用户密码
 * PUT /api/v1/users/{id}/password/reset
 */
export const resetUserPasswordApi = (id: string | number, data: UserPasswordResetDTO) => {
  return put(`/users/${id}/password/reset`, data)
}

/**
 * 修改当前用户密码
 * PUT /api/v1/users/me/password
 */
export const changePasswordApi = (data: UserPasswordChangeDTO) => {
  return put('/users/me/password', data)
}
