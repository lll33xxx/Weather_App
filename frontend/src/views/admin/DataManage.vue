<script setup>
import { ref, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getWeatherDataList, getWeatherYears, getWeatherProvinces, getWeatherDaily } from '@/api/admin'
import CitySelect from '@/components/CitySelect.vue'
import { cityCoords } from '@/utils/cityCoords'

const metrics = [
  { code: 'rain', name: '降水', colName: '年总降水量', unit: 'mm', daysName: '降雨天数' },
  { code: 'wind', name: '风速', colName: '平均风速', unit: 'm/s', daysName: '大风天数' },
  { code: 'temp', name: '气温', colName: '平均气温', unit: '℃', daysName: '高温天数' },
  { code: 'sun', name: '日照', colName: '年总日照', unit: 'h', daysName: '晴好天数' },
  { code: 'humidity', name: '湿度', colName: '平均湿度', unit: '%', daysName: '高湿天数' }
]

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)

const selectedMetric = ref('rain')
const selectedYear = ref(null)
const selectedProvince = ref('')
const searchCity = ref('')
const cityList = Object.keys(cityCoords)

const yearOptions = ref([])
const provinceOptions = ref([])

const drawerVisible = ref(false)
const dailyLoading = ref(false)
const dailyData = ref([])
const currentCity = ref('')
const currentYear = ref(null)

const currentMetric = () => metrics.find(m => m.code === selectedMetric.value) || metrics[0]

const loadFilters = async () => {
  try {
    const [years, provinces] = await Promise.all([
      getWeatherYears(selectedMetric.value),
      getWeatherProvinces(selectedMetric.value)
    ])
    yearOptions.value = years
    provinceOptions.value = provinces
  } catch (e) {
    ElMessage.error('加载筛选条件失败')
  }
}

const loadData = async () => {
  loading.value = true
  try {
    const params = {
      metric: selectedMetric.value,
      pageNum: pageNum.value,
      pageSize: pageSize.value
    }
    if (selectedYear.value) params.year = selectedYear.value
    if (selectedProvince.value) params.province = selectedProvince.value
    if (searchCity.value.trim()) params.city = searchCity.value.trim()

    const res = await getWeatherDataList(params)
    tableData.value = res.list
    total.value = res.total
  } catch (e) {
    ElMessage.error(e.message || '加载数据失败')
  } finally {
    loading.value = false
  }
}

const handleRowClick = async (row) => {
  currentCity.value = row.city
  currentYear.value = row.year
  drawerVisible.value = true
  dailyLoading.value = true
  dailyData.value = []
  try {
    const res = await getWeatherDaily({
      metric: selectedMetric.value,
      city: row.city,
      year: row.year
    })
    dailyData.value = res
    if (res.length === 0) {
      ElMessage.info('该城市该年份暂无日度明细数据')
    }
  } catch (e) {
    ElMessage.error(e.message || '日度数据查询失败')
  } finally {
    dailyLoading.value = false
  }
}

watch(selectedMetric, () => {
  pageNum.value = 1
  loadFilters()
  loadData()
})

const handleSearch = () => {
  pageNum.value = 1
  loadData()
}

const handleReset = () => {
  selectedYear.value = null
  selectedProvince.value = ''
  searchCity.value = ''
  pageNum.value = 1
  loadData()
}

