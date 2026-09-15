<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts'
import { useWeatherStore } from '@/stores/weather'
import {
  getGeoJson,
  CHINA_ADCODE,
  extractRegions,
  aggregateByProvince,
  cleanProvinceName,
  cleanCityName
} from '@/api/geo'
import { getTrend, getRanking } from '@/api/weather'

const weatherStore = useWeatherStore()

const MUNICIPALITIES = ['北京市', '上海市', '天津市', '重庆市']
const isMunicipality = (name) => {
  if (!name) return false
  return MUNICIPALITIES.some((m) => name.includes(m.replace('市', '')))
}

// ========== 地图层级状态 ==========
const currentLevel = ref(0)
const currentAdcode = ref(CHINA_ADCODE)
const currentName = ref('中国')
const breadcrumb = ref([{ name: '中国', adcode: CHINA_ADCODE, level: 0 }])

// ========== 选择器状态 ==========
const selectedMetric = ref('rain')
const selectedYear = ref(null)

// ========== 数据状态 ==========
const rankingData = ref([])
const cityTrendData = ref([])
const loading = ref(false)
const mapLoading = ref(false)
const errorMsg = ref('')

// ========== ECharts 实例 ==========
const mapChartRef = ref(null)
const trendChartRef = ref(null)
let mapChart = null
let trendChart = null
let resizeHandler = null

const registeredMapName = ref('')

// ========== 计算属性 ==========
const metricMeta = computed(() => {
  return weatherStore.metrics.find((m) => m.code === selectedMetric.value) || {}
})

const metricColorSchemes = {
  rain: {
    visualMap: ['#bfdbfe', '#93c5fd', '#3b82f6', '#1d4ed8', '#1e3a8a'],
    emphasis: '#3b82f6',
    select: '#1d4ed8'
  },
  wind: {
    visualMap: ['#a7f3d0', '#6ee7b7', '#10b981', '#059669', '#064e3b'],
    emphasis: '#10b981',
    select: '#059669'
  },
  temp: {
    visualMap: ['#fecaca', '#fca5a5', '#ef4444', '#dc2626', '#7f1d1d'],
    emphasis: '#ef4444',
    select: '#dc2626'
  },
  sun: {
    visualMap: ['#fde68a', '#fcd34d', '#f59e0b', '#d97706', '#78350f'],
    emphasis: '#f59e0b',
    select: '#d97706'
  },
  humidity: {
    visualMap: ['#a5f3fc', '#67e8f9', '#06b6d4', '#0891b2', '#164e63'],
    emphasis: '#06b6d4',
    select: '#0891b2'
  }
}

const currentColorScheme = computed(() => {
  return metricColorSchemes[selectedMetric.value] || metricColorSchemes.rain
})

const provinceMapData = computed(() => {
  if (currentLevel.value !== 0) return []
  const agg = aggregateByProvince(rankingData.value)

  const nameValueMap = new Map()
  agg.forEach((value, name) => {
    const originalNames = new Set()
    rankingData.value.forEach((d) => {
      if (d.province && cleanProvinceName(d.province) === name) {
        originalNames.add(d.province)
      }
    })
    const allNames = new Set()
    originalNames.forEach((origName) => allNames.add(origName))
    allNames.add(name)
    const hasSuffix = /(省|市|自治区|特别行政区)$/.test(name)
    if (!hasSuffix) {
      allNames.add(name + '省')
      allNames.add(name + '市')
      allNames.add(name + '自治区')
      allNames.add(name + '特别行政区')
    }
    allNames.forEach((n) => {
      nameValueMap.set(n, value)
    })
  })
  const result = []
  nameValueMap.forEach((value, name) => {
    result.push({ name, value })
  })
  return result
})

