package com.example.nl.service.impl;

import com.example.nl.mapper.WeatherMapper;
import com.example.nl.enums.WeatherMetric;
import com.example.nl.service.WeatherService;
import com.example.nl.vo.RankingVO;
import com.example.nl.vo.TrendVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 气象数据业务层实现
 */
@Service
public class WeatherServiceImpl implements WeatherService {

    @Autowired
    private WeatherMapper weatherMapper;

    /**
     * 查询指定指标、年份的城市排名
     *
     * @param metricCode 指标编码 rain/wind/temp/sun/humidity
     * @param year       年份
     * @param limit      取前N名，默认20
     */
    @Cacheable(value = "weather:ranking", key = "#metricCode + '_' + #year + '_' + #limit")
    @Override
    public List<RankingVO> getRanking(String metricCode, Integer year, Integer limit) {
        WeatherMetric metric = WeatherMetric.fromCode(metricCode);
        if (metric == null) {
            throw new IllegalArgumentException("不支持的指标类型: " + metricCode);
        }
        int lim = (limit == null || limit <= 0) ? 20 : limit;
        return weatherMapper.selectRanking(metric.getRankingTable(),
                metric.getMetricCol(), metric.getDaysCol(), year, lim);
    }

    /**
     * 查询指定指标、城市的历年趋势
     */
    @Cacheable(value = "weather:trend", key = "#metricCode + '_' + #city")
    @Override
    public List<TrendVO> getTrend(String metricCode, String city) {
        WeatherMetric metric = WeatherMetric.fromCode(metricCode);
        if (metric == null) {
            throw new IllegalArgumentException("不支持的指标类型: " + metricCode);
        }
        return weatherMapper.selectTrend(metric.getTrendTable(),
                metric.getMetricCol(), metric.getDaysCol(), city);
    }

    /**
     * 查询指定指标的可用年份列表
     */
    @Cacheable(value = "weather:years", key = "#metricCode")
    @Override
    public List<Integer> getYears(String metricCode) {
        WeatherMetric metric = WeatherMetric.fromCode(metricCode);
        if (metric == null) {
            throw new IllegalArgumentException("不支持的指标类型: " + metricCode);
        }
        return weatherMapper.selectYears(metric.getRankingTable());
    }

    /**
     * 查询指定指标的可用城市列表
     */
    @Cacheable(value = "weather:cities", key = "#metricCode")
    @Override
    public List<String> getCities(String metricCode) {
        WeatherMetric metric = WeatherMetric.fromCode(metricCode);
        if (metric == null) {
            throw new IllegalArgumentException("不支持的指标类型: " + metricCode);
        }
        return weatherMapper.selectCities(metric.getTrendTable());
    }

    /**
     * 获取所有指标的元信息列表（供前端渲染指标切换器）
     */
    @Cacheable(value = "weather:metrics")
    @Override
    public List<Map<String, String>> getMetricList() {
        return Arrays.stream(WeatherMetric.values())
                .map(m -> Map.of(
                        "code", m.getCode(),
                        "name", m.getShortName(),
                        "displayName", m.getDisplayName(),
                        "unit", m.getUnit(),
                        "daysName", m.getDaysName()
                ))
                .collect(Collectors.toList());
    }
}
