import request from '@/api/weather'

// ========== 用户管理 API ==========

/**
 * 分页查询用户列表
 * @param {Object} params
 * @param {number} params.pageNum
 * @param {number} params.pageSize
 * @param {string} [params.keyword]
 */
export const getUserList = (params) => {
  return request.get('/admin/users', { params })
}

/**
 * 修改用户角色
 * @param {number} id
 * @param {string} role - 'user' | 'admin'
 */
export const updateUserRole = (id, role) => {
  return request.put(`/admin/users/${id}/role`, { role })
}

/**
 * 删除用户
 * @param {number} id
 */
export const deleteUser = (id) => {
  return request.delete(`/admin/users/${id}`)
}

// ========== 天气数据管理 API ==========

/**
 * 分页查询天气数据
 * @param {Object} params
 * @param {string} params.metric - 指标编码 rain/wind/temp/sun/humidity
 * @param {number} [params.year]
 * @param {string} [params.province]
 * @param {string} [params.city]
 * @param {number} params.pageNum
 * @param {number} params.pageSize
 */
export const getWeatherDataList = (params) => {
  return request.get('/admin/weather-data', { params })
}

/**
 * 获取指定指标的可用年份
 * @param {string} metric
 */
export const getWeatherYears = (metric) => {
  return request.get('/admin/weather-data/years', { params: { metric } })
}

/**
 * 获取指定指标的可用省份
 * @param {string} metric
 */
export const getWeatherProvinces = (metric) => {
  return request.get('/admin/weather-data/provinces', { params: { metric } })
}

/**
 * 查询指定城市、年份、指标的逐日数据（从Hive查询）
 * @param {Object} params
 * @param {string} params.metric - 指标编码
 * @param {string} params.city - 城市名
 * @param {number} params.year - 年份
 */
export const getWeatherDaily = (params) => {
  return request.get('/admin/weather-daily', { params })
}

// ========== 系统模块管理 API ==========

/**
 * 获取所有模块（管理员用）
 */
export const getModuleList = () => {
  return request.get('/admin/modules')
}

/**
 * 获取启用的模块（首页展示用）
 */
export const getActiveModules = () => {
  return request.get('/modules/active')
}

/**
 * 新增模块
 * @param {Object} data
 */
export const createModule = (data) => {
  return request.post('/admin/modules', data)
}

/**
 * 更新模块
 * @param {number} id
 * @param {Object} data
 */
export const updateModule = (id, data) => {
  return request.put(`/admin/modules/${id}`, data)
}

/**
 * 启用/禁用模块
 * @param {number} id
 * @param {number} status - 1启用 0禁用
 */
export const updateModuleStatus = (id, status) => {
  return request.put(`/admin/modules/${id}/status`, { status })
}

/**
 * 删除模块
 * @param {number} id
 */
export const deleteModule = (id) => {
  return request.delete(`/admin/modules/${id}`)
}
