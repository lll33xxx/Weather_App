package com.example.nl.controller;

import com.example.nl.service.WeatherDataService;
import com.example.nl.vo.PageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理员 - 天气数据管理控制器
 * 基础路径: /api/admin/weather-data
 */
@RestController
@RequestMapping("/api/admin/weather-data")
@CrossOrigin(origins = "*")
public class WeatherDataController {

    @Autowired
    private WeatherDataService weatherDataService;

    /**
     * 分页查询天气数据
     * GET /api/admin/weather-data?metric=rain&year=2020&province=福建&city=福州&pageNum=1&pageSize=20
     */
    @GetMapping
    public Result<PageResult<Map<String, Object>>> list(
            @RequestParam(defaultValue = "rain") String metric,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String city,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        try {
            return Result.success(weatherDataService.getDataPage(
                    metric, year, province, city, pageNum, pageSize));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error(500, e.getMessage());
        }
    }

    /**
     * 获取指定指标的可用年份列表
     * GET /api/admin/weather-data/years?metric=rain
     */
    @GetMapping("/years")
    public Result<List<Integer>> getYears(@RequestParam(defaultValue = "rain") String metric) {
        try {
            return Result.success(weatherDataService.getYears(metric));
        } catch (Exception e) {
            return Result.error(500, e.getMessage());
        }
    }

    /**
     * 获取指定指标的可用省份列表
     * GET /api/admin/weather-data/provinces?metric=rain
     */
    @GetMapping("/provinces")
    public Result<List<String>> getProvinces(@RequestParam(defaultValue = "rain") String metric) {
        try {
            return Result.success(weatherDataService.getProvinces(metric));
        } catch (Exception e) {
            return Result.error(500, e.getMessage());
        }
    }
}
