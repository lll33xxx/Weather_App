import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as apiLogin, register as apiRegister, logout as apiLogout, getCurrentUser } from '@/api/auth'

// localStorage 持久化 key
const STORAGE_KEY = 'nl_auth_user'

// 从 localStorage 读取保存的用户信息
const loadSavedUser = () => {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) return JSON.parse(raw)
  } catch (e) {
    console.warn('读取登录状态失败:', e)
  }
  return null
}

// 保存用户信息到 localStorage
const saveUser = (user) => {
  try {
    if (user) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(user))
    } else {
      localStorage.removeItem(STORAGE_KEY)
    }
  } catch (e) {
    console.warn('保存登录状态失败:', e)
  }
}

/**
 * 认证状态管理
 * 管理当前登录用户信息、登录状态、角色判断
 * 用户信息持久化到 localStorage，刷新页面后保持登录态
 */
export const useAuthStore = defineStore('auth', () => {
  // ========== State ==========
  /** 当前登录用户信息 {id, username, role}，未登录为 null */
  const user = ref(loadSavedUser())

  // ========== Getters ==========
  /** 是否已登录 */
  const isLoggedIn = computed(() => user.value !== null)

  /** 是否为管理员 */
  const isAdmin = computed(() => user.value?.role === 'admin')

  /** 当前用户名 */
  const username = computed(() => user.value?.username || '')

  // ========== Actions ==========

  /**
   * 登录
   * @param {string} username
   * @param {string} password
   * @returns {Promise<Object>} 登录成功的用户信息
   */
  const login = async (username, password) => {
    const res = await apiLogin({ username, password })
    user.value = res
    saveUser(res)
    return res
  }

  /**
   * 注册（仅普通用户）
   * @param {string} username
   * @param {string} password
   * @returns {Promise<Object>} 注册成功的用户信息
   */
  const register = async (username, password) => {
    const res = await apiRegister({ username, password })
    return res
  }

  /**
   * 登出
   */
  const logout = async () => {
    try {
      await apiLogout()
    } catch (e) {
      console.warn('登出请求失败:', e)
    } finally {
      user.value = null
      saveUser(null)
    }
  }

  /**
   * 从后端拉取当前用户信息（用于页面刷新后校验登录态）
   * 如果后端返回未登录，则清除本地状态
   */
  const fetchCurrentUser = async () => {
    try {
      const res = await getCurrentUser()
      user.value = res
      saveUser(res)
      return res
    } catch (e) {
      // 后端返回未登录，清除本地状态
      user.value = null
      saveUser(null)
      return null
    }
  }

  return {
    // state
    user,
    // getters
    isLoggedIn,
    isAdmin,
    username,
    // actions
    login,
    register,
    logout,
    fetchCurrentUser
  }
})
