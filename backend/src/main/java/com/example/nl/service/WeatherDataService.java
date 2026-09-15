package com.example.nl.service;

import com.example.nl.vo.PageResult;

import java.util.List;
import java.util.Map;

/**
 * 天气数据管理业务层接口
 * 面向后台数据管理，支持分页、多条件筛选
 */
public interface WeatherDataService {

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
    PageResult<Map<String, Object>> getDataPage(String metricCode, Integer year,
                                                 String province, String city,
                                                 int pageNum, int pageSize);

    /**
     * 获取指定指标的可用年份列表
     */
    List<Integer> getYears(String metricCode);

    /**
     * 获取指定指标的可用省份列表
     */
    List<String> getProvinces(String metricCode);
}
