package com.example.nl.vo;

import lombok.Data;

/**
 * 日度天气数据VO（从MySQL dwd层查询）
 */
@Data
public class DailyWeatherVO {
    /** 日期 yyyy-MM-dd */
    private String date;
    /** 指标值 */
    private Double metricValue;
    /** 指标编码 */
    private String metricCode;
    /** 指标显示名 */
    private String metricName;
    /** 单位 */
    private String unit;
}
