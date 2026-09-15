<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts'
import { useWeatherStore } from '@/stores/weather'
import CitySelect from '@/components/CitySelect.vue'

const store = useWeatherStore()

// ========== ECharts 实例 ==========
const rankingChartRef = ref(null)
const trendChartRef = ref(null)
let rankingChart = null
let trendChart = null
let resizeHandler = null


// ========== 标题计算 ==========
const rankingTitle = computed(() => {
  const year = store.currentYear ? store.currentYear + '年' : ''
  const name = store.currentMetricMeta.name || ''
  const province = store.selectedProvince ? store.selectedProvince : '全国城市'
  return `${year} ${province}${name}排名 Top${store.limit}`
})

const trendTitle = computed(() => {
  if (!store.currentCity) return '历年趋势（请在上方选择城市）'
  const city = store.currentCity
  const name = store.currentMetricMeta.name || ''
  let rankInfo = ''
  if (store.currentYear) {
    rankInfo = store.currentCityRank
      ? ` · ${store.currentYear}年排名第${store.currentCityRank}名`
      : ` · ${store.currentYear}年排名Top${store.limit}之外`
  }
  return `${city} 历年${name}变化趋势${rankInfo}`
})

const thMetric = computed(() => {
  const meta = store.currentMetricMeta
  return `${meta.displayName || '指标值'}(${meta.unit || ''})`
})

const thDays = computed(() => store.currentMetricMeta.daysName || '天数')

// ========== 数据覆盖说明 ==========
const isTempMetric = computed(() => store.currentMetric === 'temp')
const coverageNote = computed(() => {
  const total = store.cities.length
  const limit = store.limit
  if (isTempMetric.value) {
    return `当前指标共覆盖 ${total} 个城市，排名与地图展示前 ${limit} 名。年平均气温南高北低，Top${limit} 集中于华南地区属正常现象。`
  }
  return `当前指标共覆盖 ${total} 个城市，排名与地图展示前 ${limit} 名。`
})

// ========== 排名徽章样式 ==========
const getRankClass = (rankNum) => {
  if (rankNum === 1) return 'rank-1'
  if (rankNum === 2) return 'rank-2'
  if (rankNum === 3) return 'rank-3'
  return 'rank-other'
}


// 城市选中/清空后重新加载趋势
const onCityChange = () => {
  store.loadTrend()
}

// ========== 图表渲染 ==========
const renderRankingChart = () => {
  if (!rankingChart) return
  const data = store.filteredRankingData
  const meta = store.currentMetricMeta

  if (!data || data.length === 0) {
    rankingChart.clear()
    const emptyText = store.selectedProvince
      ? `${store.selectedProvince}在当前排名中暂无数据`
      : store.currentYear
        ? '数据加载中...'
        : '该指标暂无年份数据'
    rankingChart.setOption({
      title: {
        text: emptyText,
        left: 'center',
        top: 'center',
        textStyle: { color: '#f59e0b', fontSize: 14 }
      }
    })
    return
  }

  const cities = data.map((d) => d.city).reverse()
  const values = data.map((d) => d.metricValue).reverse()

  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params) => {
        const idx = params[0].dataIndex
        const d = data[data.length - 1 - idx]
        return `<b>${d.city}</b>（${d.province || '-'}）<br/>` +
          `排名：第${d.rankNum}名<br/>` +
          `${meta.displayName}：${d.metricValue}${meta.unit}<br/>` +
          `${meta.daysName}：${d.days}天<br/>` +
          `<span style="color:#9ca3af;font-size:12px">点击查看该城市历年趋势</span>`
      }
    },
    grid: { left: 80, right: 50, top: 20, bottom: 30 },
    xAxis: {
      type: 'value',
      axisLabel: { color: '#6b7280' },
      splitLine: { lineStyle: { color: '#e5e7eb' } }
    },
    yAxis: {
      type: 'category',
      data: cities,
      axisLabel: { color: '#374151', fontSize: 12 },
      axisLine: { lineStyle: { color: '#d1d5db' } }
    },
    series: [
      {
        type: 'bar',
        cursor: 'pointer',
        data: values.map((v, i) => {
          if (cities[i] === store.currentCity) {
            return {
              value: v,
              itemStyle: {
                borderRadius: [0, 6, 6, 0],
                color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
                  { offset: 0, color: '#f59e0b' },
                  { offset: 1, color: '#ef4444' }
                ])
              }
            }
          }
          return v
        }),
        barWidth: '60%',
        itemStyle: {
          borderRadius: [0, 6, 6, 0],
          color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
            { offset: 0, color: '#3b82f6' },
            { offset: 1, color: '#6366f1' }
          ])
        },
        label: {
          show: true,
          position: 'right',
          color: '#1f2937',
          fontSize: 11,
          formatter: '{c}'
        }
      }
    ]
  }
  rankingChart.setOption(option, true)
}

