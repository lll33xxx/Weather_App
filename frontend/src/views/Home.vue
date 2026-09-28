<script setup>
import {
  ref,
  computed,
  watch,
  onMounted,
  onBeforeUnmount,
  nextTick,
} from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import * as echarts from "echarts";
import { cityCoords } from "@/utils/cityCoords";
import CitySelect from '@/components/CitySelect.vue';
import { getGeoJson, CHINA_ADCODE } from "@/api/geo";
import {
  getRealtime,
  getHourly,
  getDaily,
  getWeatherExtras,
  getCitiesOverview,
} from "@/api/weather";

const router = useRouter();

// ========== 城市列表 ==========
const cityList = Object.keys(cityCoords)
  .filter(
    (name) =>
      !/省|自治区|特别行政区/.test(name) || /^北京|^上海|^天津|^重庆/.test(name)
  )
  .sort((a, b) => a.localeCompare(b, "zh-Hans-CN", { sensitivity: "base" }));

// ========== 当前位置与城市 ==========
const currentCity = ref("北京");
const currentLon = ref(116.4074);
const currentLat = ref(39.9042);
const locating = ref(false);

// ========== 天气数据 ==========
const realtime = ref(null);
const hourly = ref([]);
const daily = ref([]);
const warning = ref({ hasWarning: false, alerts: [] });
const airQuality = ref(null);
const indices = ref({ items: [] });
const citiesOverview = ref([]);
const loading = ref({
  realtime: false,
  chart: false,
  extras: false,
  map: false,
});

// ========== 实时天气动态背景 ==========
// 根据和风 icon code + 日出日落判断天气类型和昼夜
const weatherTheme = computed(() => {
  if (!realtime.value) return { type: "clear", day: true };
  const code = parseInt(realtime.value.icon) || 0;
  // 判断昼夜：优先用今天的 sunrise/sunset，没有就用当前小时
  const today = daily.value && daily.value[0];
  let isDay = true;
  const nowH = new Date().getHours();
  if (today && today.sunrise && today.sunset) {
    const now = nowH * 60 + new Date().getMinutes();
    const [srH, srM] = today.sunrise.split(":").map(Number);
    const [ssH, ssM] = today.sunset.split(":").map(Number);
    isDay = now >= srH * 60 + srM && now < ssH * 60 + ssM;
  } else {
    isDay = nowH >= 6 && nowH < 18;
  }
  // 天气分类（和风 v1 icon code）
  // 100=晴白天, 150=晴夜晚, 101-103=多云, 104=阴, 300-399=雨, 400-499=雪, 500-599=雾/霾
  let type = "clear";
  if (code === 100) type = "clear";
  else if (code === 150) type = "clear-night";
  else if (code >= 101 && code <= 103) type = "cloudy";
  else if (code === 104) type = "overcast";
  else if (code >= 300 && code <= 399) type = "rain";
  else if (code >= 400 && code <= 499) type = "snow";
  else if (code >= 500 && code <= 599) type = "fog";
  return { type, day: isDay };
});

// ========== 图表视图切换：hourly / daily ==========
const chartView = ref("hourly");
const chartRef = ref(null);
let chart = null;

// ========== 地图 ==========
const mapRef = ref(null);
let mapChart = null;
let resizeHandler = null;

// ========== 粒子动画 ==========
const particlesRef = ref(null);
let particlesAnim = null;

// ========== 轮播指示器显隐 ==========
const showIndicators = ref(false);

