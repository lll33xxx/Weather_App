package com.example.nl.service;

import com.example.nl.vo.DailyWeatherVO;

import java.util.List;

/**
 * 日度天气数据业务层接口
 * 从 Hive dwd 层日度表查询指定城市、年份、指标的逐日数据
 */
public interface DailyWeatherService {

    /**
     * 查询指定城市、年份、指标的逐日数据
     *
     * @param metricCode 指标编码
     * @param city       城市名
     * @param year       年份
     * @return 按日期升序排列的日度数据列表
     */
    List<DailyWeatherVO> getDailyData(String metricCode, String city, Integer year);
}
