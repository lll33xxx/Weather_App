package com.example.nl.service;

import com.example.nl.vo.RankingVO;
import com.example.nl.vo.TrendVO;

import java.util.List;
import java.util.Map;

/**
 * 气象数据业务层接口
 */
public interface WeatherService {

    /**
     * 查询指定指标、年份的城市排名
     *
     * @param metricCode 指标编码 rain/wind/temp/sun/humidity
     * @param year       年份
     * @param limit      取前N名，默认20
     */
    List<RankingVO> getRanking(String metricCode, Integer year, Integer limit);

    /**
     * 查询指定指标、城市的历年趋势
     */
    List<TrendVO> getTrend(String metricCode, String city);

    /**
     * 查询指定指标的可用年份列表
     */
    List<Integer> getYears(String metricCode);

    /**
     * 查询指定指标的可用城市列表
     */
    List<String> getCities(String metricCode);

    /**
     * 获取所有指标的元信息列表（供前端渲染指标切换器）
     */
    List<Map<String, String>> getMetricList();
}