const cityMapData = computed(() => {
  if (currentLevel.value === 1) {
    const currentProvinceClean = cleanProvinceName(currentName.value)
    const filtered = rankingData.value.filter((d) => {
      if (!d.province || !currentProvinceClean) return false
      const dClean = cleanProvinceName(d.province)
      return dClean === currentProvinceClean ||
        d.province.includes(currentName.value) ||
        currentName.value.includes(d.province)
    })
    return filtered.flatMap((d) => {
      const base = {
        value: d.metricValue,
        adcode: d.city,
        rankNum: d.rankNum,
        days: d.days,
        province: d.province
      }
      const names = new Set()
      names.add(d.city)
      const clean = cleanCityName(d.city)
      if (clean !== d.city) names.add(clean)
      if (!clean.endsWith('市')) names.add(clean + '市')
      return Array.from(names).map((name) => ({ name, ...base }))
    })
  } else if (currentLevel.value === 2) {
    const cleanName = cleanCityName(currentName.value)
    const item = rankingData.value.find((d) => cleanCityName(d.city) === cleanName)
    if (!item) return []
    const base = {
      value: item.metricValue,
      adcode: item.city,
      rankNum: item.rankNum,
      days: item.days,
      province: item.province
    }
    const names = new Set()
    names.add(currentName.value)
    names.add(cleanName)
    if (!cleanName.endsWith('市')) names.add(cleanName + '市')
    return Array.from(names).map((name) => ({ name, ...base }))
  }
  return []
})

const visualMapRange = computed(() => {
  const data = currentLevel.value === 0 ? provinceMapData.value : cityMapData.value
  const values = data.map((d) => d.value).filter((v) => v != null)
  if (values.length === 0) return { min: 0, max: 100 }
  return {
    min: Math.floor(Math.min(...values)),
    max: Math.ceil(Math.max(...values))
  }
})

const quantile = (sortedValues, p) => {
  const n = sortedValues.length
  if (n === 0) return 0
  const pos = p * (n - 1)
  const base = Math.floor(pos)
  const rest = pos - base
  if (sortedValues[base + 1] !== undefined) {
    return sortedValues[base] + rest * (sortedValues[base + 1] - sortedValues[base])
  }
  return sortedValues[base]
}

const roundSmart = (val) => {
  if (val >= 100) return Math.round(val)
  if (val >= 10) return Math.round(val * 10) / 10
  return Math.round(val * 100) / 100
}

const PIECE_COUNT = 10
const HIGH_VALUE_THRESHOLD = 0.8
const visualMapPieces = computed(() => {
  const data = currentLevel.value === 0 ? provinceMapData.value : cityMapData.value
  const values = data.map((d) => d.value).filter((v) => v != null)
  if (values.length === 0) return []

  let rawBoundaries = []
  if (values.length < PIECE_COUNT * 2) {
    const minVal = Math.min(...values)
    const maxVal = Math.max(...values)
    const step = (maxVal - minVal) / PIECE_COUNT
    rawBoundaries = Array.from({ length: PIECE_COUNT + 1 }, (_, i) => minVal + i * step)
  } else {
    const sorted = [...values].sort((a, b) => a - b)
    const lowPercentiles = [0, 0.16, 0.32, 0.48, 0.64, HIGH_VALUE_THRESHOLD]
    const highPercentiles = [HIGH_VALUE_THRESHOLD, 0.84, 0.88, 0.92, 0.96, 1]
    const allPercentiles = [...lowPercentiles, ...highPercentiles.slice(1)]
    rawBoundaries = allPercentiles.map((p) => quantile(sorted, p))
  }

  const pieces = []
  for (let i = 0; i < rawBoundaries.length - 1; i++) {
    const rawMin = rawBoundaries[i]
    const rawMax = rawBoundaries[i + 1]
    if (rawMax <= rawMin) continue

    const labelMin = roundSmart(rawMin)
    const labelMax = roundSmart(rawMax)
    const piece = {}

    if (i === 0) {
      piece.max = rawMax
      piece.label = `${labelMax} 以下`
    } else if (i === rawBoundaries.length - 2) {
      piece.min = rawMin
      piece.label = `${labelMin} 以上`
    } else {
      piece.min = rawMin
      piece.max = rawMax
      piece.label = `${labelMin} ~ ${labelMax}`
    }
    pieces.push(piece)
  }

  if (pieces.length < 3) {
    const minVal = Math.min(...values)
    const maxVal = Math.max(...values)
    const mid = (minVal + maxVal) / 2
    return [
      { max: mid, label: `${roundSmart(mid)} 以下` },
      { min: mid, max: maxVal, label: `${roundSmart(mid)} ~ ${roundSmart(maxVal)}` },
      { min: maxVal, label: `${roundSmart(maxVal)} 以上` }
    ]
  }

  return pieces
})

const subRegions = ref([])
const currentGeoJson = ref(null)