const renderTrendChart = () => {
  if (!trendChart) return
  const data = store.trendData
  const meta = store.currentMetricMeta

  if (!data || data.length === 0) {
    trendChart.clear()
    const emptyText = !store.currentCity
      ? '请选择城市查看历年趋势'
      : '趋势数据加载失败'
    trendChart.setOption({
      title: {
        text: emptyText,
        left: 'center',
        top: 'center',
        textStyle: { color: '#f59e0b', fontSize: 14 }
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
            d.trendLabel === '上升'
              ? '#ef4444'
              : d.trendLabel === '下降'
                ? '#10b981'
                : '#f59e0b'
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
    grid: { left: 60, right: 60, top: 40, bottom: 30 },
    xAxis: {
      type: 'category',
      data: years,
      axisLabel: { color: '#6b7280' },
      axisLine: { lineStyle: { color: '#d1d5db' } }
    },
    yAxis: [
      {
        type: 'value',
        name: meta.unit,
        nameTextStyle: { color: '#6b7280' },
        axisLabel: { color: '#6b7280' },
        splitLine: { lineStyle: { color: '#e5e7eb' } }
      },
      {
        type: 'value',
        name: '天',
        nameTextStyle: { color: '#6b7280' },
        axisLabel: { color: '#6b7280' },
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
        symbolSize: 8,
        lineStyle: { width: 3, color: '#3b82f6' },
        itemStyle: { color: '#3b82f6', borderColor: '#fff', borderWidth: 2 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(59, 130, 246, 0.25)' },
            { offset: 1, color: 'rgba(59, 130, 246, 0.02)' }
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
        symbolSize: 7,
        lineStyle: { width: 2, color: '#f59e0b', type: 'dashed' },
        itemStyle: { color: '#f59e0b' }
      }
    ]
  }
  trendChart.setOption(option, true)
}


// ========== 生命周期 ==========
onMounted(async () => {
  await nextTick()
  requestAnimationFrame(async () => {
    rankingChart = echarts.init(rankingChartRef.value)
    trendChart = echarts.init(trendChartRef.value)

    rankingChart.on('click', (params) => {
      if (params.componentType === 'series' && params.seriesType === 'bar') {
        const city = params.name
        if (city && city !== store.currentCity) {
          store.currentCity = city
          store.loadTrend()
        }
      }
    })

    resizeHandler = () => {
      rankingChart?.resize()
      trendChart?.resize()
    }
    window.addEventListener('resize', resizeHandler)

    setTimeout(() => {
      rankingChart?.resize()
      trendChart?.resize()
    }, 300)


    try {
      await store.init().catch((e) => console.error('数据初始化失败:', e))
    } catch (e) {
      console.error('初始化异常:', e)
    }
    await nextTick()
    try {
      renderRankingChart()
      renderTrendChart()
    } catch (e) {
      console.error('兜底渲染失败:', e)
    }
  })
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeHandler)
  rankingChart?.dispose()
  trendChart?.dispose()
})

watch(
  () => [store.rankingData, store.currentMetric, store.currentCity, store.selectedProvince],
  () => {
    nextTick(() => {
      renderRankingChart()
    })
  },
  { deep: true }
)

watch(
  () => [store.trendData, store.currentMetric],
  () => {
    nextTick(() => {
      renderTrendChart()
    })
  }
)

const handleYearChange = async (e) => {
  store.currentYear = Number(e.target.value)
  await store.refreshRanking()
  await nextTick()
  renderRankingChart()
}

const handleLimitChange = async (e) => {
  store.limit = Number(e.target.value)
  await store.refreshRanking()
  await nextTick()
  renderRankingChart()
}

const clearProvinceFilter = () => {
  store.toggleProvince(null)
  nextTick(() => {
    renderRankingChart()
  })
}

const handleSwitchMetric = async (code) => {
  await store.switchMetric(code)
}
</script>

<template>
  <div class="dashboard-container">
    <!-- 指标切换标签 -->
    <div class="metric-tabs">
      <div
        v-for="m in store.metrics"
        :key="m.code"
        :class="['metric-tab', 'metric-' + m.code, { active: m.code === store.currentMetric }]"
        @click="handleSwitchMetric(m.code)"
      >
        {{ m.name }}
      </div>
    </div>

    <!-- 控制面板 -->
    <div class="control-panel">
      <div class="control-item">
        <label>年份：</label>
        <select :value="store.currentYear" @change="handleYearChange">
          <option v-for="y in store.years" :key="y" :value="y">{{ y }}年</option>
        </select>
      </div>

      <div class="control-item">
        <label>城市：</label>
        <CitySelect
          v-model="store.currentCity"
          :cities="store.cities"
          placeholder="搜索或选择城市"
          clearable
          @change="onCityChange"
        />
      </div>

      <div class="control-item">
        <label>排名数量：</label>
        <select :value="store.limit" @change="handleLimitChange">
          <option :value="10">Top 10</option>
          <option :value="15">Top 15</option>
          <option :value="20">Top 20</option>
          <option :value="30">Top 30</option>
        </select>
      </div>
    </div>

    <!-- 加载提示 -->
    <div class="loading-bar" :class="{ 'is-hidden': !store.loading }">数据加载中...</div>

    <!-- 数据覆盖说明 -->
    <div class="coverage-note" :class="{ 'is-temp': isTempMetric }">
      <span class="coverage-icon">📊</span>
      <span>{{ coverageNote }}</span>
    </div>

    <!-- 省份筛选提示条 -->
    <div v-if="store.selectedProvince" class="province-filter-bar">
      <span class="filter-label">已筛选省份：</span>
      <span class="filter-province">{{ store.selectedProvince }}</span>
      <span class="filter-count">（{{ store.filteredRankingData.length }} 个城市）</span>
      <button class="filter-clear-btn" @click="clearProvinceFilter">× 取消筛选</button>
    </div>

    <!-- 图表区：排名 + 趋势 -->
    <div class="charts-row">
      <div class="chart-card">
        <div class="chart-title">{{ rankingTitle }}</div>
        <div ref="rankingChartRef" class="chart-container"></div>
      </div>
      <div class="chart-card">
        <div class="chart-title">{{ trendTitle }}</div>
        <div ref="trendChartRef" class="chart-container"></div>
      </div>
    </div>

    <!-- 数据表格 -->
    <div class="table-card">
      <div class="chart-title">排名明细数据</div>
      <div style="overflow-x: auto">
        <table class="data-table">
          <thead>
            <tr>
              <th>排名</th>
              <th>城市</th>
              <th>省份</th>
              <th>{{ thMetric }}</th>
              <th>{{ thDays }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!store.filteredRankingData || store.filteredRankingData.length === 0">
              <td colspan="5" class="loading">{{ store.selectedProvince ? '该省份暂无数据' : '暂无数据' }}</td>
            </tr>
            <tr v-for="d in store.filteredRankingData" :key="d.rankNum">
              <td>
                <span class="rank-badge" :class="getRankClass(d.rankNum)">
                  {{ d.rankNum }}
                </span>
              </td>
              <td>{{ d.city }}</td>
              <td>{{ d.province || '-' }}</td>
              <td>{{ d.metricValue }}</td>
              <td>{{ d.days }}天</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dashboard-container {
  padding: 20px 24px;
}

/* 指标切换标签 */
.metric-tabs {
  display: flex;
  justify-content: center;
  gap: 10px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.metric-tab {
  padding: 8px 22px;
  border-radius: 4px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.3s;
  user-select: none;
  color: #374151;
}

.metric-tab:hover {
  border-color: #3b82f6;
  color: #3b82f6;
}

.metric-tab.active {
  color: #fff;
  font-weight: bold;
  border-color: transparent;
}
.metric-tab.active.metric-rain { background: linear-gradient(135deg, #3b82f6, #2563eb); }
.metric-tab.active.metric-temp { background: linear-gradient(135deg, #f97316, #ef4444); }
.metric-tab.active.metric-wind { background: linear-gradient(135deg, #10b981, #06b6d4); }
.metric-tab.active.metric-sun { background: linear-gradient(135deg, #f59e0b, #fbbf24); }
.metric-tab.active.metric-humidity { background: linear-gradient(135deg, #8b5cf6, #a855f7); }

/* 控制面板 */
.control-panel {
  display: flex;
  justify-content: center;
  gap: 20px;
  margin-bottom: 20px;
  flex-wrap: wrap;
  align-items: center;
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
  padding: 7px 14px;
  border-radius: 6px;
  border: 1px solid #d1d5db;
  background: #ffffff;
  color: #1f2937;
  font-size: 14px;
  cursor: pointer;
  outline: none;
  min-width: 140px;
}

.control-item select:focus {
  border-color: #3b82f6;
}

/* 加载提示 */
.loading-bar {
  text-align: center;
  padding: 10px;
  color: #3b82f6;
  font-size: 14px;
  height: 38px;
  box-sizing: border-box;
  visibility: visible;
  opacity: 1;
  transition: opacity 0.2s;
}

.loading-bar.is-hidden {
  visibility: hidden;
  opacity: 0;
}

/* 数据覆盖说明条 */
.coverage-note {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  margin-bottom: 12px;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  border-radius: 4px;
  color: #6b7280;
  font-size: 13px;
  line-height: 1.5;
}

.coverage-note.is-temp {
  background: #fffbeb;
  border-color: #fde68a;
  color: #92400e;
}

.coverage-icon {
  flex-shrink: 0;
  font-size: 14px;
}

/* 省份筛选提示条 */
.province-filter-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  margin-bottom: 16px;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  border-radius: 4px;
  font-size: 14px;
}

.filter-label {
  color: #6b7280;
}

.filter-province {
  color: #3b82f6;
  font-weight: bold;
  font-size: 15px;
}

.filter-count {
  color: #9ca3af;
  font-size: 13px;
}

.filter-clear-btn {
  margin-left: auto;
  padding: 4px 12px;
  border-radius: 6px;
  border: 1px solid #ef4444;
  background: transparent;
  color: #ef4444;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}

.filter-clear-btn:hover {
  background: #fef2f2;
}

/* 图表区域 */
.charts-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  margin-bottom: 40px;
}

.chart-card {
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  padding: 16px;
  /* 高度 = 100vh - 顶部栏(60px) - dashboard padding上下(40px) - 指标tabs(60px含margin) - 控制栏(50px) - coverage-note(30px) - 余量 */
  height: calc(100vh - 320px);
  min-height: 480px;
  display: flex;
  flex-direction: column;
}

.chart-title {
  font-size: 15px;
  font-weight: bold;
  margin-bottom: 10px;
  color: #1f2937;
  display: flex;
  align-items: center;
  gap: 8px;
}

.chart-title::before {
  content: '';
  width: 4px;
  height: 18px;
  background: linear-gradient(180deg, #3b82f6, #6366f1);
  border-radius: 2px;
}

.chart-container {
  width: 100%;
  flex: 1;
  min-height: 0;
}



/* 数据表格 */
.table-card {
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  padding: 16px;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

.data-table th {
  background: #eff6ff;
  color: #3b82f6;
  padding: 12px;
  text-align: left;
  border-bottom: 2px solid #e5e7eb;
  font-weight: 600;
}

.data-table td {
  padding: 10px 12px;
  border-bottom: 1px solid #f3f4f6;
  color: #374151;
}

.data-table tr:hover td {
  background: #f9fafb;
}

.rank-badge {
  display: inline-block;
  width: 26px;
  height: 26px;
  line-height: 26px;
  text-align: center;
  border-radius: 50%;
  font-weight: bold;
  font-size: 12px;
}

.rank-1 {
  background: linear-gradient(135deg, #fbbf24, #f59e0b);
  color: #fff;
}

.rank-2 {
  background: linear-gradient(135deg, #d1d5db, #9ca3af);
  color: #fff;
}

.rank-3 {
  background: linear-gradient(135deg, #fb923c, #ea580c);
  color: #fff;
}

.rank-other {
  background: #f3f4f6;
  color: #6b7280;
}

.loading {
  text-align: center;
  padding: 30px;
  color: #6b7280;
  font-size: 14px;
}

@media (max-width: 900px) {
  .charts-row {
    grid-template-columns: 1fr;
  }
  .chart-card {
    height: 420px;
  }
  }
</style>
