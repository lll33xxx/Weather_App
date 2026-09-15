package com.example.nl.service.impl;

import com.example.nl.enums.WeatherMetric;
import com.example.nl.mapper.WeatherAiMapper;
import com.example.nl.service.WeatherAiService;
import com.example.nl.vo.RankingVO;
import com.example.nl.vo.WeatherSummaryVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 天气数据 AI 模块业务层实现
 * 基于 5 张 ranking 表（ads 层）提供 Coze 插件所需的查询能力
 */
@Service
public class WeatherAiServiceImpl implements WeatherAiService {

    @Autowired
    private WeatherAiMapper weatherAiMapper;

    /**
     * 查询单城市单年 5 类指标汇总
     * 各指标从对应 ranking 表按 city + year 查询，5 张表结果组装为统一结构
     */
    @Cacheable(value = "ai:summary", key = "#city + '_' + #year")
    @Override
    public WeatherSummaryVO getSummary(String city, Integer year) {
        // 未指定年份时，默认取数据中的最近年份（保证必返回数据）
        Integer targetYear = (year == null) ? getLatestYear() : year;

        WeatherSummaryVO vo = new WeatherSummaryVO();
        vo.setCity(city);
        vo.setYear(targetYear);

        Map<String, WeatherSummaryVO.MetricItem> metrics = new LinkedHashMap<>();
        for (WeatherMetric metric : WeatherMetric.values()) {
            Map<String, Object> row = weatherAiMapper.selectSummary(
                    metric.getRankingTable(), metric.getMetricCol(), metric.getDaysCol(),
                    city, targetYear);
            if (row != null && row.get("metricValue") != null) {
                if (vo.getProvince() == null) {
                    vo.setProvince((String) row.get("province"));
                }
                WeatherSummaryVO.MetricItem item = new WeatherSummaryVO.MetricItem();
                item.setCode(metric.getCode());
                item.setName(metric.getShortName());
                item.setDisplayName(metric.getDisplayName());
                item.setUnit(metric.getUnit());
                item.setValue(((Number) row.get("metricValue")).doubleValue());
                item.setDays((Integer) row.get("days"));
                item.setDaysName(metric.getDaysName());
                metrics.put(metric.getCode(), item);
            }
        }
        vo.setMetrics(metrics);
        return vo;
    }

    /**
     * 查询指定指标、年份的城市排名，支持省份筛选
     */
    @Cacheable(value = "ai:ranking", key = "#metricCode + '_' + #year + '_' + #province + '_' + #limit")
    @Override
    public List<RankingVO> getRanking(String metricCode, Integer year, String province, Integer limit) {
        WeatherMetric metric = WeatherMetric.fromCode(metricCode);
        if (metric == null) {
            throw new IllegalArgumentException("不支持的指标类型: " + metricCode);
        }
        Integer targetYear = (year == null) ? getLatestYear() : year;
        int lim = (limit == null || limit <= 0) ? 10 : limit;
        return weatherAiMapper.selectRankingWithProvince(
                metric.getRankingTable(), metric.getMetricCol(), metric.getDaysCol(),
                targetYear, province, lim);
    }

    /**
     * 查询指定省份、年份的城市列表（以气温表为基准，保证省份字段最完整）
     */
    @Cacheable(value = "ai:cities", key = "#province + '_' + #year")
    @Override
    public List<String> getCitiesByProvince(String province, Integer year) {
        Integer targetYear = (year == null) ? getLatestYear() : year;
        return weatherAiMapper.selectCitiesByProvince(
                WeatherMetric.TEMP.getRankingTable(), targetYear, province);
    }