const selectedCityInfo = computed(() => {
  if (currentLevel.value !== 2) return null
  const cleanName = cleanCityName(currentName.value)
  const item = rankingData.value.find(
    (d) => cleanCityName(d.city) === cleanName
  )
  return item || { city: currentName.value, province: '', metricValue: '-', days: '-' }
})

// ========== 地图渲染 ==========
const renderMap = async () => {
  if (!mapChart) return
  mapLoading.value = true
  errorMsg.value = ''

  try {
    const full = currentLevel.value < 2
    const geoJson = await getGeoJson(currentAdcode.value, full)
    currentGeoJson.value = geoJson
    const mapName = `map_${currentAdcode.value}`
    echarts.registerMap(mapName, geoJson)
    registeredMapName.value = mapName

    subRegions.value = extractRegions(geoJson)

    const data = currentLevel.value === 0 ? provinceMapData.value : cityMapData.value
    const range = visualMapRange.value
    const hasMapData = visualMapPieces.value.length > 0

    const option = {
      title: hasMapData ? undefined : {
        text: '暂无数据',
        left: 'center',
        top: 'center',
        textStyle: { color: '#9ca3af', fontSize: 18, fontWeight: 'normal' }
      },
      tooltip: {
        trigger: 'item',
        formatter: (params) => {
          if (params.value == null || isNaN(params.value)) {
            return `<b>${params.name}</b><br/>暂无数据`
          }
          const meta = metricMeta.value
          if (currentLevel.value === 0) {
            const cleanName = cleanProvinceName(params.name)
            const cityCount = rankingData.value.filter(
              (d) => d.province && cleanProvinceName(d.province) === cleanName
            ).length
            return `<b>${params.name}</b><br/>` +
              `平均${meta.displayName || '指标值'}：${params.value}${meta.unit || ''}<br/>` +
              `覆盖城市：${cityCount}个`
          }
          return `<b>${params.name}</b><br/>` +
            `${meta.displayName || '指标值'}：${params.value}${meta.unit || ''}`
        }
      },
      visualMap: {
        show: hasMapData,
        type: 'piecewise',
        pieces: visualMapPieces.value,
        left: 20,
        bottom: 20,
        textStyle: { color: '#6b7280', fontSize: 10 },
        itemWidth: 14,
        itemHeight: 10,
        itemGap: 4,
        inRange: {
          color: currentColorScheme.value.visualMap
        }
      },
      geo: {
        map: mapName,
        roam: true,
        zoom: 1.1,
        label: {
          show: currentLevel.value >= 1,
          color: '#374151',
          fontSize: 11
        },
        itemStyle: {
          areaColor: '#f1f5f9',
          borderColor: '#e2e8f0',
          borderWidth: 0.5
        },
        emphasis: {
          label: { show: true, color: '#fff', fontWeight: 'bold' },
          itemStyle: { areaColor: currentColorScheme.value.emphasis }
        },
        select: {
          label: { color: '#fff' },
          itemStyle: { areaColor: currentColorScheme.value.select }
        }
      },
      series: [
        {
          name: metricMeta.value.displayName || '气象指标',
          type: 'map',
          map: mapName,
          geoIndex: 0,
          data: data
        }
      ]
    }

    mapChart.setOption(option, true)
  } catch (e) {
    console.error('渲染地图失败:', e)
    const isTaiwan = currentName.value &&
      (currentName.value.includes('台湾') || currentName.value === '台湾省')

    if (isTaiwan) {
      errorMsg.value = ''
      mapChart.clear()
      mapChart.setOption({
        title: {
          text: '地图加载失败，台湾省早日回归',
          left: 'center',
          top: 'center',
          textStyle: { color: '#ef4444', fontSize: 16 }
        }
      })
    } else {
      errorMsg.value = '地图数据加载失败，请检查网络连接'
      mapChart.clear()
      mapChart.setOption({
        title: {
          text: '地图加载失败',
          left: 'center',
          top: 'center',
          textStyle: { color: '#ef4444', fontSize: 16 }
        }
      })
    }
  } finally {
    mapLoading.value = false
  }
}

// ========== 城市趋势图渲染 ==========
const ensureTrendChart = () => {
  if (!trendChartRef.value) return null
  if (!trendChart || trendChart.getDom() !== trendChartRef.value) {
    if (trendChart) {
      trendChart.dispose()
    }
    trendChart = echarts.init(trendChartRef.value)
  }
  return trendChart
}

