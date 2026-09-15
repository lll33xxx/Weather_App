package com.example.nl.service.impl;

import com.example.nl.config.WeatherProperties;
import com.example.nl.enums.WindDirectionEnum;
import com.example.nl.service.RealtimeWeatherService;
import com.example.nl.vo.AirQualityVO;
import com.example.nl.vo.CityOverviewVO;
import com.example.nl.vo.DailyForecastVO;
import com.example.nl.vo.HourlyPointVO;
import com.example.nl.vo.IndicesVO;
import com.example.nl.vo.RealtimeWeatherVO;
import com.example.nl.vo.WarningVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 实时天气业务层实现
 *
 * 数据来源：和风天气 QWeather
 * - v1 实时/24h/7d/预警/空气：路径参数经纬度，Header 认证
 * - v7 生活指数：query 参数 location=lon,lat
 *
 * 缓存：RedisTemplate，key 前缀 weather:realtime:* / weather:hourly:* 等
 *
 * 【字段换算注意】
 * - v1 humidity 返回 0~1 小数，需 *100 转百分比
 * - v1 wind.speed 单位 m/s，转 km/h 乘 3.6
 * - v1 wind.direction.compass 是英文缩写，用 WindDirectionEnum 转中文
 */
@Slf4j
@Service
public class RealtimeWeatherServiceImpl implements RealtimeWeatherService {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private WeatherProperties weatherProperties;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 生活指数类型：3=穿衣，5=紫外线，1=运动，9=感冒（和风 v7 indices type 编号，见官方文档） */
    private static final String INDICES_TYPES = "3,5,1,9";

    @Override
    public RealtimeWeatherVO getRealtime(double lon, double lat) {
        String cacheKey = cacheKey("realtime", lon, lat);
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof RealtimeWeatherVO vo) {
            return vo;
        }

