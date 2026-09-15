import axios from 'axios'
import router from '@/router'
import { useAuthStore } from '@/stores/auth'

// 创建 axios 实例
const request = axios.create({
  baseURL: '/api',
  timeout: 15000,
  // Session 认证需要携带 Cookie
  withCredentials: true
})

// 统一处理未登录：清除状态 + 跳转登录页（提示由调用方统一弹出）
function handleUnauthorized() {
  const authStore = useAuthStore()
  authStore.user = null
  localStorage.removeItem('nl_auth_user')
  if (router.currentRoute.value.path !== '/login') {
    router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
  }
}

// 响应拦截器：统一处理 Result 包装 + 401 未授权 + 友好中文错误
request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code === 200) {
      return res.data
    }
    // 业务层 401（如 /api/auth/me 返回未登录）
    if (res.code === 401) {
      handleUnauthorized()
    }
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  (error) => {
    // HTTP 层 401（拦截器返回的未登录）
    if (error.response?.status === 401) {
      handleUnauthorized()
      return Promise.reject(new Error('登录已过期，请重新登录'))
    }
    // 其他 HTTP 错误，提取友好中文提示
    let msg = '请求失败，请稍后重试'
    if (error.code === 'ECONNABORTED' || error.message?.includes('timeout')) {
      msg = '请求超时，请检查网络后重试'
    } else if (error.response?.status >= 500) {
      msg = '服务器开小差了，请稍后重试'
    } else if (error.response?.status === 403) {
      msg = '没有权限访问该资源'
    } else if (error.response?.data?.message) {
      const raw = error.response.data.message
      // 数据库字段过长
      if (raw.includes('Data too long') || raw.includes('Data truncation')) {
        msg = '提交内容过长，请缩短后重试'
      } else if (raw.includes('Duplicate entry') || raw.includes('DuplicateKey')) {
        msg = '数据重复，请检查后重试'
      } else {
        msg = raw
      }
    }
    console.error('Request Error:', error)
    return Promise.reject(new Error(msg))
  }
)

export default request

// ========== 气象数据 API ==========

/**
 * 获取所有指标元信息
 * @returns {Promise<Array>} [{code, name, displayName, unit, daysName}]
 */
export const getMetrics = () => {
  return request.get('/weather/metrics')
}

/**
 * 获取指定指标的可用年份列表
 * @param {string} metric - 指标编码 rain/wind/temp/sun/humidity
 * @returns {Promise<Array<number>>}
 */
export const getYears = (metric) => {
  return request.get('/weather/years', { params: { metric } })
}

/**
 * 获取指定指标的可用城市列表
 * @param {string} metric
 * @returns {Promise<Array<string>>}
 */
export const getCities = (metric) => {
  return request.get('/weather/cities', { params: { metric } })
}

/**
 * 查询城市排名
 * @param {Object} params
 * @param {string} params.metric - 指标编码
 * @param {number} params.year - 年份
 * @returns {Promise<Array>} [{year, rankNum, city, province, metricValue, days}]
 */
export const getRanking = (params) => {
  return request.get('/weather/ranking', { params: { metric: params.metric, year: params.year, limit: params.limit || 20 } })
}

/**
 * 查询城市历年趋势
 * @param {Object} params
 * @param {string} params.metric - 指标编码
 * @param {string} params.city - 城市名
 * @returns {Promise<Array>} [{city, year, metricValue, days, trendLabel}]
 */
export const getTrend = (params) => {
  return request.get('/weather/trend', { params: { metric: params.metric, city: params.city } })
}

// ========== 实时天气（和风天气） ==========

/**
 * 获取实时天气
 * @param {number} lon 经度
 * @param {number} lat 纬度
 */
export const getRealtime = (lon, lat) => {
  return request.get('/weather/now', { params: { lon, lat } })
}

/**
 * 获取 24 小时预报
 */
export const getHourly = (lon, lat) => {
  return request.get('/weather/hourly', { params: { lon, lat } })
}

/**
 * 获取 7 天预报
 */
export const getDaily = (lon, lat) => {
  return request.get('/weather/daily', { params: { lon, lat } })
}

/**
 * 获取附加信息聚合：预警 + 空气质量 + 生活指数
 */
export const getWeatherExtras = (lon, lat) => {
  return request.get('/weather/extras', { params: { lon, lat } })
}

/**
 * 获取主要城市实时天气概览（首页地图标注用）
 */
export const getCitiesOverview = () => {
  return request.get('/weather/cities-overview')
}