const renderCityTrendChart = () => {
  if (!ensureTrendChart()) return
  const data = cityTrendData.value
  const meta = metricMeta.value

  if (!data || data.length === 0) {
    trendChart.clear()
    trendChart.setOption({
      title: {
        text: '暂无趋势数据',
        left: 'center',
        top: 'center',
        textStyle: { color: '#9ca3af', fontSize: 14 }
      }
    })
    return
  }

  const years = data.map((d) => d.year)
  const values = data.map((d) => d.metricValue)
  const days = data.map((d) => d.days)

  const option = {
    tooltip: {
      trigger: 'axis',
      formatter: (params) => {
        const p = params[0]
        const d = data[p.dataIndex]
        let trendHtml = ''
        if (d.trendLabel) {
          const color =
            d.trendLabel === '上升' ? '#ef4444' : d.trendLabel === '下降' ? '#10b981' : '#f59e0b'
          trendHtml = `<br/>趋势：<span style="color:${color}">${d.trendLabel}</span>`
        }
        return `<b>${d.year}年</b><br/>` +
          `${meta.displayName}：${d.metricValue}${meta.unit}<br/>` +
          `${meta.daysName}：${d.days}天${trendHtml}`
      }
    },
    legend: {
      data: [meta.displayName, meta.daysName],
      textStyle: { color: '#6b7280' },
      top: 0
    },
    grid: { left: 50, right: 50, top: 35, bottom: 25 },
    xAxis: {
      type: 'category',
      data: years,
      axisLabel: { color: '#6b7280', fontSize: 11 },
      axisLine: { lineStyle: { color: '#d1d5db' } }
    },
    yAxis: [
      {
        type: 'value',
        name: meta.unit,
        nameTextStyle: { color: '#6b7280', fontSize: 11 },
        axisLabel: { color: '#6b7280', fontSize: 11 },
        splitLine: { lineStyle: { color: '#e5e7eb' } }
      },
      {
        type: 'value',
        name: '天',
        nameTextStyle: { color: '#6b7280', fontSize: 11 },
        axisLabel: { color: '#6b7280', fontSize: 11 },
        splitLine: { show: false }
      }
    ],
    series: [
      {
        name: meta.displayName,
        type: 'line',
        data: values,
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: { width: 2.5, color: currentColorScheme.value.emphasis },
        itemStyle: { color: currentColorScheme.value.emphasis, borderColor: '#fff', borderWidth: 1.5 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: currentColorScheme.value.emphasis + '4D' },
            { offset: 1, color: currentColorScheme.value.emphasis + '05' }
          ])
        }
      },
      {
        name: meta.daysName,
        type: 'line',
        yAxisIndex: 1,
        data: days,
        smooth: true,
        symbol: 'diamond',
        symbolSize: 5,
        lineStyle: { width: 1.5, color: '#f59e0b', type: 'dashed' },
        itemStyle: { color: '#f59e0b' }
      }
    ]
  }
  trendChart.setOption(option, true)
}

// ========== 地图点击事件：下钻 ==========
const handleMapClick = async (params) => {
  const clickedName = params.name
  if (!clickedName) return

  let region = subRegions.value.find((r) => r.name === clickedName)
  if (!region) {
    region = subRegions.value.find((r) =>
      r.name.includes(clickedName) || clickedName.includes(r.name)
    )
  }
  if (!region) return

  if (currentLevel.value === 0) {
    if (isMunicipality(clickedName) || isMunicipality(region.name)) {
      await drillDown(region.adcode, region.name, 2)
    } else {
      await drillDown(region.adcode, region.name, 1)
    }
  } else if (currentLevel.value === 1) {
    await drillDown(region.adcode, region.name, 2)
  }
}

const drillDown = async (adcode, name, level) => {
  currentLevel.value = level
  currentAdcode.value = adcode
  currentName.value = name
  breadcrumb.value.push({ name, adcode, level })

  await renderMap()

  if (level === 2) {
    await loadCityTrend(name)
    await nextTick()
    await nextTick()
    if (ensureTrendChart()) {
      renderCityTrendChart()
    }
  }
}

const navigateTo = (index) => {
  if (index >= breadcrumb.value.length - 1) return
  const target = breadcrumb.value[index]
  breadcrumb.value = breadcrumb.value.slice(0, index + 1)
  currentLevel.value = target.level
  currentAdcode.value = target.adcode
  currentName.value = target.name
  cityTrendData.value = []
  renderMap()
}

