import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'
import {
  getMetrics,
  getYears,
  getCities,
  getRanking,
  getTrend
} from '@/api/weather'

// localStorage 持久化 key
const STORAGE_KEY = 'weather_dashboard_state'
// 默认年份（数据范围 2000-2020，默认取最新一年）
const DEFAULT_YEAR = 2020

// 从 localStorage 读取保存的用户选择
const loadSavedState = () => {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) return JSON.parse(raw)
  } catch (e) {
    console.warn('读取本地存储失败:', e)
  }
  return {}
}

// 保存用户选择到 localStorage
const saveState = (state) => {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state))
  } catch (e) {
    console.warn('保存本地存储失败:', e)
  }
}

/**
 * 气象数据全局状态管理
 * 管理当前选中的指标、年份、城市、排名数量，以及对应的数据
 * 用户选择（指标/年份/城市/排名数）自动持久化到 localStorage
 */
export const useWeatherStore = defineStore('weather', () => {
  // 读取本地保存的状态作为初始值
  const saved = loadSavedState()

  // ========== 基础状态 ==========
  /** 所有指标元信息 */
  const metrics = ref([])
  /** 当前选中的指标编码（默认降水） */
  const currentMetric = ref(saved.currentMetric || 'rain')
  /** 当前指标的可用年份列表 */
  const years = ref([])
  /** 当前选中的年份（默认2020） */
  const currentYear = ref(saved.currentYear || DEFAULT_YEAR)
  /** 当前指标的可用城市列表 */
  const cities = ref([])
  /** 当前选中的城市 */
  const currentCity = ref(saved.currentCity || '')
  /** 排名数量（默认20） */
  const limit = ref(saved.limit || 20)
  /** 当前选中的省份（用于地图省份筛选，null表示全部） */
  const selectedProvince = ref(null)

  // ========== 数据状态 ==========
  /** 排名数据 */
  const rankingData = ref([])
  /** 趋势数据 */
  const trendData = ref([])
  /** 加载状态 */
  const loading = ref(false)
  /** 错误信息 */
  const error = ref('')

  // ========== 自动持久化：监听用户选择变化并保存到 localStorage ==========
  watch(
    [currentMetric, currentYear, currentCity, limit],
    ([metric, year, city, lim]) => {
      saveState({
        currentMetric: metric,
        currentYear: year,
        currentCity: city,
        limit: lim
      })
    },
    { deep: true }
  )

  // ========== 计算属性 ==========
  /** 当前指标的元信息 */
  const currentMetricMeta = computed(() => {
    return metrics.value.find((m) => m.code === currentMetric.value) || {}
  })

  /** 当前城市在当前年份的排名 */
  const currentCityRank = computed(() => {
    const item = rankingData.value.find((d) => d.city === currentCity.value)
    return item ? item.rankNum : null
  })

  /** 按选中省份筛选后的排名数据（null表示全部） */
  const filteredRankingData = computed(() => {
    if (!selectedProvince.value) return rankingData.value
    return rankingData.value.filter((d) => d.province === selectedProvince.value)
  })

  /** 获取指定省份在当前年份的平均指标值（用于地图省份tooltip） */
  const getProvinceAvg = (provinceName) => {
    if (!provinceName || !rankingData.value.length) return null
    const provinceCities = rankingData.value.filter((d) => d.province === provinceName)
    if (provinceCities.length === 0) return null
    const sum = provinceCities.reduce((acc, d) => acc + (d.metricValue || 0), 0)
    return {
      avg: Math.round((sum / provinceCities.length) * 100) / 100,
      count: provinceCities.length,
      cities: provinceCities
    }
  }

  // ========== Actions ==========

  /** 加载所有指标元信息 */
  const loadMetrics = async () => {
    try {
      metrics.value = await getMetrics()
    } catch (e) {
      console.error('加载指标列表失败:', e)
    }
  }

  /** 加载当前指标的可用年份列表 */
  const loadYears = async (preferredYear = null) => {
    try {
      years.value = await getYears(currentMetric.value)
      // 倒序（最新年份在前）
      years.value.sort((a, b) => b - a)
      // 优先使用传入的偏好年份，其次用本地保存的年份，最后默认选最新一年
      const targetYear = preferredYear ?? currentYear.value
      if (targetYear && years.value.includes(targetYear)) {
        currentYear.value = targetYear
      } else if (years.value.length > 0) {
        currentYear.value = years.value[0]
      } else {
        currentYear.value = null
      }
    } catch (e) {
      console.error('加载年份列表失败:', e)
      years.value = []
      // API 失败时使用默认年份，确保下拉框不为空
      currentYear.value = DEFAULT_YEAR
    }
  }

  /** 加载当前指标的可用城市列表 */
  const loadCities = async (preferredCity = '') => {
    try {
      cities.value = await getCities(currentMetric.value)
      // 优先使用传入的偏好城市，其次用本地保存的城市；不再默认选中第一个城市
      // （避免首次进入时默认选中一个排名之外的城市，造成用户困惑）
      const targetCity = preferredCity || currentCity.value
      if (targetCity && cities.value.includes(targetCity)) {
        currentCity.value = targetCity
      } else {
        currentCity.value = ''
      }
    } catch (e) {
      console.error('加载城市列表失败:', e)
      cities.value = []
      currentCity.value = ''
    }
  }

  /** 切换指标（保留年份和城市选择） */
  const switchMetric = async (code) => {
    if (code === currentMetric.value) return
    const prevYear = currentYear.value
    const prevCity = currentCity.value
    currentMetric.value = code
    selectedProvince.value = null // 切换指标时重置省份筛选
    await Promise.all([loadYears(prevYear), loadCities(prevCity)])
    await refreshAll()
  }

  /**
   * 切换选中省份（点击地图省份时调用）
   * 再次点击同一省份或传入 null 则取消筛选
   */
  const toggleProvince = (provinceName) => {
    if (!provinceName || selectedProvince.value === provinceName) {
      selectedProvince.value = null
    } else {
      selectedProvince.value = provinceName
    }
  }

  /** 加载排名数据 */
  const loadRanking = async () => {
    if (!currentYear.value) {
      rankingData.value = []
      return
    }
    try {
      rankingData.value = await getRanking({
        metric: currentMetric.value,
        year: currentYear.value,
        limit: limit.value
      })
    } catch (e) {
      console.error('加载排名数据失败:', e)
      rankingData.value = []
      error.value = e.message
    }
  }

  /** 加载趋势数据 */
  const loadTrend = async () => {
    if (!currentCity.value) {
      trendData.value = []
      return
    }
    try {
      trendData.value = await getTrend({
        metric: currentMetric.value,
        city: currentCity.value
      })
    } catch (e) {
      console.error('加载趋势数据失败:', e)
      trendData.value = []
      error.value = e.message
    }
  }

  /** 刷新全部数据（排名 + 趋势并行加载） */
  const refreshAll = async () => {
    loading.value = true
    error.value = ''
    try {
      await Promise.all([loadRanking(), loadTrend()])
    } finally {
      loading.value = false
    }
  }

  /** 仅刷新排名数据（年份/排名数变化时使用，趋势数据不受影响，避免趋势图无辜重绘） */
  const refreshRanking = async () => {
    loading.value = true
    error.value = ''
    try {
      await loadRanking()
    } finally {
      loading.value = false
    }
  }

  /**
   * 初始化：加载指标、年份、城市并刷新数据
   * 从 localStorage 恢复用户选择（指标/年份/城市/排名数），
   * 确保刷新页面后数据不丢失，初次进入时有明确默认值
   */
  const init = async () => {
    await loadMetrics()
    // loadYears / loadCities 内部会自动使用 currentYear / currentCity 的当前值
    // （即从 localStorage 恢复的值）作为偏好，不存在则回退到默认
    await Promise.all([loadYears(), loadCities()])
    await refreshAll()
  }

  return {
    // state
    metrics,
    currentMetric,
    years,
    currentYear,
    cities,
    currentCity,
    limit,
    selectedProvince,
    rankingData,
    trendData,
    loading,
    error,
    // getters
    currentMetricMeta,
    currentCityRank,
    filteredRankingData,
    getProvinceAvg,
    // actions
    loadMetrics,
    loadYears,
    loadCities,
    switchMetric,
    toggleProvince,
    loadRanking,
    loadTrend,
    refreshAll,
    refreshRanking,
    init
  }
})