        String url = buildUrl("/weather/v1/current/", lon, lat) + "?lang=zh&localTime=true";
        JsonNode root = getForJson(url);
        RealtimeWeatherVO vo = parseRealtime(root);
        redisTemplate.opsForValue().set(cacheKey, vo,
                weatherProperties.getCacheTtlSeconds(), TimeUnit.SECONDS);
        return vo;
    }

    @Override
    public List<HourlyPointVO> getHourly(double lon, double lat) {
        String cacheKey = cacheKey("hourly", lon, lat);
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof HourlyPointVO) {
            @SuppressWarnings("unchecked")
            List<HourlyPointVO> typed = (List<HourlyPointVO>) list;
            return typed;
        }

        String url = buildUrl("/weather/v1/hourly/", lon, lat)
                + "?hours=24&lang=zh&localTime=true";
        JsonNode root = getForJson(url);
        List<HourlyPointVO> result = parseHourly(root);
        redisTemplate.opsForValue().set(cacheKey, result,
                weatherProperties.getCacheTtlSeconds(), TimeUnit.SECONDS);
        return result;
    }

    @Override
    public List<DailyForecastVO> getDaily(double lon, double lat) {
        String cacheKey = cacheKey("daily", lon, lat);
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof DailyForecastVO) {
            @SuppressWarnings("unchecked")
            List<DailyForecastVO> typed = (List<DailyForecastVO>) list;
            return typed;
        }

        String url = buildUrl("/weather/v1/daily/", lon, lat)
                + "?days=7&lang=zh&localTime=true";
        JsonNode root = getForJson(url);
        List<DailyForecastVO> result = parseDaily(root);
        redisTemplate.opsForValue().set(cacheKey, result,
                weatherProperties.getCacheTtlSeconds(), TimeUnit.SECONDS);
        return result;
    }

    @Override
    public WarningVO getWarning(double lon, double lat) {
        String cacheKey = cacheKey("warning", lon, lat);
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof WarningVO vo) {
            return vo;
        }

        WarningVO vo;
        try {
            String url = buildUrl("/weatheralert/v1/current/", lon, lat)
                    + "?lang=zh&localTime=true";
            JsonNode root = getForJson(url);
            vo = parseWarning(root);
        } catch (Exception e) {
            // 预警接口异常不阻断首页，返回空预警
            log.warn("查询天气预警失败: {}", e.getMessage());
            vo = new WarningVO();
            vo.setHasWarning(false);
            vo.setAlerts(new ArrayList<>());
        }
        redisTemplate.opsForValue().set(cacheKey, vo,
                weatherProperties.getAlertCacheTtlSeconds(), TimeUnit.SECONDS);
        return vo;
    }

    @Override
    public AirQualityVO getAirQuality(double lon, double lat) {
        String cacheKey = cacheKey("air", lon, lat);
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof AirQualityVO vo) {
            return vo;
        }

        AirQualityVO vo;
        try {
            String url = buildUrl("/airquality/v1/current/", lon, lat) + "?lang=zh";
            JsonNode root = getForJson(url);
            vo = parseAirQuality(root);
        } catch (Exception e) {
            log.warn("查询空气质量失败: {}", e.getMessage());
            vo = null;
        }
        if (vo != null) {
            redisTemplate.opsForValue().set(cacheKey, vo,
                    weatherProperties.getCacheTtlSeconds(), TimeUnit.SECONDS);
        }
        return vo;
    }

    @Override
    public IndicesVO getIndices(double lon, double lat) {
        String cacheKey = cacheKey("indices", lon, lat);
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof IndicesVO vo) {
            return vo;
        }

        IndicesVO vo;
        try {
            // v7 生活指数接口：location=经度,纬度
            String location = String.format("%.2f,%.2f", lon, lat);
            String url = weatherProperties.getHost()
                    + "/v7/indices/1d?type=" + INDICES_TYPES
                    + "&location=" + location + "&lang=zh";
            JsonNode root = getForJson(url);
            vo = parseIndices(root);
        } catch (Exception e) {
            log.warn("查询生活指数失败: {}", e.getMessage());
            vo = new IndicesVO();
            vo.setItems(new ArrayList<>());
        }
        redisTemplate.opsForValue().set(cacheKey, vo,
                weatherProperties.getAlertCacheTtlSeconds(), TimeUnit.SECONDS);
        return vo;
    }

    // ==================== 私有工具方法 ====================

    /** 拼接 v1 接口 URL：{host}{path}{lat}/{lon} */
    private String buildUrl(String pathPrefix, double lon, double lat) {
        return String.format("%s%s%.2f/%.2f",
                weatherProperties.getHost(), pathPrefix, lat, lon);
    }

    /** 统一带认证头发起 GET 请求并解析为 JsonNode
     *  和风 API 主动返回 gzip 压缩，SimpleClientHttpRequestFactory 不会自动解压，
     *  因此按 byte[] 接收，检测 gzip 魔数后手动解压。 */
    private JsonNode getForJson(String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-QW-Api-Key", weatherProperties.getApiKey());
        headers.set("Accept-Encoding", "gzip");
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<byte[]> resp = restTemplate.exchange(
                url, HttpMethod.GET, entity, byte[].class);
        byte[] body = resp.getBody();
        if (body == null) {
            throw new RuntimeException("和风天气响应为空");
        }
        // gzip 魔数：0x1F 0x8B
        if (body.length >= 2 && (body[0] & 0xFF) == 0x1F && (body[1] & 0xFF) == 0x8B) {
            body = gunzip(body);
        }
        try {
            return objectMapper.readValue(new String(body, StandardCharsets.UTF_8), JsonNode.class);
        } catch (Exception e) {
            throw new RuntimeException("解析和风天气响应失败: " + e.getMessage(), e);
        }
    }

    /** gzip 解压 */
    private byte[] gunzip(byte[] compressed) {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(compressed));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("gzip 解压失败: " + e.getMessage(), e);
        }
    }

    /** 缓存 key：weather:v2:{type}:{lon},{lat}（v2 前缀用于让旧版错误缓存自动失效） */
    private String cacheKey(String type, double lon, double lat) {
        return String.format("weather:v2:%s:%.2f,%.2f", type, lon, lat);
    }

    // ==================== 响应解析 ====================

    private RealtimeWeatherVO parseRealtime(JsonNode root) {
        RealtimeWeatherVO vo = new RealtimeWeatherVO();
        JsonNode condition = root.path("condition");
        vo.setText(condition.path("text").asText(""));
        vo.setIcon(condition.path("code").asText(""));
        vo.setTemp(root.path("temperature").path("value").asDouble());
        vo.setFeelsLike(root.path("feelsLike").path("value").asDouble());
        double humidity01 = root.path("humidity").asDouble(0);
        vo.setHumidity((int) Math.round(humidity01 * 100));
        JsonNode wind = root.path("wind");
        vo.setWindDir(WindDirectionEnum.of(wind.path("direction").path("compass").asText()));
        vo.setWindScale(wind.path("scale").asInt());
        double speedMs = wind.path("speed").path("value").asDouble(0);
        vo.setWindSpeed(Math.round(speedMs * 3.6 * 10) / 10.0);
        // v1 current 接口未直接返回 obsTime，使用元数据 tag 不适合，这里留空由前端展示当前时间
        vo.setObsTime(null);
        return vo;
    }

    private List<HourlyPointVO> parseHourly(JsonNode root) {
        List<HourlyPointVO> list = new ArrayList<>();
        JsonNode hours = root.path("hours");
        if (hours.isArray()) {
            for (JsonNode h : hours) {
                HourlyPointVO p = new HourlyPointVO();
                p.setTime(h.path("forecastTime").asText());
                p.setTemp(h.path("temperature").path("value").asDouble());
                p.setText(h.path("condition").path("text").asText());
                p.setIcon(h.path("condition").path("code").asText());
                double prob = h.path("precipitation").path("probability").asDouble(0);
                p.setPrecipProb((int) Math.round(prob * 100));
                list.add(p);
            }
        }
        return list;
    }

    private List<DailyForecastVO> parseDaily(JsonNode root) {
        List<DailyForecastVO> list = new ArrayList<>();
        JsonNode days = root.path("days");
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        if (days.isArray()) {
            for (JsonNode d : days) {
                DailyForecastVO vo = new DailyForecastVO();
                String start = d.path("forecastStartTime").asText("");
                // ISO 形如 2024-08-11T00:00+08:00，取日期部分
                String datePart = start.length() >= 10 ? start.substring(0, 10) : start;
                vo.setDate(datePart);
                vo.setWeek(weekLabel(datePart));
                vo.setTempMax(d.path("temperatureMax").path("value").asDouble());
                vo.setTempMin(d.path("temperatureMin").path("value").asDouble());
                JsonNode day = d.path("daytime");
                vo.setDayText(day.path("condition").path("text").asText());
                vo.setDayIcon(day.path("condition").path("code").asText());
                JsonNode night = d.path("nighttime");
                vo.setNightText(night.path("condition").path("text").asText());
                vo.setNightIcon(night.path("condition").path("code").asText());
                vo.setWindDir(WindDirectionEnum.of(day.path("wind").path("direction").path("compass").asText()));
                vo.setWindScale(day.path("wind").path("scale").asInt());
                // 日出日落：和风 v1 可能在 astro 节点下，也可能直接在当前节点
                String sr = d.path("sunrise").asText("");
                String ss = d.path("sunset").asText("");
                if (sr.isEmpty() && d.has("astro")) {
                    sr = d.path("astro").path("sunrise").asText("");
                    ss = d.path("astro").path("sunset").asText("");
                }
                vo.setSunrise(extractTime(sr));
                vo.setSunset(extractTime(ss));
                list.add(vo);
            }
        }
        return list;
    }

    private WarningVO parseWarning(JsonNode root) {
        WarningVO vo = new WarningVO();
        JsonNode alerts = root.path("alerts");
        List<WarningVO.Item> items = new ArrayList<>();
        if (alerts.isArray()) {
            for (JsonNode a : alerts) {
                // 跳过 cancel 类型的取消预警
                String msgCode = a.path("messageType").path("code").asText("");
                if ("cancel".equalsIgnoreCase(msgCode)) continue;
                WarningVO.Item item = new WarningVO.Item();
                item.setHeadline(a.path("headline").asText(""));
                item.setEventType(a.path("eventType").path("name").asText(""));
                item.setColorCode(a.path("color").path("code").asText("blue"));
                item.setSeverity(a.path("severity").asText("minor"));
                item.setIssuedTime(a.path("issuedTime").asText(""));
                item.setDescription(a.path("description").asText(""));
                items.add(item);
            }
        }
        vo.setHasWarning(!items.isEmpty());
        vo.setAlerts(items);
        return vo;
    }

    private AirQualityVO parseAirQuality(JsonNode root) {
        AirQualityVO vo = new AirQualityVO();
        // indexes 数组里优先取中国国标 AQI（cn-mee），其次 us-epa，最后取第一个
        JsonNode indexes = root.path("indexes");
        JsonNode chosen = null;
        if (indexes.isArray()) {
            for (JsonNode idx : indexes) {
                String code = idx.path("code").asText("");
                if ("cn-mee".equalsIgnoreCase(code) || "cn-aqi".equalsIgnoreCase(code)) {
                    chosen = idx; break;
                }
            }
            if (chosen == null && indexes.size() > 0) {
                // 退而求其次：优先 us-epa
                for (JsonNode idx : indexes) {
                    if ("us-epa".equalsIgnoreCase(idx.path("code").asText())) { chosen = idx; break; }
                }
            }
            if (chosen == null && indexes.size() > 0) chosen = indexes.get(0);
        }
        if (chosen != null) {
            vo.setAqi(chosen.path("aqi").asInt());
            vo.setAqiDisplay(chosen.path("aqiDisplay").asText());
            vo.setCategory(chosen.path("category").asText(""));
            vo.setLevel(chosen.path("level").asText(""));
            // 颜色 RGBA → #RRGGBB
            JsonNode color = chosen.path("color");
            int r = color.path("red").asInt(0);
            int g = color.path("green").asInt(0);
            int b = color.path("blue").asInt(0);
            vo.setColor(String.format("#%02X%02X%02X", r, g, b));
            JsonNode primary = chosen.path("primaryPollutant");
            if (!primary.isMissingNode() && !primary.isNull() && !primary.path("name").isMissingNode()) {
                vo.setPrimaryPollutant(primary.path("name").asText(""));
            }
            JsonNode advice = chosen.path("health").path("advice").path("generalPopulation");
            if (!advice.isMissingNode()) {
                vo.setAdvice(advice.asText(""));
            }
        }

        // 从 pollutants 数组提取 PM2.5 / PM10
        JsonNode pollutants = root.path("pollutants");
        if (pollutants.isArray()) {
            for (JsonNode p : pollutants) {
                String code = p.path("code").asText("");
                double v = p.path("concentration").path("value").asDouble(0);
                if ("pm2p5".equals(code)) vo.setPm25(v);
                else if ("pm10".equals(code)) vo.setPm10(v);
                else if ("co".equals(code)) vo.setCo(v);
            }
        }
        return vo;
    }

    private IndicesVO parseIndices(JsonNode root) {
        IndicesVO vo = new IndicesVO();
        List<IndicesVO.Item> items = new ArrayList<>();
        JsonNode daily = root.path("daily");
        if (daily.isArray()) {
            for (JsonNode d : daily) {
                IndicesVO.Item item = new IndicesVO.Item();
                item.setName(d.path("name").asText(""));
                item.setCategory(d.path("category").asText(""));
                item.setText(d.path("text").asText(""));
                items.add(item);
            }
        }
        vo.setItems(items);
        return vo;
    }

    /** 首页地图标注的主要城市（与前端 cityCoords.js 保持一致） */
    private static final Object[][] MAP_CITIES = {
            {"北京", 116.41, 39.90},
            {"上海", 121.47, 31.23},
            {"广州", 113.26, 23.13},
            {"深圳", 114.06, 22.54},
            {"成都", 104.07, 30.57},
            {"西安", 108.94, 34.34},
            {"武汉", 114.30, 30.59},
            {"杭州", 120.15, 30.28},
            {"重庆", 106.55, 29.56},
            {"福州", 119.30, 26.08}
    };

    @Override
    public List<CityOverviewVO> getCitiesOverview() {
        String cacheKey = "weather:v2:cities-overview";
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof CityOverviewVO) {
            @SuppressWarnings("unchecked")
            List<CityOverviewVO> typed = (List<CityOverviewVO>) list;
            return typed;
        }

        List<CityOverviewVO> result = new ArrayList<>();
        for (Object[] row : MAP_CITIES) {
            String city = (String) row[0];
            double lon = (double) row[1];
            double lat = (double) row[2];
            try {
                String url = buildUrl("/weather/v1/current/", lon, lat) + "?lang=zh";
                JsonNode root = getForJson(url);
                // 复用 parseRealtime，再从中取概览字段，避免重复解析逻辑
                RealtimeWeatherVO rt = parseRealtime(root);
                CityOverviewVO vo = new CityOverviewVO();
                vo.setCity(city);
                vo.setLon(lon);
                vo.setLat(lat);
                vo.setTemp(rt.getTemp());
                vo.setText(rt.getText());
                vo.setIcon(rt.getIcon());
                result.add(vo);
            } catch (Exception e) {
                log.warn("查询城市[{}]天气失败: {}", city, e.getMessage());
            }
        }
        if (!result.isEmpty()) {
            redisTemplate.opsForValue().set(cacheKey, result,
                    weatherProperties.getCacheTtlSeconds(), TimeUnit.SECONDS);
        }
        return result;
    }

    /** 从 ISO 时间或 HH:mm 提取 HH:mm，如 2024-08-11T06:12+08:00 或 06:12 */
    private String extractTime(String t) {
        if (t == null || t.isEmpty()) return "";
        int idx = t.indexOf('T');
        if (idx >= 0 && t.length() >= idx + 6) {
            return t.substring(idx + 1, idx + 6);
        }
        // 已是 HH:mm 格式
        if (t.length() >= 5 && t.charAt(2) == ':') return t.substring(0, 5);
        return t;
    }

    /** ISO 日期转中文星期 */
    private String weekLabel(String datePart) {
        try {
            LocalDate d = LocalDate.parse(datePart, dateFmt());
            DayOfWeek dow = d.getDayOfWeek();
            String[] labels = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
            return labels[dow.getValue() - 1];
        } catch (Exception e) {
            return "";
        }
    }

    private DateTimeFormatter dateFmt() {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd");
    }
}