const goBack = () => {
  if (breadcrumb.value.length <= 1) return
  navigateTo(breadcrumb.value.length - 2)
}

// ========== 数据加载 ==========
const loadRankingData = async () => {
  if (!selectedYear.value) return
  loading.value = true
  try {
    rankingData.value = await getRanking({
      metric: selectedMetric.value,
      year: selectedYear.value,
      limit: 500
    })
  } catch (e) {
    console.error('加载排名数据失败:', e)
    rankingData.value = []
  } finally {
    loading.value = false
  }
}

const loadCityTrend = async (cityName) => {
  try {
    let apiCityName = cityName
    if (apiCityName && !apiCityName.endsWith('市') && !apiCityName.endsWith('州') && !apiCityName.endsWith('盟') && !apiCityName.endsWith('地区')) {
      apiCityName = apiCityName + '市'
    }
    cityTrendData.value = await getTrend({
      metric: selectedMetric.value,
      city: apiCityName
    })
    nextTick(() => renderCityTrendChart())
  } catch (e) {
    console.error('加载城市趋势数据失败:', e)
    cityTrendData.value = []
  }
}

const handleMetricChange = async () => {
  await loadRankingData()
  if (currentLevel.value === 2) {
    await loadCityTrend(currentName.value)
  }
  renderMap()
}

const handleYearChange = async () => {
  await loadRankingData()
  renderMap()
}

// ========== 生命周期 ==========
onMounted(async () => {
  await nextTick()
  mapChart = echarts.init(mapChartRef.value)

  mapChart.on('click', handleMapClick)

  resizeHandler = () => {
    mapChart?.resize()
    trendChart?.resize()
  }
  window.addEventListener('resize', resizeHandler)

  if (weatherStore.metrics.length === 0) {
    await weatherStore.loadMetrics()
  }
  if (weatherStore.years.length === 0) {
    await weatherStore.loadYears()
  }
  selectedYear.value = weatherStore.years[0] || 2020

  await loadRankingData()
  await renderMap()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeHandler)
  mapChart?.off('click', handleMapClick)
  mapChart?.dispose()
  trendChart?.dispose()
})
</script>

