package com.example.nl.service;

import com.example.nl.vo.RankingVO;
import com.example.nl.vo.WeatherSummaryVO;

import java.util.List;
import java.util.Map;

/**
 * 天气数据 AI 模块业务层接口
 * 供 Coze 智能体插件调用，覆盖天气指标咨询、排名查询、元信息查询
 */
public interface WeatherAiService {

    /**
     * 查询单城市单年 5 类指标汇总
     *
     * @param city 城市名
     * @param year 年份（null 时默认取数据中的最近年份）
     */
    WeatherSummaryVO getSummary(String city, Integer year);

    /**
     * 查询指定指标、年份的城市排名，支持省份筛选
     *
     * @param metricCode 指标编码 rain/wind/temp/sun/humidity
     * @param year       年份
     * @param province   省份（可为空，空为全国）
     * @param limit      取前N名，默认10
     */
    List<RankingVO> getRanking(String metricCode, Integer year, String province, Integer limit);

    /**
     * 查询指定省份、年份的城市列表
     */
    List<String> getCitiesByProvince(String province, Integer year);

    /**
     * 获取 AI 模块元信息：指标列表、年份范围、省份列表
     */
    Map<String, Object> getMeta();

    /**
     * 查询指定城市、指定日期的全部天气指标（单日详情）
     *
     * @param city 城市名
     * @param date 日期（yyyy-MM-dd）
     */
    Map<String, Object> getDailyDetail(String city, String date);

    /**
     * 查询指定城市、指定指标的历年趋势数据（2000-2020）
     *
     * @param city       城市名
     * @param metricCode 指标编码 rain/wind/temp/sun/humidity
     */
    Map<String, Object> getTrendAnalysis(String city, String metricCode);

    /**
     * 查询指定城市、月份、指标的逐日数据
     *
     * @param city       城市名
     * @param year       年份
     * @param month      月份（1-12）
     * @param metricCode 指标编码
     */
    Map<String, Object> getMonthlyDaily(String city, Integer year, Integer month, String metricCode);
}
