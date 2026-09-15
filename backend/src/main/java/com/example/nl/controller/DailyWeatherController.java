package com.example.nl.controller;

import com.example.nl.service.DailyWeatherService;
import com.example.nl.vo.DailyWeatherVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 日度天气数据控制器
 * 基础路径: /api/admin/weather-daily
 * 从 MySQL 查询指定城市、年份、指标的逐日明细
 */
@RestController
@RequestMapping("/api/admin/weather-daily")
@CrossOrigin(origins = "*")
public class DailyWeatherController {

    @Autowired
    private DailyWeatherService dailyWeatherService;

    /**
     * 查询指定城市、年份、指标的逐日数据
     * GET /api/admin/weather-daily?metric=rain&city=北京&year=2020
     */
    @GetMapping
    public Result<List<DailyWeatherVO>> getDailyData(
            @RequestParam String metric,
            @RequestParam String city,
            @RequestParam Integer year) {
        try {
            return Result.success(dailyWeatherService.getDailyData(metric, city, year));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error(500, e.getMessage());
        }
    }
}
