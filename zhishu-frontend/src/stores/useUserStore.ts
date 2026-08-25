import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  loginApi,
  getCurrentUserApi,
  type UserEntity,
  type UserVO,
} from '@/api/user'
import { getPublicKeyApi, logoutApi } from '@/api/auth'
import { getCurrentUserPermissionsApi } from '@/api/menu'
import { encryptWithRsaPublicKey } from '@/utils/rsaEncrypt'
import { matchPermissions } from '@/utils/permission'
import { useLayoutStore } from '@/stores/useLayoutStore'
import { showErrorMessage, showSuccessMessage } from '@/utils/uiMessage'

interface UserInfo {
  id: string | number
  username: string
  realName: string
  email: string
  phone: string
  role?: string
  roleId?: string | number
  roleName?: string
  status: number
}

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  const tokenExpiration = ref<number>(Number(localStorage.getItem('tokenExpiration') || 0))
  const userInfo = ref<UserInfo | null>(null)
  const permissions = ref<string[]>([])

  const isLoggedIn = computed(() => !!token.value)
  const userRole = computed(() => userInfo.value?.roleName || userInfo.value?.role || '')
  const userName = computed(() => userInfo.value?.realName || userInfo.value?.username || '')
  const isAdmin = computed(() => {
    const role = (userInfo.value?.role || '').toUpperCase()
    return role === 'ADMIN' || role === 'ROLE_ADMIN'
  })

  const hasPermission = (code: string | string[]) =>
    matchPermissions(permissions.value, code, { isAdmin: isAdmin.value })

  const hasAllPermissions = (codes: string[]) =>
    matchPermissions(permissions.value, codes, { requireAll: true, isAdmin: isAdmin.value })

  const applyPermissions = (codes: string[]) => {
    permissions.value = codes || []
    sessionStorage.setItem('permissions', JSON.stringify(permissions.value))
  }

  const fetchUserPermissions = async (options?: { silent?: boolean }) => {
    try {
      const codes = await getCurrentUserPermissionsApi(
        options?.silent ? { skipErrorMessage: true } : undefined,
      )
      applyPermissions(codes || [])
    } catch (error) {
      console.error('获取用户权限失败:', error)
      applyPermissions([])
    }
  }

  const setToken = (newToken: string, expiration?: number) => {
    token.value = newToken
    localStorage.setItem('token', newToken)
    if (expiration && expiration > 0) {
      tokenExpiration.value = expiration
      localStorage.setItem('tokenExpiration', String(expiration))
    }
  }

  const setUserInfo = (info: UserInfo | UserEntity | UserVO) => {
    userInfo.value = {
      id: info.id,
      username: info.username,
      realName: info.realName,
      email: info.email,
      phone: info.phone,
      role: info.role,
      roleId: 'roleId' in info ? info.roleId : undefined,
      roleName: 'roleName' in info ? info.roleName : undefined,
      status: info.status,
    }
    sessionStorage.setItem('userInfo', JSON.stringify(userInfo.value))
  }

  const login = async (username: string, password: string, captchaToken: string) => {
    try {
      const publicKeyData = await getPublicKeyApi()
      if (!publicKeyData?.keyId || !publicKeyData.publicKey) {
        const message = '获取登录公钥失败'
        showErrorMessage(message)
        throw new Error(message)
      }

      const [encryptedUsername, encryptedPassword] = await Promise.all([
        encryptWithRsaPublicKey(username, publicKeyData.publicKey, publicKeyData.algorithm),
        encryptWithRsaPublicKey(password, publicKeyData.publicKey, publicKeyData.algorithm),
      ])

      const loginData = await loginApi({
        username: encryptedUsername,
        password: encryptedPassword,
        captchaToken,
        keyId: publicKeyData.keyId,
      })

      if (!loginData?.token) {
        const message = '登录响应数据异常'
        showErrorMessage(message)
        throw new Error(message)
      }

      setToken(loginData.token, loginData.expiration)
      await fetchUserInfo()
      useLayoutStore().applyLoginDefaultLayout()
      return Promise.resolve()
    } catch (error) {
      return Promise.reject(error)
    }
  }

  const clearLocalSession = () => {
    token.value = ''
    tokenExpiration.value = 0
    userInfo.value = null
    permissions.value = []
    localStorage.removeItem('token')
    localStorage.removeItem('tokenExpiration')
    sessionStorage.removeItem('userInfo')
    sessionStorage.removeItem('permissions')
  }

  /**
   * 退出登录
   * @param options.silent 为 true 时不弹成功提示（用于 401 自动退出）
   * @param options.notifyServer 为 false 时跳过服务端 logout
   */
  const logout = async (options?: { silent?: boolean; notifyServer?: boolean }) => {
    const notifyServer = options?.notifyServer !== false
    if (notifyServer && token.value) {
      try {
        await logoutApi()
      } catch (error) {
        console.warn('服务端退出登录失败，继续清理本地会话', error)
      }
    }

    clearLocalSession()
    if (!options?.silent) {
      showSuccessMessage('已退出登录')
    }
  }

  const fetchUserInfo = async () => {
    if (!token.value) {
      return Promise.reject(new Error('未登录'))
    }

    try {
      const userData = await getCurrentUserApi()
      setUserInfo(userData)
      await fetchUserPermissions()
      return Promise.resolve()
    } catch (error) {
      console.error('获取用户信息失败:', error)
      return Promise.reject(error)
    }
  }

  const initUserInfo = () => {
    if (!userInfo.value && token.value) {
      const cached = sessionStorage.getItem('userInfo')
      if (cached) {
        try {
          userInfo.value = JSON.parse(cached)
        } catch (e) {
          console.error('解析缓存用户信息失败:', e)
        }
      }
    }
    if (!permissions.value.length && token.value) {
      const cachedCodes = sessionStorage.getItem('permissions')
      if (cachedCodes) {
        try {
          permissions.value = JSON.parse(cachedCodes)
        } catch (e) {
          console.error('解析缓存权限失败:', e)
        }
      }
    }
  }

  return {
    token,
    tokenExpiration,
    userInfo,
    permissions,
    isLoggedIn,
    userRole,
    userName,
    isAdmin,
    hasPermission,
    hasAllPermissions,
    setToken,
    setUserInfo,
    login,
    logout,
    clearLocalSession,
    fetchUserInfo,
    fetchUserPermissions,
    initUserInfo,
  }
})
