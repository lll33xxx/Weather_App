package com.example.nl.service.impl;

import com.example.nl.mapper.WeatherDataMapper;
import com.example.nl.service.WeatherDataService;
import com.example.nl.enums.WeatherMetric;
import com.example.nl.vo.PageResult;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 天气数据管理业务层实现
 * 面向后台数据管理，支持分页、多条件筛选
 */
@Service
public class WeatherDataServiceImpl implements WeatherDataService {

    @Autowired
    private WeatherDataMapper weatherDataMapper;

    /**
     * 分页查询天气数据
     *
     * @param metricCode 指标编码 rain/wind/temp/sun/humidity
     * @param year       年份筛选（可为null）
     * @param province   省份筛选（可为空）
     * @param city       城市模糊搜索（可为空）
     * @param pageNum    页码（从1开始）
     * @param pageSize   每页条数
     * @return 分页结果，每条数据附带指标元信息
     */
    @Cacheable(value = "weatherData:page",
            key = "#metricCode + '_' + #year + '_' + #province + '_' + #city + '_' + #pageNum + '_' + #pageSize")
    @Override
    public PageResult<Map<String, Object>> getDataPage(String metricCode, Integer year,
                                                       String province, String city,
                                                       int pageNum, int pageSize) {
        WeatherMetric metric = WeatherMetric.fromCode(metricCode);
        if (metric == null) {
            throw new IllegalArgumentException("不支持的指标类型: " + metricCode);
        }
        int pn = Math.max(pageNum, 1);
        int ps = (pageSize <= 0) ? 10 : Math.min(pageSize, 100);
        // PageHelper 只对紧随其后的第一条 MyBatis 查询生效
        PageHelper.startPage(pn, ps);
        List<Map<String, Object>> rawList = weatherDataMapper.selectDataPage(
                metric.getRankingTable(), metric.getMetricCol(), metric.getDaysCol(),
                year, province, city);
        PageInfo<Map<String, Object>> pageInfo = new PageInfo<>(rawList);

        // 给每条数据附加指标元信息，方便前端统一展示
        List<Map<String, Object>> list = rawList.stream().map(row -> {
            Map<String, Object> item = new HashMap<>(row);
            item.put("metricCode", metric.getCode());
            item.put("metricName", metric.getShortName());
            item.put("metricDisplayName", metric.getDisplayName());
            item.put("unit", metric.getUnit());
            item.put("daysName", metric.getDaysName());
            return item;
        }).toList();

        return PageResult.of(pn, ps, pageInfo.getTotal(), list);
    }

    /**
     * 获取指定指标的可用年份列表
     */
    @Cacheable(value = "weatherData:years", key = "#metricCode")
    @Override
    public List<Integer> getYears(String metricCode) {
        WeatherMetric metric = WeatherMetric.fromCode(metricCode);
        if (metric == null) {
            throw new IllegalArgumentException("不支持的指标类型: " + metricCode);
        }
        return weatherDataMapper.selectYears(metric.getRankingTable());
    }

    /**
     * 获取指定指标的可用省份列表
     */
    @Cacheable(value = "weatherData:provinces", key = "#metricCode")
    @Override
    public List<String> getProvinces(String metricCode) {
        WeatherMetric metric = WeatherMetric.fromCode(metricCode);
        if (metric == null) {
            throw new IllegalArgumentException("不支持的指标类型: " + metricCode);
        }
        return weatherDataMapper.selectProvinces(metric.getRankingTable());
    }
}
