import axios from 'axios'

/**
 * 地图 GeoJSON 数据 API
 * 数据来源：阿里云 DataV.GeoAtlas（公开地理数据服务）
 *
 * URL 规则：
 *   中国(含各省)：  https://geo.datav.aliyun.com/areas_v3/bound/100000_full.json
 *   省份(含各市)：  https://geo.datav.aliyun.com/areas_v3/bound/{adcode}_full.json
 *   城市(含各区县)：https://geo.datav.aliyun.com/areas_v3/bound/{adcode}_full.json
 *   城市(仅边界)：  https://geo.datav.aliyun.com/areas_v3/bound/{adcode}.json
 */

const GEO_BASE = 'https://geo.datav.aliyun.com/areas_v3/bound'

// 内存缓存：避免重复请求同一区域地图
const geoCache = new Map()

const geoRequest = axios.create({
  timeout: 20000
})

/**
 * 获取指定 adcode 的 GeoJSON 地图数据
 * @param {string|number} adcode - 行政区划代码
 * @param {boolean} full - 是否包含下级行政区（true: _full.json, false: 仅边界）
 * @returns {Promise<Object>} GeoJSON 对象
 */
export const getGeoJson = async (adcode, full = true) => {
  const key = `${adcode}_${full ? 'full' : 'simple'}`
  if (geoCache.has(key)) {
    return geoCache.get(key)
  }
  const url = `${GEO_BASE}/${adcode}${full ? '_full' : ''}.json`
  try {
    const res = await geoRequest.get(url)
    geoCache.set(key, res.data)
    return res.data
  } catch (e) {
    console.error(`获取地图数据失败 [${adcode}]:`, e)
    throw e
  }
}

/** 中国地图 adcode */
export const CHINA_ADCODE = '100000'

/**
 * 从 GeoJSON features 中提取区域列表 [{name, adcode, center}]
 * @param {Object} geoJson
 * @returns {Array<{name: string, adcode: string, center: [number, number]}>}
 */
export const extractRegions = (geoJson) => {
  if (!geoJson?.features) return []
  return geoJson.features.map((f) => ({
    name: f.properties?.name || '',
    adcode: String(f.properties?.adcode || ''),
    center: f.properties?.center || f.properties?.centroid || null
  }))
}

/**
 * 将城市排名数据按省份聚合（取该省所有城市指标值的平均值）
 * 用于中国地图省级着色
 * 计算逻辑：该省所有城市 metricValue 之和 ÷ 该省城市数量（城市名去重，避免重复数据）
 * @param {Array} rankingData - [{city, province, metricValue, ...}]
 * @returns {Map<string, number>} provinceName -> avgMetricValue
 */
export const aggregateByProvince = (rankingData) => {
  // 按省份存储城市数据：provinceName -> Map<cityName, metricValue>
  const provinceCityMap = new Map()
  rankingData.forEach((item) => {
    const province = item.province
    const city = item.city
    const metricValue = item.metricValue
    if (!province || !city || metricValue == null) return
    const cleanProvince = cleanProvinceName(province)
    if (!provinceCityMap.has(cleanProvince)) {
      provinceCityMap.set(cleanProvince, new Map())
    }
    // 同一城市只保留一个值（避免重复数据），如果有多个取平均值
    const cityMap = provinceCityMap.get(cleanProvince)
    if (cityMap.has(city)) {
      // 已有该城市数据，取平均值
      const oldVal = cityMap.get(city)
      cityMap.set(city, (oldVal + metricValue) / 2)
    } else {
      cityMap.set(city, metricValue)
    }
  })
  // 计算每个省份的平均值
  const result = new Map()
  provinceCityMap.forEach((cityMap, provinceName) => {
    const cities = Array.from(cityMap.values())
    if (cities.length === 0) return
    const sum = cities.reduce((s, v) => s + v, 0)
    const avg = sum / cities.length
    result.set(provinceName, Math.round(avg * 100) / 100)
  })
  return result
}

/**
 * 清理省份名称后缀，匹配地图数据中的 name
 * 例如："福建省" → "福建"，"广西壮族自治区" → "广西"
 */
export const cleanProvinceName = (name) => {
  if (!name) return name
  return name
    .replace(/(省|市|自治区|特别行政区)$/, '')
    .replace(/壮族|回族|维吾尔族/g, '')
    .replace(/族$/, '')
}

/**
 * 清理城市名称后缀，匹配地图数据中的 name
 * 例如："北京市" → "北京"，"上海市" → "上海"
 */
export const cleanCityName = (name) => {
  if (!name) return name
  return name.replace(/(市|自治州|地区|盟)$/, '')
}
