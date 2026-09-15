package com.example.nl.controller;

import com.example.nl.service.RealtimeWeatherService;
import com.example.nl.vo.AirQualityVO;
import com.example.nl.vo.CityOverviewVO;
import com.example.nl.vo.DailyForecastVO;
import com.example.nl.vo.HourlyPointVO;
import com.example.nl.vo.IndicesVO;
import com.example.nl.vo.RealtimeWeatherVO;
import com.example.nl.vo.WarningVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 实时天气控制器
 * 基础路径: /api/weather
 * 数据来源：和风天气 QWeather v1 API
 *
 * 接口列表：
 * - GET /api/weather/now       实时天气
 * - GET /api/weather/hourly    24 小时预报
 * - GET /api/weather/daily     7 天预报
 * - GET /api/weather/extras    预警 + 空气质量 + 生活指数（聚合，减少首屏请求数）
 */
@RestController
@RequestMapping("/api/weather")
public class RealtimeWeatherController {

    @Autowired
    private RealtimeWeatherService realtimeWeatherService;

    /**
     * 实时天气
     * GET /api/weather/now?lon=119.30&lat=26.08
     */
    @GetMapping("/now")
    public Result<RealtimeWeatherVO> now(@RequestParam Double lon,
                                         @RequestParam Double lat) {
        try {
            validateLonLat(lon, lat);
            return Result.success(realtimeWeatherService.getRealtime(lon, lat));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error(500, "获取实时天气失败：" + e.getMessage());
        }
    }

    /**
     * 24 小时预报
     */
    @GetMapping("/hourly")
    public Result<List<HourlyPointVO>> hourly(@RequestParam Double lon,
                                               @RequestParam Double lat) {
        try {
            validateLonLat(lon, lat);
            return Result.success(realtimeWeatherService.getHourly(lon, lat));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error(500, "获取逐小时预报失败：" + e.getMessage());
        }
    }

    /**
     * 7 天预报
     */
    @GetMapping("/daily")
    public Result<List<DailyForecastVO>> daily(@RequestParam Double lon,
                                               @RequestParam Double lat) {
        try {
            validateLonLat(lon, lat);
            return Result.success(realtimeWeatherService.getDaily(lon, lat));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error(500, "获取每日预报失败：" + e.getMessage());
        }
    }

    /**
     * 附加信息聚合：预警 + 空气质量 + 生活指数
     * 一次请求返回首页右侧轮播卡片所需全部数据
     */
    @GetMapping("/extras")
    public Result<Map<String, Object>> extras(@RequestParam Double lon,
                                               @RequestParam Double lat) {
        try {
            validateLonLat(lon, lat);
            WarningVO warning = realtimeWeatherService.getWarning(lon, lat);
            AirQualityVO air = realtimeWeatherService.getAirQuality(lon, lat);
            IndicesVO indices = realtimeWeatherService.getIndices(lon, lat);
            Map<String, Object> data = new HashMap<>();
            data.put("warning", warning);
            data.put("airQuality", air);
            data.put("indices", indices);
            return Result.success(data);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error(500, "获取附加天气信息失败：" + e.getMessage());
        }
    }

    /**
     * 主要城市实时天气概览（首页地图标注用）
     * GET /api/weather/cities-overview
     */
    @GetMapping("/cities-overview")
    public Result<List<CityOverviewVO>> citiesOverview() {
        try {
            return Result.success(realtimeWeatherService.getCitiesOverview());
        } catch (Exception e) {
            return Result.error(500, "获取城市概览失败：" + e.getMessage());
        }
    }

    /** 经纬度合法性校验 */
    private void validateLonLat(Double lon, Double lat) {
        if (lon == null || lat == null) {
            throw new IllegalArgumentException("经纬度不能为空");
        }
        if (lon < -180 || lon > 180) {
            throw new IllegalArgumentException("经度取值范围应为 -180 ~ 180");
        }
        if (lat < -90 || lat > 90) {
            throw new IllegalArgumentException("纬度取值范围应为 -90 ~ 90");
        }
    }
}