onMounted(async () => {
  await loadFilters()
  await loadData()
})
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">数据管理</h2>
      <p class="page-desc">分页浏览天气数据，点击任意城市行查看该城市该年份的逐日明细数据</p>
    </div>

    <div class="filter-bar">
      <div class="filter-item">
        <label class="filter-label">天气指标</label>
        <el-radio-group v-model="selectedMetric" size="default">
          <el-radio-button v-for="m in metrics" :key="m.code" :value="m.code">
            {{ m.name }}
          </el-radio-button>
        </el-radio-group>
      </div>

      <div class="filter-row">
        <div class="filter-item">
          <label class="filter-label">年份</label>
          <el-select
            v-model="selectedYear"
            placeholder="全部年份"
            clearable
            style="width: 140px"
            @change="handleSearch"
          >
            <el-option v-for="y in yearOptions" :key="y" :label="y" :value="y" />
          </el-select>
        </div>

        <div class="filter-item">
          <label class="filter-label">省份</label>
          <el-select
            v-model="selectedProvince"
            placeholder="全部省份"
            clearable
            filterable
            style="width: 160px"
            @change="handleSearch"
          >
            <el-option v-for="p in provinceOptions" :key="p" :label="p" :value="p" />
          </el-select>
        </div>

        <div class="filter-item">
          <label class="filter-label">城市搜索</label>
          <CitySelect
            v-model="searchCity"
            :cities="cityList"
            placeholder="输入城市名"
            clearable
            style="width: 180px"
            @change="handleSearch"
          />
        </div>

        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>
    </div>

    <el-table
      :data="tableData"
      v-loading="loading"
      class="data-table"
      max-height="560"
      highlight-current-row
      @row-click="handleRowClick"
    >
      <el-table-column prop="year" label="年份" width="90" />
      <el-table-column prop="rankNum" label="排名" width="80" />
      <el-table-column prop="city" label="城市" min-width="120">
        <template #default="{ row }">
          <span class="city-link">{{ row.city }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="province" label="省份" min-width="100" />
      <el-table-column :label="currentMetric().colName + '(' + currentMetric().unit + ')'" min-width="140">
        <template #default="{ row }">
          <span class="metric-value">{{ row.metricValue }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="currentMetric().daysName" width="120">
        <template #default="{ row }">
          {{ row.days }} 天
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button type="warning" size="small" link @click.stop="handleRowClick(row)">
            日度明细
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="table-tip">💡 点击任意行或"日度明细"按钮，查看该城市在该行对应年份的逐日数据</div>

    <div class="pagination">
      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @size-change="loadData"
        @current-change="loadData"
      />
    </div>

    <el-drawer
      v-model="drawerVisible"
      :title="currentCity + ' · ' + currentYear + '年 · ' + currentMetric().name + '逐日数据'"
      direction="rtl"
      size="600px"
    >
      <div v-loading="dailyLoading" class="daily-content">
        <el-table :data="dailyData" stripe max-height="calc(100vh - 140px)" class="daily-table">
          <el-table-column prop="date" label="日期" width="140" />
          <el-table-column :label="currentMetric().name + '(' + currentMetric().unit + ')'" min-width="160">
            <template #default="{ row }">
              <span class="metric-value">{{ row.metricValue }}</span>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="!dailyLoading && dailyData.length > 0" class="daily-summary">
          共 {{ dailyData.length }} 条日度记录
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.page-container {
  padding: 20px;
}

.page-header {
  margin-bottom: 20px;
}

.page-title {
  font-size: 20px;
  color: #1f2937;
  margin: 0 0 6px;
}

.page-desc {
  color: #6b7280;
  font-size: 13px;
  margin: 0;
}

.filter-bar {
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 16px 20px;
  margin-bottom: 16px;
}

.filter-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-label {
  color: #6b7280;
  font-size: 13px;
  white-space: nowrap;
}

.filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 14px;
  align-items: center;
}

.data-table {
  background: #ffffff;
  border-radius: 4px;
  cursor: pointer;
}

.city-link {
  color: #3b82f6;
  font-weight: 500;
  text-decoration: underline;
  text-underline-offset: 2px;
}

.metric-value {
  color: #3b82f6;
  font-weight: 600;
}

.table-tip {
  color: #9ca3af;
  font-size: 12px;
  margin-top: 10px;
  text-align: center;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}

.daily-content {
  height: 100%;
}

.daily-summary {
  text-align: center;
  color: #6b7280;
  font-size: 13px;
  margin-top: 12px;
}

:deep(.el-radio-button__inner) {
  background: #ffffff;
  border-color: #d1d5db;
  color: #374151;
}

:deep(.el-radio-button.is-active .el-radio-button__inner) {
  background: linear-gradient(135deg, #3b82f6, #6366f1);
  border-color: transparent;
  color: #fff;
}
</style>
