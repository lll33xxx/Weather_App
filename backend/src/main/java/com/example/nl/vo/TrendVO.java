package com.example.nl.vo;

import lombok.Data;

/**
 * 通用趋势VO - 适配5张 trend 表（降水/风速/气温/日照/湿度）
 */
@Data
public class TrendVO {
    /** 城市 */
    private String city;
    /** 年份 */
    private Integer year;
    /** 指标值 */
    private Double metricValue;
    /** 天数 */
    private Integer days;
    /** 趋势标签（上升/下降/稳定） */
    private String trendLabel;
}
