package com.example.nl.service;

import com.example.nl.vo.AirQualityVO;
import com.example.nl.vo.CityOverviewVO;
import com.example.nl.vo.DailyForecastVO;
import com.example.nl.vo.HourlyPointVO;
import com.example.nl.vo.IndicesVO;
import com.example.nl.vo.RealtimeWeatherVO;
import com.example.nl.vo.WarningVO;

import java.util.List;

/**
 * 实时天气业务层接口
 * 数据来源：和风天气 QWeather v1 API
 * 缓存策略：Redis 缓存实时/预报 30 分钟，预警/指数 10 分钟
 */
public interface RealtimeWeatherService {

    /**
     * 获取实时天气
     */
    RealtimeWeatherVO getRealtime(double lon, double lat);

    /**
     * 获取 24 小时预报
     */
    List<HourlyPointVO> getHourly(double lon, double lat);

    /**
     * 获取 7 天预报
     */
    List<DailyForecastVO> getDaily(double lon, double lat);

    /**
     * 获取当前生效天气预警
     */
    WarningVO getWarning(double lon, double lat);

    /**
     * 获取实时空气质量
     */
    AirQualityVO getAirQuality(double lon, double lat);

    /**
     * 获取生活指数（穿衣/紫外线/运动/感冒）
     */
    IndicesVO getIndices(double lon, double lat);

    /**
     * 获取主要城市实时天气概览（用于首页地图标注）
     * 后端循环查询若干主要城市，Redis 缓存 30 分钟
     */
    List<CityOverviewVO> getCitiesOverview();
}