<template>
  <div class="geo-dashboard">
    <!-- 面包屑导航 -->
    <div class="breadcrumb">
      <span
        v-for="(item, index) in breadcrumb"
        :key="item.adcode"
        class="breadcrumb-item"
        :class="{ active: index === breadcrumb.length - 1, clickable: index < breadcrumb.length - 1 }"
        @click="navigateTo(index)"
      >
        {{ item.name }}
        <span v-if="index < breadcrumb.length - 1" class="breadcrumb-sep">/</span>
      </span>
    </div>

    <!-- 控制面板 -->
    <div class="control-panel">
      <div class="control-item">
        <label>气象指标：</label>
        <select v-model="selectedMetric" @change="handleMetricChange">
          <option v-for="m in weatherStore.metrics" :key="m.code" :value="m.code">
            {{ m.name }}（{{ m.displayName }}）
          </option>
        </select>
      </div>
      <div class="control-item">
        <label>年份：</label>
        <select v-model="selectedYear" @change="handleYearChange">
          <option v-for="y in weatherStore.years" :key="y" :value="y">{{ y }}年</option>
        </select>
      </div>
      <div class="control-item hint">
        <span v-if="currentLevel === 0">💡 点击省份下钻查看城市分布</span>
        <span v-else-if="currentLevel === 1">💡 点击城市查看详细数据</span>
        <span v-else>💡 点击面包屑返回上一级</span>
      </div>
    </div>

    <!-- 主内容区 -->
    <div class="main-content">
      <!-- 左侧：地图 -->
      <div class="map-section">
        <div class="section-card">
          <div class="section-title">
            <span class="title-bar"></span>
            {{ currentName }} - {{ metricMeta.displayName || '气象指标' }}分布
            <span v-if="mapLoading" class="loading-text">（地图加载中...）</span>
          </div>
          <div class="map-chart-wrapper">
            <div ref="mapChartRef" class="map-chart"></div>
            <button
              v-if="currentLevel > 0"
              class="map-back-btn"
              @click="goBack"
              title="返回上一级"
            >
              ← 返回上一级
            </button>
          </div>
          <div v-if="errorMsg" class="error-msg">{{ errorMsg }}</div>
        </div>
      </div>

      <!-- 右侧：信息面板 -->
      <div class="info-section">
        <!-- 中国层级：全国概览 -->
        <div v-if="currentLevel === 0" class="section-card">
          <div class="section-title">
            <span class="title-bar"></span>
            全国概览
          </div>
          <div class="overview-stats">
            <div class="stat-item">
              <div class="stat-value">{{ rankingData.length }}</div>
              <div class="stat-label">覆盖城市数</div>
            </div>
            <div class="stat-item">
              <div class="stat-value">{{ weatherStore.metrics.length }}</div>
              <div class="stat-label">气象指标数</div>
            </div>
            <div class="stat-item">
              <div class="stat-value">{{ weatherStore.years.length }}</div>
              <div class="stat-label">数据年份数</div>
            </div>
          </div>
          <div class="section-subtitle">指标说明</div>
          <div class="metric-list">
            <div v-for="m in weatherStore.metrics" :key="m.code" class="metric-item">
              <span class="metric-name">{{ m.name }}</span>
              <span class="metric-detail">{{ m.displayName }}（{{ m.unit }}）</span>
            </div>
          </div>
        </div>

        <!-- 省份层级：该省城市列表 -->
        <div v-else-if="currentLevel === 1" class="section-card">
          <div class="section-title">
            <span class="title-bar"></span>
            {{ currentName }} - 城市排名
          </div>
          <div class="city-ranking-list">
            <div
              v-for="(city, idx) in cityMapData.slice(0, 20)"
              :key="city.name"
              class="city-ranking-item"
            >
              <span class="city-rank">{{ idx + 1 }}</span>
              <span class="city-name">{{ city.name }}</span>
              <span class="city-value">{{ city.value }}{{ metricMeta.unit }}</span>
            </div>
            <div v-if="cityMapData.length === 0" class="empty-text">该省暂无数据</div>
          </div>
        </div>

        <!-- 城市层级：详细数据 -->
        <div v-else class="section-card">
          <div class="section-title">
            <span class="title-bar"></span>
            {{ currentName }} - 详细数据
          </div>

          <div class="city-info-card">
            <div class="city-info-row">
              <span class="info-label">城市：</span>
              <span class="info-value">{{ selectedCityInfo.city }}</span>
            </div>
            <div class="city-info-row">
              <span class="info-label">所属省份：</span>
              <span class="info-value">{{ selectedCityInfo.province || '-' }}</span>
            </div>
            <div class="city-info-row">
              <span class="info-label">{{ selectedYear }}年{{ metricMeta.displayName }}：</span>
              <span class="info-value highlight">{{ selectedCityInfo.metricValue }}{{ metricMeta.unit }}</span>
            </div>
            <div class="city-info-row">
              <span class="info-label">{{ metricMeta.daysName }}：</span>
              <span class="info-value">{{ selectedCityInfo.days }}天</span>
            </div>
            <div v-if="selectedCityInfo.rankNum" class="city-info-row">
              <span class="info-label">全国排名：</span>
              <span class="info-value">第 {{ selectedCityInfo.rankNum }} 名</span>
            </div>
          </div>

          <div class="section-subtitle">历年变化趋势（{{ metricMeta.displayName }}）</div>
          <div ref="trendChartRef" class="trend-chart"></div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.geo-dashboard {
  padding: 20px 24px;
}

/* 面包屑 */
.breadcrumb {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 16px;
  padding: 10px 16px;
  background: #ffffff;
  border-radius: 4px;
  border: 1px solid #e5e7eb;
}

.breadcrumb-item {
  font-size: 14px;
  color: #6b7280;
}

.breadcrumb-item.clickable {
  cursor: pointer;
  transition: color 0.2s;
}

.breadcrumb-item.clickable:hover {
  color: #3b82f6;
}

.breadcrumb-item.active {
  color: #3b82f6;
  font-weight: bold;
}

.breadcrumb-sep {
  margin: 0 6px;
  color: #d1d5db;
}

