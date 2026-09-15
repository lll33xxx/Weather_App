import request from '@/api/weather'

// ========== 认证相关 API ==========

/**
 * 登录
 * @param {Object} data
 * @param {string} data.username
 * @param {string} data.password
 * @returns {Promise<Object>} {id, username, role}
 */
export const login = (data) => {
  return request.post('/auth/login', data)
}

/**
 * 注册（仅普通用户）
 * @param {Object} data
 * @param {string} data.username
 * @param {string} data.password
 * @returns {Promise<Object>} {id, username, role}
 */
export const register = (data) => {
  return request.post('/auth/register', data)
}

/**
 * 登出
 */
export const logout = () => {
  return request.post('/auth/logout')
}

/**
 * 获取当前登录用户信息
 * @returns {Promise<Object>} {id, username, role}
 */
export const getCurrentUser = () => {
  return request.get('/auth/me')
}

export const updateUsername = (username) => {
  return request.post('/auth/profile/username', { username })
}

export const changePassword = (oldPassword, newPassword) => {
  return request.post('/auth/profile/password', { oldPassword, newPassword })
}

export const uploadAvatar = (file) => {
  const fd = new FormData()
  fd.append('file', file)
  return request.post('/auth/profile/avatar', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
}