// ========== 当前日期 ==========
const todayStr = computed(() => {
  const d = new Date();
  const week = ["周日", "周一", "周二", "周三", "周四", "周五", "周六"][
    d.getDay()
  ];
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日 ${week}`;
});

// ========== 浏览器定位 ==========
const LOC_STORE_KEY = "nl_weather_location";

const applyCity = async (cityName) => {
  // 一键清空：静默置空，不弹错误、不重新加载
  if (!cityName) {
    currentCity.value = "";
    return;
  }
  const coord = cityCoords[cityName];
  if (!coord) {
    ElMessage.error("未找到该城市的坐标");
    return;
  }
  currentCity.value = cityName;
  currentLon.value = coord[0];
  currentLat.value = coord[1];
  await loadAll();
  // 同步当前城市在地图上的温度，保持和右上角一致
  const exist = citiesOverview.value.find((c) => c.city === cityName);
  if (exist && realtime.value) {
    exist.temp = realtime.value.temp;
    exist.text = realtime.value.text;
    exist.icon = realtime.value.icon;
  }
  if (!exist) {
    citiesOverview.value.push({
      city: cityName, lon: coord[0], lat: coord[1],
      temp: realtime.value ? realtime.value.temp : 0,
      text: realtime.value ? realtime.value.text : '',
      icon: realtime.value ? realtime.value.icon : ''
    });
  }
  renderMap();
};

const locateFromBrowser = () => {
  if (!navigator.geolocation) {
    ElMessage.warning("当前浏览器不支持定位，已使用默认城市北京");
    applyCity("北京");
    return;
  }
  locating.value = true;
  navigator.geolocation.getCurrentPosition(
    async (pos) => {
      locating.value = false;
      const { longitude, latitude } = pos.coords;
      currentLon.value = Math.round(longitude * 100) / 100;
      currentLat.value = Math.round(latitude * 100) / 100;
      localStorage.setItem(
        LOC_STORE_KEY,
        JSON.stringify({
          lon: currentLon.value,
          lat: currentLat.value,
        })
      );
      currentCity.value = nearestCity(currentLon.value, currentLat.value);
      await loadAll();
      renderMap();
    },
    (err) => {
      locating.value = false;
      let msg = "定位失败";
      if (err.code === err.PERMISSION_DENIED) {
        msg = "浏览器定位权限被拒绝，请点击地址栏左侧锁形图标允许位置访问";
      } else if (err.code === err.POSITION_UNAVAILABLE) {
        msg = "暂无法获取位置信息，请检查系统定位开关";
      } else if (err.code === err.TIMEOUT) {
        msg = "定位超时，请点击右上角定位图标重试";
      }
      ElMessage.error(msg);
      applyCity("北京");
    },
    { enableHighAccuracy: false, timeout: 8000, maximumAge: 60000 }
  );
};

const nearestCity = (lon, lat) => {
  let best = "北京";
  let bestDist = Infinity;
  for (const [name, [clon, clat]] of Object.entries(cityCoords)) {
    if (
      /省|市|自治区|特别行政区/.test(name) &&
      !/^北京|^上海|^天津|^重庆/.test(name)
    )
      continue;
    const d = (clon - lon) ** 2 + (clat - lat) ** 2;
    if (d < bestDist) {
      bestDist = d;
      best = name;
    }
  }
  return best;
};

// ========== 数据加载 ==========
const loadAll = async () => {
  await Promise.all([loadRealtime(), loadChart(), loadExtras()]);
};

const loadRealtime = async () => {
  loading.value.realtime = true;
  try {
    realtime.value = await getRealtime(currentLon.value, currentLat.value);
  } catch (e) {
    ElMessage.error(e.message || "实时天气加载失败");
  } finally {
    loading.value.realtime = false;
  }
};

const loadChart = async () => {
  loading.value.chart = true;
  try {
    // 始终加载 daily（24h 图需要 sunrise/sunset 标注）
    const tasks = [
      getDaily(currentLon.value, currentLat.value).then(
        (d) => (daily.value = d)
      ),
    ];
    if (chartView.value === "hourly") {
      tasks.push(
        getHourly(currentLon.value, currentLat.value).then(
          (h) => (hourly.value = h)
        )
      );
    }
    await Promise.all(tasks);
    await nextTick();
    renderChart();
  } catch (e) {
    ElMessage.error(e.message || "图表数据加载失败");
  } finally {
    loading.value.chart = false;
  }
};

const loadExtras = async () => {
  loading.value.extras = true;
  try {
    const data = await getWeatherExtras(currentLon.value, currentLat.value);
    warning.value = data.warning || { hasWarning: false, alerts: [] };
    airQuality.value = data.airQuality;
    indices.value = data.indices || { items: [] };
  } catch (e) {
    ElMessage.error(e.message || "附加天气信息加载失败");
  } finally {
    loading.value.extras = false;
  }
};

const loadCitiesOverview = async () => {
  loading.value.map = true;
  try {
    citiesOverview.value = await getCitiesOverview();
  } catch (e) {
    ElMessage.error(e.message || "城市概览加载失败");
  } finally {
    loading.value.map = false;
  }
};

// ========== 图表渲染 ==========
const renderChart = () => {
  if (!chartRef.value) return;
  if (!chart) chart = echarts.init(chartRef.value);
  chart.setOption(buildChartOption(), true);
};

const buildSunMarkLines = (times) => {
  const today = daily.value && daily.value[0];
  if (!today || !today.sunrise || !today.sunset) return undefined;
  const toMin = (t) => { const [h, m] = t.split(':').map(Number); return h * 60 + m };
  const findIdx = (target) => {
    let best = 0, bestDiff = Infinity;
    times.forEach((t, i) => { const diff = Math.abs(toMin(t) - target); if (diff < bestDiff) { bestDiff = diff; best = i } });
    return best;
  };
  return {
    symbol: 'none', animation: false,
    data: [
      { xAxis: findIdx(toMin(today.sunrise)), label: { formatter: '日出 ' + today.sunrise, color: '#f59e0b', fontSize: 9, position: 'end', distance: 8 }, lineStyle: { color: '#f59e0b', type: 'dashed', width: 1 } },
      { xAxis: findIdx(toMin(today.sunset)), label: { formatter: '日落 ' + today.sunset, color: '#ef4444', fontSize: 9, position: 'end', distance: 8 }, lineStyle: { color: '#ef4444', type: 'dashed', width: 1 } }
    ]
  };
}

const buildChartOption = () => {
  if (chartView.value === "hourly") {
    const times = hourly.value.map((p) => {
      const m = p.time.match(/T(\d{2}):/);
      return m ? m[1] + ':00' : p.time;
    });
    let hourlyData = hourly.value.map((p) => ({ value: p.temp }));
    const today = daily.value && daily.value[0];
    let markAreaData = [];
    let markPointData = [];
    if (today && today.sunrise && today.sunset) {
      const toMin = (t) => { const [h,m] = t.split(':').map(Number); return h*60+m };
      const sr = toMin(today.sunrise), ss = toMin(today.sunset);
      // 找整点索引
      const idxOf = (target) => { let best=0, bd=Infinity; times.forEach((t,i)=>{ const d=Math.abs(toMin(t)-target); if(d<bd){bd=d;best=i} }); return best; };
      const srIdx = idxOf(sr), ssIdx = idxOf(ss);
      // 插值日出日落温度
      const interp = (target) => {
        for (let i = 0; i < times.length-1; i++) {
          const a = toMin(times[i]), b = toMin(times[i+1]);
          if (target >= a && target <= b) {
            const ratio = (target-a)/(b-a);
            return Math.round((hourlyData[i].value + (hourlyData[i+1].value - hourlyData[i].value)*ratio)*10)/10;
          }
        }
        return hourlyData[srIdx].value;
      };
      const srTemp = interp(sr), ssTemp = interp(ss);
      // 找 target 所在的两个整点之间，插在前一个整点后面
      const insertIdx = (target) => {
        for (let i = 0; i < times.length-1; i++) {
          const a = toMin(times[i]), b = toMin(times[i+1]);
          if (target >= a && target < b) return i+1;
        }
        return times.length;
      };
      if (!times.includes(today.sunrise)) {
        const ii = insertIdx(sr);
        times.splice(ii, 0, today.sunrise);
        hourlyData.splice(ii, 0, { value: srTemp });
      }
      if (!times.includes(today.sunset)) {
        const ii = insertIdx(ss);
        times.splice(ii, 0, today.sunset);
        hourlyData.splice(ii, 0, { value: ssTemp });
      }
      // markArea: 日出→日落 白天浅暖色，其余夜晚浅冷色
      const srLabel = today.sunrise, ssLabel = today.sunset;
      const srRealIdx = times.indexOf(srLabel);
      const ssRealIdx = times.indexOf(ssLabel);
      const dayGrad = new echarts.graphic.LinearGradient(0, 0, 1, 0, [ {offset:0, color:'rgba(255,190,80,0.18)'}, {offset:0.5, color:'rgba(255,170,50,0.5)'}, {offset:1, color:'rgba(255,190,80,0.18)'} ]);
      const nightGrad = new echarts.graphic.LinearGradient(0, 0, 1, 0, [ {offset:0, color:'rgba(35,50,110,0.2)'}, {offset:0.5, color:'rgba(35,50,110,0.55)'}, {offset:1, color:'rgba(35,50,110,0.2)'} ]);
      markAreaData = [
        [{ xAxis: srRealIdx, itemStyle: { color: dayGrad } }, { xAxis: ssRealIdx }],
        [{ xAxis: 0, itemStyle: { color: nightGrad } }, { xAxis: srRealIdx }],
        [{ xAxis: ssRealIdx, itemStyle: { color: nightGrad } }, { xAxis: times.length-1 }]
      ];
      markPointData = [
        { coord: [srRealIdx, srTemp], value: '日出', symbolSize: 34, itemStyle: { color: '#f59e0b' }, label: { formatter: '日出', color: '#fff', fontSize: 9 }, tooltip: { formatter: '日出 ' + today.sunrise } },
        { coord: [ssRealIdx, ssTemp], value: '日落', symbolSize: 34, itemStyle: { color: '#ef4444' }, label: { formatter: '日落', color: '#fff', fontSize: 9 }, tooltip: { formatter: '日落 ' + today.sunset } }
      ];
    }
    return {
      grid: { left: 36, right: 16, top: 36, bottom: 24 },
      tooltip: { trigger: 'axis', formatter: (params) => { const i = params[0].dataIndex; const item = hourly.value[Math.min(i, hourly.value.length-1)]; if (!item) return params[0].name; return params[0].name + '<br/>温度: ' + params[0].value + '°C<br/>天气: ' + (item.text||'') + '<br/>降水概率: ' + (item.precipProb||0) + '%'; } },
      xAxis: { type: 'category', data: times, axisLine: { lineStyle: { color: '#e5e7eb' } }, axisLabel: { color: '#6b7280', fontSize: 10 } },
      yAxis: { type: 'value', name: '°C', splitLine: { lineStyle: { color: '#f3f4f6' } }, axisLabel: { color: '#6b7280', fontSize: 10 } },
      series: [{
        name: '温度', type: 'line', smooth: true, data: hourlyData,
        symbol: 'circle', symbolSize: 5,
        lineStyle: { width: 2.5, color: '#3b82f6' },
        itemStyle: { color: '#3b82f6' },
        markArea: markAreaData.length ? { silent: true, data: markAreaData } : undefined,
        markLine: buildSunMarkLines(times),
      }]
    };
  } else {
    const days = daily.value.map((d) => d.week || d.date);
    const maxTemps = daily.value.map((d) => d.tempMax);
    const minTemps = daily.value.map((d) => d.tempMin);
    return {
      grid: { left: 36, right: 16, top: 36, bottom: 36 },
      tooltip: {
        trigger: "axis",
        formatter: (params) => {
          const item = daily.value[params[0].dataIndex];
          return `${item.week || item.date}<br/>白天: ${item.dayText} ${
            item.tempMax
          }°C<br/>夜间: ${item.nightText} ${item.tempMin}°C`;
        },
      },
      legend: {
        data: ["最高温", "最低温"],
        top: 0,
        textStyle: { color: "#6b7280", fontSize: 10 },
      },
      xAxis: {
        type: "category",
        data: days,
        boundaryGap: false,
        axisLine: { lineStyle: { color: "#e5e7eb" } },
        axisLabel: {
          color: "#6b7280",
          fontSize: 10,
          lineHeight: 14,
          formatter: (value, index) => {
            const item = daily.value[index];
            if (!item) return value;
            return `{a|${value}}\n{b|${item.dayText}}`;
          },
          rich: {
            a: {
              color: "#1f2937",
              fontSize: 11,
              fontWeight: 600,
              lineHeight: 14,
            },
            b: { color: "#6b7280", fontSize: 9, lineHeight: 12 },
          },
        },
      },
      yAxis: {
        type: "value",
        name: "°C",
        splitLine: { lineStyle: { color: "#f3f4f6" } },
        axisLabel: { color: "#6b7280", fontSize: 10 },
      },
      series: [
        {
          name: "最高温",
          type: "line",
          smooth: true,
          data: maxTemps,
          symbol: "circle",
          symbolSize: 5,
          lineStyle: { width: 2.5, color: "#f97316" },
          itemStyle: { color: "#f97316" },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: "rgba(249,115,22,0.25)" },
              { offset: 1, color: "rgba(249,115,22,0.02)" },
            ]),
          },
        },
        {
          name: "最低温",
          type: "line",
          smooth: true,
          data: minTemps,
          symbol: "circle",
          symbolSize: 5,
          lineStyle: { width: 2.5, color: "#3b82f6" },
          itemStyle: { color: "#3b82f6" },
        },
      ],
    };
  }
};

watch(chartView, () => loadChart());

// ========== 地图渲染 ==========
const renderMap = async () => {
  if (!mapRef.value) return;
  if (!mapChart) mapChart = echarts.init(mapRef.value);
  try {
    const geoJson = await getGeoJson(CHINA_ADCODE, true);
    echarts.registerMap("china-weather", geoJson);
  } catch (e) {
    console.error("地图加载失败", e);
  }

  // 散点数据：[lon, lat, temp, city, text]
  const scatterData = citiesOverview.value.map((c) => ({
    name: c.city,
    value: [c.lon, c.lat, Math.round(c.temp), c.text],
    itemStyle: {
      color: c.city === currentCity.value ? "#ef4444" : "#3b82f6",
    },
    symbolSize: c.city === currentCity.value ? 22 : 16,
  }));

  mapChart.setOption(
    {
      tooltip: {
        trigger: "item",
        formatter: (p) => {
          if (p.seriesType === "scatter") {
            return `<b>${p.name}</b><br/>${p.value[3]} ${p.value[2]}°C`;
          }
          return p.name;
        },
      },
      geo: {
        map: "china-weather",
        roam: true,
        zoom: 1.2,
        center: [105, 36],
        itemStyle: {
          areaColor: "#f1f5f9",
          borderColor: "#cbd5e1",
        },
        emphasis: {
          itemStyle: { areaColor: "#e0e7ff" },
          label: { show: false },
        },
        label: { show: false },
      },
      series: [
        {
          name: "城市天气",
          type: "scatter",
          coordinateSystem: "geo",
          data: scatterData,
          label: {
            show: true,
            position: "right",
            formatter: (p) => `${p.name} ${p.value[2]}°`,
            fontSize: 10,
            color: "#1f2937",
          },
        },
      ],
    },
    true
  );

  // 点击城市切换
  mapChart.off("click");
  mapChart.on("click", (params) => {
    if (params.seriesType === "scatter" && params.name) {
      applyCity(params.name);
    }
  });
};

// ========== 模块介绍区 ==========
const modules = [
  {
    title: "气象数据可视化",
    desc: "基于 2000-2020 年全国城市历史气象数据，支持气温、降水、风速、日照、湿度等多指标的年度排名与历年趋势对比分析。",
    tags: ["历史排名", "趋势对比"],
    path: "/weather",
    icon: "📊",
  },
  {
    title: "地图可视化看板",
    desc: "在全国地图上直观呈现各城市历年气象指标的空间分布，支持省份下钻与热力图渲染，一眼掌握区域差异。",
    tags: ["三级下钻", "热力图"],
    path: "/geo",
    icon: "🗺️",
  },
  {
    title: "天气智能助手",
    desc: "基于本地大模型的智能问答，支持天气数据解读、城市推荐与气象常识咨询，让数据分析更自然。",
    tags: ["智能问答", "城市推荐"],
    path: "/ai",
    icon: "🤖",
  },
];

const goModule = (m) => {
  if (m.path) router.push(m.path);
};

// ========== 预警颜色 ==========
const warningColorClass = (code) => {
  const map = {
    red: "w-red",
    orange: "w-orange",
    amber: "w-orange",
    yellow: "w-yellow",
    blue: "w-blue",
  };
  return map[(code || "").toLowerCase()] || "w-blue";
};
const warningLevelText = (sev) => {
  const map = {
    minor: "一般",
    moderate: "较重",
    severe: "严重",
    extreme: "特别严重",
  };
  return map[sev] || "一般";
};

// ========== 生命周期 ==========
onMounted(async () => {
  const cached = localStorage.getItem(LOC_STORE_KEY);
  if (cached) {
    try {
      const { lon, lat } = JSON.parse(cached);
      currentLon.value = lon;
      currentLat.value = lat;
      currentCity.value = nearestCity(lon, lat);
      await loadAll();
    } catch (e) {
      locateFromBrowser();
    }
  } else {
    locateFromBrowser();
  }
  await loadCitiesOverview();
  await nextTick();
  renderMap();

  resizeHandler = () => {
    if (chart) chart.resize();
    if (mapChart) mapChart.resize();
    resizeParticles();
  };
  window.addEventListener("resize", resizeHandler);

  // 初始化粒子
  await nextTick();
  startParticles();
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", resizeHandler);
  if (chart) {
    chart.dispose();
    chart = null;
  }
  if (mapChart) {
    mapChart.dispose();
    mapChart = null;
  }
  stopParticles();
});

// ========== 粒子动画 ==========
const stopParticles = () => {
  if (particlesAnim) {
    cancelAnimationFrame(particlesAnim);
    particlesAnim = null;
  }
};
const resizeParticles = () => {
  const canvas = particlesRef.value;
  if (!canvas) return;
  const card = canvas.parentElement;
  canvas.width = card.clientWidth;
  canvas.height = card.clientHeight;
};
const startParticles = () => {
  stopParticles();
  const canvas = particlesRef.value;
  if (!canvas) return;
  const ctx = canvas.getContext('2d');
  const card = canvas.parentElement;
  canvas.width = card.clientWidth;
  canvas.height = card.clientHeight;
  const theme = weatherTheme.value;
  if (theme.type !== 'rain' && theme.type !== 'snow' && theme.type !== 'clear-night' && theme.type !== 'cloudy' && theme.type !== 'clear') return;
  const W = canvas.width, H = canvas.height;
  const particles = [];
  if (theme.type === 'rain') {
    for (let i = 0; i < 60; i++) particles.push({ x: Math.random()*W, y: Math.random()*H, len: 10+Math.random()*15, speed: 8+Math.random()*6 });
  } else if (theme.type === 'snow') {
    for (let i = 0; i < 40; i++) particles.push({ x: Math.random()*W, y: Math.random()*H, r: 1.5+Math.random()*2.5, speed: 1+Math.random()*1.5, drift: (Math.random()-0.5)*0.5 });
  } else if (theme.type === 'cloudy') {
    for (let i = 0; i < 5; i++) particles.push({ x: Math.random()*W, y: 20+Math.random()*(H/2), w: 60+Math.random()*80, h: 18+Math.random()*12, speed: 0.2+Math.random()*0.3, alpha: 0.15+Math.random()*0.15 });
  } else {
    for (let i = 0; i < 50; i++) particles.push({ x: Math.random()*W, y: Math.random()*H, r: 0.5+Math.random()*1.5, twinkle: Math.random()*Math.PI*2 });
  }
  const draw = () => {
    ctx.clearRect(0, 0, W, H);
    if (theme.type === 'rain') {
      ctx.strokeStyle = 'rgba(174,194,224,0.6)'; ctx.lineWidth = 1.2;
      particles.forEach(p => { ctx.beginPath(); ctx.moveTo(p.x,p.y); ctx.lineTo(p.x-1,p.y+p.len); ctx.stroke(); p.y += p.speed; if (p.y > H) { p.y = -p.len; p.x = Math.random()*W; } });
    } else if (theme.type === 'snow') {
      ctx.fillStyle = 'rgba(255,255,255,0.85)';
      particles.forEach(p => { ctx.beginPath(); ctx.arc(p.x,p.y,p.r,0,Math.PI*2); ctx.fill(); p.y += p.speed; p.x += p.drift; if (p.y > H) { p.y = -p.r; p.x = Math.random()*W; } });
    } else if (theme.type === 'cloudy') {
      particles.forEach(p => {
        ctx.fillStyle = 'rgba(255,255,255,' + p.alpha + ')';
        ctx.beginPath();
        ctx.ellipse(p.x, p.y, p.w*0.5, p.h*0.5, 0, 0, Math.PI*2);
        ctx.ellipse(p.x - p.w*0.25, p.y - p.h*0.3, p.w*0.3, p.h*0.35, 0, 0, Math.PI*2);
        ctx.ellipse(p.x + p.w*0.2, p.y - p.h*0.25, p.w*0.25, p.h*0.3, 0, 0, Math.PI*2);
        ctx.fill();
        p.x += p.speed;
        if (p.x - p.w > W) { p.x = -p.w; p.y = 20+Math.random()*(H/2); }
      });
    } else if (theme.type === 'clear') {
      const sx = W*0.15, sy = H*0.15, sr = 24;
      const grad = ctx.createRadialGradient(sx, sy, 0, sx, sy, sr*2.5);
      grad.addColorStop(0, 'rgba(255,240,180,0.9)');
      grad.addColorStop(0.5, 'rgba(255,220,100,0.35)');
      grad.addColorStop(1, 'rgba(255,200,80,0)');
      ctx.fillStyle = grad;
      ctx.beginPath(); ctx.arc(sx, sy, sr*2.5, 0, Math.PI*2); ctx.fill();
      const beam = ctx.createLinearGradient(sx, sy, W*0.6, H*0.8);
      beam.addColorStop(0, 'rgba(255,240,180,0.22)');
      beam.addColorStop(1, 'rgba(255,240,180,0)');
      ctx.fillStyle = beam;
      ctx.beginPath();
      ctx.moveTo(sx, sy);
      ctx.lineTo(W*0.55, H*0.95);
      ctx.lineTo(W*0.85, H*0.5);
      ctx.closePath(); ctx.fill();
    } else {
      const t = Date.now() / 600;
      particles.forEach(p => { const a = 0.4+0.6*Math.abs(Math.sin(t+p.twinkle)); ctx.fillStyle = 'rgba(255,255,220,' + a + ')'; ctx.beginPath(); ctx.arc(p.x,p.y,p.r,0,Math.PI*2); ctx.fill(); });
    }
    particlesAnim = requestAnimationFrame(draw);
  };
  draw();
};

// 天气切换时重启粒子
watch(weatherTheme, () => {
  nextTick(startParticles);
});
</script>

<template>
  <div class="home-page">
    <!-- ========== 首屏 ========== -->
    <div class="weather-main">
      <!-- 左侧 2/3：全国天气地图 -->
      <div class="map-panel">
        <div class="panel-header">
          <div class="panel-title">全国主要城市实时天气</div>
          <div class="panel-subtitle">点击城市查看详情 · 拖动缩放地图</div>
        </div>
        <div v-loading="loading.map" ref="mapRef" class="map-body"></div>
      </div>

      <!-- 右侧 1/3：上中下三块 -->
      <div class="weather-side">
        <!-- 上：实时天气 -->
        <div
          class="now-card"
          :class="[
            'theme-' + weatherTheme.type,
            weatherTheme.day ? 'is-day' : 'is-night',
          ]"
        >
          <canvas ref="particlesRef" class="now-particles"></canvas>
          <div class="now-top">
            <CitySelect
              v-model="currentCity"
              :cities="cityList"
              placeholder="选择城市"
              clearable
              style="flex: 1"
              @change="applyCity"
            />
            <button
              class="relocate-btn"
              :disabled="locating"
              title="重新获取定位"
              @click="locateFromBrowser"
            >
              <span v-if="locating" class="spin">⟳</span>
              <span v-else>⦿</span>
            </button>
          </div>
          <div class="now-date">{{ todayStr }}</div>
          <div v-loading="loading.realtime" class="now-body">
            <template v-if="realtime">
              <div class="now-temp-row">
                <span class="now-temp">{{ Math.round(realtime.temp) }}</span>
                <span class="now-unit">°C</span>
                <span class="now-text">{{ realtime.text }}</span>
              </div>
              <div class="now-grid">
                <div class="now-item">
                  <div class="now-item-label">体感</div>
                  <div class="now-item-value">
                    {{ Math.round(realtime.feelsLike) }}°
                  </div>
                </div>
                <div class="now-item">
                  <div class="now-item-label">湿度</div>
                  <div class="now-item-value">{{ realtime.humidity }}%</div>
                </div>
                <div class="now-item">
                  <div class="now-item-label">风向</div>
                  <div class="now-item-value">{{ realtime.windDir }}</div>
                </div>
                <div class="now-item">
                  <div class="now-item-label">风速</div>
                  <div class="now-item-value">
                    {{ realtime.windSpeed }} km/h
                  </div>
                </div>
              </div>
            </template>
          </div>
        </div>

        <!-- 中：24h/7d 图表 -->
        <div class="chart-card">
          <div class="chart-card-header">
            <span>{{ currentCity }} · 温度趋势</span>
            <el-select v-model="chartView" size="small" style="width: 110px">
              <el-option label="24小时" value="hourly" />
              <el-option label="7天" value="daily" />
            </el-select>
          </div>
          <div
            v-loading="loading.chart"
            ref="chartRef"
            class="chart-body"
          ></div>
        </div>

        <!-- 下：预警 + 空气 + 指数 轮播 -->
        <div
          class="extras-carousel"
          :class="{ 'is-hover': showIndicators }"
          @mouseenter="showIndicators = true"
          @mouseleave="showIndicators = false"
        >
          <el-carousel
            height="100%"
            :interval="6000"
            arrow="never"
            indicator-position="outside"
          >
            <!-- 预警 -->
            <el-carousel-item>
              <div class="extra-page">
                <div class="extra-page-title">天气预警</div>
                <div v-if="warning.hasWarning" class="warn-list">
                  <div
                    v-for="(w, i) in warning.alerts"
                    :key="i"
                    class="warn-item"
                    :class="warningColorClass(w.colorCode)"
                  >
                    <div class="warn-head">
                      <span class="warn-level">{{
                        warningLevelText(w.severity)
                      }}</span>
                      <span class="warn-type">{{ w.eventType }}</span>
                    </div>
                    <div class="warn-desc">{{ w.headline }}</div>
                  </div>
                </div>
                <div v-else class="extra-page-empty">
                  <span class="ok-icon">✓</span> 当前无生效天气预警
                </div>
              </div>
            </el-carousel-item>

            <!-- 空气 -->
            <el-carousel-item>
              <div class="extra-page">
                <div class="extra-page-title">空气质量</div>
                <div v-if="airQuality" class="air-body">
                  <div class="air-hero">
                    <div
                      class="air-aqi"
                      :style="{ background: airQuality.color }"
                    >
                      <span class="air-aqi-num">{{
                        airQuality.aqiDisplay
                      }}</span>
                      <span class="air-aqi-label">AQI</span>
                    </div>
                    <div class="air-hero-meta">
                      <div class="air-cat">{{ airQuality.category }}</div>
                      <div class="air-pollutant">
                        首要污染物 · {{ airQuality.primaryPollutant || "—" }}
                      </div>
                    </div>
                  </div>
                  <div class="air-grid">
                    <div class="air-cell">
                      <span class="air-cell-label">PM2.5</span>
                      <b class="air-cell-val">{{ airQuality.pm25 ?? "—" }}</b>
                    </div>
                    <div class="air-cell">
                      <span class="air-cell-label">PM10</span>
                      <b class="air-cell-val">{{ airQuality.pm10 ?? "—" }}</b>
                    </div>
                    <div class="air-cell">
                      <span class="air-cell-label">CO</span>
                      <b class="air-cell-val">{{ airQuality.co ?? "—" }}</b>
                    </div>
                  </div>
                </div>
                <div v-else class="extra-page-empty">暂无空气质量数据</div>
              </div>
            </el-carousel-item>

            <!-- 指数 -->
            <el-carousel-item>
              <div class="extra-page">
                <div class="extra-page-title">生活指数</div>
                <div
                  v-if="indices.items && indices.items.length"
                  class="index-list"
                >
                  <div
                    v-for="(it, i) in indices.items"
                    :key="i"
                    class="index-item"
                  >
                    <div class="index-left">
                      <div class="index-name">{{ it.name }}</div>
                      <div class="index-cat">{{ it.category }}</div>
                    </div>
                    <div class="index-text">{{ it.text }}</div>
                  </div>
                </div>
                <div v-else class="extra-page-empty">暂无指数数据</div>
              </div>
            </el-carousel-item>
          </el-carousel>
        </div>
      </div>
    </div>

    <!-- ========== 下方模块介绍 ========== -->
    <div class="modules-section">
      <h2 class="modules-title">平台功能</h2>
      <div class="modules-grid">
        <div
          v-for="m in modules"
          :key="m.path"
          class="module-card"
          @click="goModule(m)"
        >
          <div class="module-icon">{{ m.icon }}</div>
          <h3 class="module-title">{{ m.title }}</h3>
          <p class="module-desc">{{ m.desc }}</p>
          <div class="module-tags">
            <span v-for="t in m.tags" :key="t" class="module-tag">{{ t }}</span>
          </div>
          <div class="module-arrow">进入 →</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.home-page {
  padding: 20px 32px 40px;
}

.weather-main {
  display: flex;
  gap: 20px;
  align-items: stretch;
  height: calc(100vh - 110px);
  min-height: 620px;
}

/* ========== 左侧地图 ========== */
.map-panel {
  flex: 2;
  background: #fff;
  border: 1px solid #e5e7eb;
  /* border-radius: 5px; */
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  /* box-shadow: 0 1px 3px rgba(0,0,0,0.04); */
  min-height: 600px;
}
.panel-header {
  margin-bottom: 8px;
}
.panel-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
}
.panel-subtitle {
  font-size: 12px;
  color: #9ca3af;
  margin-top: 2px;
}
.map-body {
  flex: 1;
  min-height: 560px;
}

/* ========== 右侧 ========== */
.weather-side {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 320px;
}

/* 实时天气 */
.now-card {
  position: relative;
  border-radius: 6px;
  padding: 14px 18px;
  color: #fff;
  overflow: hidden;
  transition: background 0.8s ease;
  /* box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15); */
}
.now-particles {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 1;
}
.now-card > *:not(.now-particles) {
  position: relative;
  z-index: 2;
}

/* 晴 白天 */
.now-card.theme-clear.is-day {
  background: linear-gradient(135deg, #4facfe 0%, #f6d365 100%);
}
/* 晴 夜晚 */
.now-card.theme-clear.is-night,
.now-card.theme-clear-night.is-day,
.now-card.theme-clear-night.is-night {
  background: linear-gradient(135deg, #1e3a8a 0%, #312e81 100%);
}
/* 多云 */
.now-card.theme-cloudy.is-day {
  background: linear-gradient(135deg, #7ec8e3 0%, #b8dce8 100%);
}
.now-card.theme-cloudy.is-night {
  background: linear-gradient(135deg, #2d4a7a 0%, #1e293b 100%);
}
/* 阴 */
.now-card.theme-overcast.is-day {
  background: linear-gradient(135deg, #9ca3af 0%, #6b7280 100%);
}
.now-card.theme-overcast.is-night {
  background: linear-gradient(135deg, #374151 0%, #1f2937 100%);
}
/* 雨 */
.now-card.theme-rain.is-day {
  background: linear-gradient(135deg, #4a5568 0%, #2d3748 100%);
}
.now-card.theme-rain.is-night {
  background: linear-gradient(135deg, #1e293b 0%, #0f172a 100%);
}
/* 雪 */
.now-card.theme-snow.is-day {
  background: linear-gradient(135deg, #94a3b8 0%, #cbd5e1 100%);
}
.now-card.theme-snow.is-night {
  background: linear-gradient(135deg, #334155 0%, #1e293b 100%);
}
/* 雾 */
.now-card.theme-fog.is-day {
  background: linear-gradient(135deg, #b0b8c1 0%, #868e96 100%);
}
.now-card.theme-fog.is-night {
  background: linear-gradient(135deg, #4b5563 0%, #374151 100%);
}
.now-top {
  display: flex;
  align-items: center;
  gap: 8px;
}
.relocate-btn {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: none;
  background: rgba(255, 255, 255, 0.2);
  color: #fff;
  font-size: 16px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}
.relocate-btn:hover {
  background: rgba(255, 255, 255, 0.35);
}
.relocate-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.relocate-btn .spin {
  display: inline-block;
  animation: spin 1s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
.now-date {
  font-size: 12px;
  opacity: 0.85;
  margin: 8px 0 4px;
}
.now-temp-row {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin: 4px 0 10px;
}
.now-temp {
  font-size: 48px;
  font-weight: 300;
  line-height: 1;
}
.now-unit {
  font-size: 16px;
  opacity: 0.9;
}
.now-text {
  font-size: 15px;
  margin-left: 6px;
}
.now-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 6px;
  padding-top: 8px;
  border-top: 1px solid rgba(255, 255, 255, 0.2);
}
.now-item {
  text-align: center;
}
.now-item-label {
  font-size: 10px;
  opacity: 0.8;
}
.now-item-value {
  font-size: 13px;
  font-weight: 500;
  margin-top: 2px;
}
.now-top :deep(.el-select__wrapper) {
  background: rgba(255, 255, 255, 0.18) !important;
  
  color: #fff;
}
.now-top :deep(.el-select__selected-item) {
  color: #fff !important;
}
.now-top :deep(.el-select__caret) {
  color: #fff !important;
}

/* 图表卡片 */
.chart-card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 12px 16px;
  /* box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04); */
  display: flex;
  flex-direction: column;
  min-height: 260px;
}
.chart-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 6px;
}
.chart-body {
  flex: 1;
  min-height: 220px;
}

/* 下：轮播 */
.extras-carousel {
  flex: 1;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 10px 14px 22px;
  /* box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04); */
  min-height: 180px;
  position: relative;
}
.extras-carousel :deep(.el-carousel) {
  height: 100%;
}
.extras-carousel :deep(.el-carousel__container) {
  height: 100%;
}

/* 自定义指示器：隐藏长条，hover 时显示，当前项高亮 */
.extras-carousel :deep(.el-carousel__indicators) {
  position: absolute !important;
  bottom: 6px !important;
  left: 0 !important;
  right: 0 !important;
  padding: 0 !important;
  margin: 0 !important;
  height: 6px !important;
  display: flex !important;
  justify-content: center !important;
  align-items: center !important;
  list-style: none !important;
  opacity: 0 !important;
  visibility: hidden;
  transition: opacity 0.25s, visibility 0.25s;
  z-index: 20;
}
.extras-carousel.is-hover :deep(.el-carousel__indicators) {
  opacity: 1 !important;
  visibility: visible;
}
.extras-carousel :deep(.el-carousel__indicator) {
  width: auto;
  padding: 0 3px;
  height: 6px;
}
.extras-carousel :deep(.el-carousel__button) {
  width: 18px;
  height: 4px;
  border-radius: 2px;
  background: #d1d5db;
  opacity: 1;
  transition: all 0.25s;
}
.extras-carousel :deep(.el-carousel__indicator.is-active .el-carousel__button) {
  width: 32px;
  background: #3b82f6;
}

.extra-page {
  padding: 4px 2px 16px;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}
.extra-page-title {
  font-size: 13px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 8px;
}
.extra-page-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #9ca3af;
  font-size: 13px;
  gap: 6px;
}
.ok-icon {
  display: inline-flex;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #10b981;
  color: #fff;
  align-items: center;
  justify-content: center;
  font-size: 12px;
}

/* 预警 */
.warn-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.warn-item {
  border-left: 3px solid;
  padding: 8px 10px;
  border-radius: 6px;
}
.warn-item.w-red {
  border-color: #ef4444;
  background: #fef2f2;
}
.warn-item.w-orange {
  border-color: #f97316;
  background: #fff7ed;
}
.warn-item.w-yellow {
  border-color: #eab308;
  background: #fefce8;
}
.warn-item.w-blue {
  border-color: #3b82f6;
  background: #eff6ff;
}
.warn-head {
  display: flex;
  gap: 8px;
  margin-bottom: 4px;
}
.warn-level {
  font-size: 12px;
  font-weight: 600;
  color: #374151;
}
.warn-type {
  font-size: 12px;
  color: #6b7280;
}
.warn-desc {
  font-size: 12px;
  color: #4b5563;
  line-height: 1.5;
}

/* 空气 */
.air-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
  justify-content: center;
}
.air-hero {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 12px;
  background: #f9fafb;
  border-radius: 4px;
}
.air-aqi {
  width: 60px;
  height: 60px;
  border-radius: 4px;
  color: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  /* box-shadow: 0 4px 10px rgba(0, 0, 0, 0.08); */
}
.air-aqi-num {
  font-size: 20px;
  font-weight: 700;
  line-height: 1;
}
.air-aqi-label {
  font-size: 10px;
  opacity: 0.9;
  margin-top: 2px;
}
.air-hero-meta {
  flex: 1;
}
.air-cat {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
}
.air-pollutant {
  font-size: 12px;
  color: #6b7280;
  margin-top: 4px;
}
.air-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}
.air-cell {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.air-cell-label {
  font-size: 11px;
  color: #9ca3af;
}
.air-cell-val {
  font-size: 16px;
  color: #1f2937;
  margin-top: 2px;
}

/* 指数 */
.index-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.index-item {
  display: flex;
  gap: 10px;
  padding: 8px 10px;
  background: #f9fafb;
  border-radius: 4px;
}
.index-left {
  min-width: 70px;
}
.index-name {
  font-size: 13px;
  font-weight: 600;
  color: #1f2937;
}
.index-cat {
  display: inline-block;
  margin-top: 2px;
  font-size: 11px;
  color: #059669;
  background: #d1fae5;
  padding: 1px 6px;
  border-radius: 4px;
}
.index-text {
  font-size: 12px;
  color: #6b7280;
  line-height: 1.5;
  flex: 1;
}

/* ========== 下方模块介绍 ========== */
.modules-section {
  margin-top: 28px;
}
.modules-title {
  font-size: 20px;
  font-weight: 700;
  color: #1f2937;
  margin-bottom: 18px;
}
.modules-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 20px;
}
.module-card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 24px;
  cursor: pointer;
  transition: all 0.3s;
  /* box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04); */
  position: relative;
  overflow: hidden;
}
.module-card::before {
  content: "";
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, #3b82f6, #6366f1);
  opacity: 0;
  transition: opacity 0.3s;
}
.module-card:hover {
  transform: translateY(-4px);
  border-color: #bfdbfe;
  /* box-shadow: 0 12px 32px rgba(59, 130, 246, 0.12); */
}
.module-card:hover::before {
  opacity: 1;
}
.module-icon {
  font-size: 32px;
  margin-bottom: 12px;
}
.module-title {
  font-size: 17px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 8px;
}
.module-desc {
  font-size: 13px;
  color: #6b7280;
  line-height: 1.6;
  margin-bottom: 14px;
}
.module-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 12px;
}
.module-tag {
  padding: 3px 10px;
  background: #eff6ff;
  border-radius: 4px;
  font-size: 11px;
  color: #3b82f6;
}
.module-arrow {
  font-size: 13px;
  font-weight: 500;
  color: #3b82f6;
}

@media (max-width: 1200px) {
  .home-page {
    padding: 16px 20px 32px;
  }
  .weather-main {
    flex-direction: column;
    height: auto !important;
    min-height: 0;
    gap: 16px;
  }
  .map-panel {
    min-height: 380px;
    padding: 12px 14px;
  }
  .map-body {
    min-height: 340px;
  }
  .weather-side {
    min-width: 0;
  }
  .now-card {
    padding: 12px 14px;
  }
  .now-temp {
    font-size: 36px;
  }
  .now-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 8px;
  }
  .chart-card {
    min-height: 220px;
  }
  .chart-body {
    min-height: 180px;
  }
  .extras-carousel {
    min-height: 200px;
    padding: 10px 12px 22px;
  }
  .modules-section {
    margin-top: 20px;
  }
  .modules-grid {
    gap: 14px;
  }
  .module-card {
    padding: 18px;
  }
}
</style>
