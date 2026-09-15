package com.example.nl.service.impl;

import com.example.nl.enums.WeatherMetric;
import com.example.nl.mapper.WeatherAiMapper;
import com.example.nl.service.DailyWeatherService;
import com.example.nl.vo.DailyWeatherVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 日度天气数据业务层实现
 * 从 MySQL dwd 层日度表查询指定城市、年份、指标的逐日数据
 */
@Service
public class DailyWeatherServiceImpl implements DailyWeatherService {

    @Autowired
    private WeatherAiMapper weatherAiMapper;

    /**
     * 查询指定城市、年份、指标的逐日数据
     */
    @Cacheable(value = "daily:data", key = "#metricCode + '_' + #city + '_' + #year")
    @Override
    public List<DailyWeatherVO> getDailyData(String metricCode, String city, Integer year) {
        WeatherMetric metric = WeatherMetric.fromCode(metricCode);
        if (metric == null) {
            throw new IllegalArgumentException("不支持的指标类型: " + metricCode);
        }
        if (city == null || city.isBlank() || year == null) {
            throw new IllegalArgumentException("城市和年份不能为空");
        }

        String startDate = year + "-01-01";
        String endDate = (year + 1) + "-01-01";

        List<Map<String, Object>> rows = weatherAiMapper.selectDailyYear(
                metric.getDailyTable(), metric.getDailyCol(), city, startDate, endDate);

        List<DailyWeatherVO> list = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            DailyWeatherVO vo = new DailyWeatherVO();
            vo.setDate(String.valueOf(row.get("date_date")));
            vo.setMetricValue(((Number) row.get("metricValue")).doubleValue());
            vo.setMetricCode(metric.getCode());
            vo.setMetricName(metric.getDailyColName());
            vo.setUnit(metric.getUnit());
            list.add(vo);
        }
        return list;
    }
}