/* 控制面板 */
.control-panel {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.control-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.control-item label {
  color: #6b7280;
  font-size: 14px;
  white-space: nowrap;
}

.control-item select {
  padding: 7px 12px;
  border-radius: 6px;
  border: 1px solid #d1d5db;
  background: #ffffff;
  color: #1f2937;
  font-size: 13px;
  cursor: pointer;
  outline: none;
  min-width: 160px;
}

.control-item select:focus {
  border-color: #3b82f6;
}

.control-item.hint {
  color: #92400e;
  font-size: 13px;
}

.loading-text {
  color: #f59e0b;
  font-size: 13px;
  font-weight: normal;
}

/* 主内容区 */
.main-content {
  display: grid;
  grid-template-columns: 1fr 380px;
  gap: 16px;
}

.map-section {
  min-width: 0;
}

.section-card {
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  padding: 16px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: bold;
  color: #1f2937;
  margin-bottom: 12px;
}

.title-bar {
  width: 4px;
  height: 16px;
  background: linear-gradient(180deg, #3b82f6, #6366f1);
  border-radius: 2px;
}

.section-subtitle {
  font-size: 14px;
  color: #6b7280;
  margin: 14px 0 8px;
  font-weight: 600;
}

/* 地图 */
.map-chart-wrapper {
  position: relative;
  width: 100%;
}

.map-chart {
  width: 100%;
  height: 600px;
}

.map-back-btn {
  position: absolute;
  top: 16px;
  left: 16px;
  z-index: 10;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 8px 16px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  color: #6b7280;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
}

.map-back-btn:hover {
  border-color: #3b82f6;
  color: #3b82f6;
  background: #eff6ff;
}

.error-msg {
  text-align: center;
  padding: 20px;
  color: #ef4444;
  font-size: 14px;
}

/* 全国概览统计 */
.overview-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-bottom: 10px;
}

.stat-item {
  text-align: center;
  padding: 14px 8px;
  background: #eff6ff;
  border-radius: 4px;
  border: 1px solid #bfdbfe;
}

.stat-value {
  font-size: 24px;
  font-weight: bold;
  color: #3b82f6;
  margin-bottom: 4px;
}

.stat-label {
  font-size: 12px;
  color: #6b7280;
}

/* 指标列表 */
.metric-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.metric-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: #f9fafb;
  border-radius: 6px;
  font-size: 13px;
}

.metric-name {
  color: #1f2937;
  font-weight: 600;
}

.metric-detail {
  color: #6b7280;
  font-size: 12px;
}

/* 城市排名列表 */
.city-ranking-list {
  max-height: 560px;
  overflow-y: auto;
}

.city-ranking-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  border-bottom: 1px solid #f3f4f6;
  font-size: 13px;
  transition: background 0.15s;
}

.city-ranking-item:hover {
  background: #f9fafb;
}

.city-rank {
  width: 24px;
  height: 24px;
  line-height: 24px;
  text-align: center;
  border-radius: 50%;
  background: #f3f4f6;
  color: #6b7280;
  font-size: 12px;
  font-weight: bold;
  flex-shrink: 0;
}

.city-ranking-item:nth-child(1) .city-rank {
  background: linear-gradient(135deg, #fbbf24, #f59e0b);
  color: #fff;
}

.city-ranking-item:nth-child(2) .city-rank {
  background: linear-gradient(135deg, #d1d5db, #9ca3af);
  color: #fff;
}

.city-ranking-item:nth-child(3) .city-rank {
  background: linear-gradient(135deg, #fb923c, #ea580c);
  color: #fff;
}

.city-name {
  flex: 1;
  color: #374151;
}

.city-value {
  color: #3b82f6;
  font-weight: 600;
  font-size: 12px;
}

.empty-text {
  text-align: center;
  padding: 30px;
  color: #9ca3af;
  font-size: 13px;
}

/* 城市详情卡片 */
.city-info-card {
  background: #f9fafb;
  border-radius: 4px;
  padding: 12px;
  margin-bottom: 8px;
}

.city-info-row {
  display: flex;
  justify-content: space-between;
  padding: 5px 0;
  font-size: 13px;
  border-bottom: 1px solid #e5e7eb;
}

.city-info-row:last-child {
  border-bottom: none;
}

.info-label {
  color: #6b7280;
}

.info-value {
  color: #1f2937;
  font-weight: 500;
}

.info-value.highlight {
  color: #3b82f6;
  font-size: 15px;
  font-weight: bold;
}

/* 趋势图 */
.trend-chart {
  width: 100%;
  height: 280px;
}

@media (max-width: 1200px) {
  .main-content {
    grid-template-columns: 1fr;
  }
  .map-chart {
    height: 500px;
  }
}
</style>