    /**
     * 获取 AI 模块元信息：指标列表、年份范围、省份列表
     */
    @Cacheable(value = "ai:meta")
    @Override
    public Map<String, Object> getMeta() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("metrics", Arrays.stream(WeatherMetric.values())
                .map(m -> Map.of(
                        "code", m.getCode(),
                        "name", m.getShortName(),
                        "displayName", m.getDisplayName(),
                        "unit", m.getUnit(),
                        "daysName", m.getDaysName()
                ))
                .toList());
        List<Integer> years = weatherAiMapper.selectYears(WeatherMetric.TEMP.getRankingTable());
        if (!years.isEmpty()) {
            meta.put("minYear", years.stream().min(Comparator.naturalOrder()).orElse(null));
            meta.put("maxYear", years.stream().max(Comparator.naturalOrder()).orElse(null));
        }
        meta.put("provinces", weatherAiMapper.selectProvinces(WeatherMetric.TEMP.getRankingTable()));
        return meta;
    }

    /**
     * 查询指定城市、指定日期的全部天气指标（单日详情）
     */
    @Cacheable(value = "ai:daily_detail", key = "#city + '_' + #date")
    @Override
    public Map<String, Object> getDailyDetail(String city, String date) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("city", city);
        result.put("date", date);

        Map<String, Map<String, Object>> metrics = new LinkedHashMap<>();
        for (WeatherMetric metric : WeatherMetric.values()) {
            Map<String, Object> row = weatherAiMapper.selectDailyDetail(
                    metric.getDailyTable(), metric.getDailyCol(), city, date);
            if (row != null && row.get("metricValue") != null) {
                if (result.get("province") == null) {
                    // 日度表没有 province 字段，从 ranking 表补
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", metric.getShortName());
                m.put("displayName", metric.getDisplayName());
                m.put("unit", metric.getUnit());
                m.put("value", ((Number) row.get("metricValue")).doubleValue());
                metrics.put(metric.getCode(), m);
            }
        }
        result.put("metrics", metrics);
        return result;
    }

    /**
     * 查询指定城市、指定指标的历年趋势数据（2000-2020）
     */
    @Cacheable(value = "ai:trend", key = "#city + '_' + #metricCode")
    @Override
    public Map<String, Object> getTrendAnalysis(String city, String metricCode) {
        WeatherMetric metric = WeatherMetric.fromCode(metricCode);
        if (metric == null) {
            throw new IllegalArgumentException("不支持的指标类型: " + metricCode);
        }
        List<Map<String, Object>> rows = weatherAiMapper.selectTrendData(
                metric.getTrendTable(), metric.getMetricCol(), metric.getDaysCol(), city);

        List<Map<String, Object>> yearly = new ArrayList<>();
        String trendLabel = "";
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("year", row.get("year"));
            item.put("value", ((Number) row.get("metricValue")).doubleValue());
            item.put("days", row.get("days"));
            yearly.add(item);
            if (row.get("trendLabel") != null) {
                trendLabel = String.valueOf(row.get("trendLabel"));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("city", city);
        result.put("metric", metric.getShortName());
        result.put("displayName", metric.getDisplayName());
        result.put("unit", metric.getUnit());
        result.put("trendLabel", trendLabel);
        result.put("yearlyData", yearly);
        return result;
    }

    /**
     * 查询指定城市、月份、指标的逐日数据
     */
    @Cacheable(value = "ai:monthly_daily", key = "#city + '_' + #year + '_' + #month + '_' + #metricCode")
    @Override
    public Map<String, Object> getMonthlyDaily(String city, Integer year, Integer month, String metricCode) {
        WeatherMetric metric = WeatherMetric.fromCode(metricCode);
        if (metric == null) {
            throw new IllegalArgumentException("不支持的指标类型: " + metricCode);
        }
        // 计算月份起止日期
        int nextMonth = month == 12 ? 1 : month + 1;
        int nextYear = month == 12 ? year + 1 : year;
        String startDate = String.format("%04d-%02d-01", year, month);
        String endDate = String.format("%04d-%02d-01", nextYear, nextMonth);

        List<Map<String, Object>> rows = weatherAiMapper.selectMonthlyDaily(
                metric.getDailyTable(), metric.getDailyCol(), city, startDate, endDate);

        List<Map<String, Object>> dailyList = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", String.valueOf(row.get("date_date")));
            item.put("value", ((Number) row.get("metricValue")).doubleValue());
            dailyList.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("city", city);
        result.put("year", year);
        result.put("month", month);
        result.put("metric", metric.getShortName());
        result.put("unit", metric.getUnit());
        result.put("dailyData", dailyList);
        return result;
    }

    /**
     * 获取数据中的最近年份（气温表年份最大值）
     */
    private Integer getLatestYear() {
        List<Integer> years = weatherAiMapper.selectYears(WeatherMetric.TEMP.getRankingTable());
        return years.stream().max(Comparator.naturalOrder()).orElse(null);
    }
}
