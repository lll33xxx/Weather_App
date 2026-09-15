package com.example.nl.controller;

import com.example.nl.service.WeatherService;
import com.example.nl.vo.RankingVO;
import com.example.nl.vo.TrendVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 气象数据 REST API
 * 基础路径: /api/weather
 */
@RestController
@RequestMapping("/api/weather")
@CrossOrigin(origins = "*")
public class WeatherController {

    @Autowired
    private WeatherService weatherService;

    /**
     * 获取所有指标元信息
     * GET /api/weather/metrics
     */
    @GetMapping("/metrics")
    public Result<List<Map<String, String>>> getMetrics() {
        return Result.success(weatherService.getMetricList());
    }

    /**
     * 获取指定指标的可用年份列表
     * GET /api/weather/years?metric=rain
     */
    @GetMapping("/years")
    public Result<List<Integer>> getYears(@RequestParam String metric) {
        try {
            return Result.success(weatherService.getYears(metric));
        } catch (Exception e) {
            return Result.error(500, e.getMessage());
        }
    }

    /**
     * 获取指定指标的可用城市列表
     * GET /api/weather/cities?metric=rain
     */
    @GetMapping("/cities")
    public Result<List<String>> getCities(@RequestParam String metric) {
        try {
            return Result.success(weatherService.getCities(metric));
        } catch (Exception e) {
            return Result.error(500, e.getMessage());
        }
    }

    /**
     * 查询城市排名
     * GET /api/weather/ranking?metric=rain&year=2020&limit=20
     */
    @GetMapping("/ranking")
    public Result<List<RankingVO>> getRanking(
            @RequestParam String metric,
            @RequestParam Integer year,
            @RequestParam(defaultValue = "20") Integer limit) {
        try {
            return Result.success(weatherService.getRanking(metric, year, limit));
        } catch (Exception e) {
            return Result.error(500, e.getMessage());
        }
    }

    /**
     * 查询城市历年趋势
     * GET /api/weather/trend?metric=rain&city=北京
     */
    @GetMapping("/trend")
    public Result<List<TrendVO>> getTrend(
            @RequestParam String metric,
            @RequestParam String city) {
        try {
            return Result.success(weatherService.getTrend(metric, city));
        } catch (Exception e) {
            return Result.error(500, e.getMessage());
        }
    }
}
