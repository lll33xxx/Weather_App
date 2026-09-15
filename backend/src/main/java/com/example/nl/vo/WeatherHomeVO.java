package com.example.nl.vo;

import lombok.Data;

import java.util.List;

/**
 * 首页天气聚合视图对象
 * 一次请求返回首页所需全部数据，前端并行调用拆分为多个接口时也可复用
 */
@Data
public class WeatherHomeVO {
    /** 经度 */
    private Double lon;
    /** 纬度 */
    private Double lat;
    /** 实时天气 */
    private RealtimeWeatherVO realtime;
    /** 24 小时预报 */
    private List<HourlyPointVO> hourly;
    /** 7 天预报 */
    private List<DailyForecastVO> daily;
    /** 天气预警 */
    private WarningVO warning;
    /** 空气质量 */
    private AirQualityVO airQuality;
    /** 生活指数 */
    private IndicesVO indices;
}
