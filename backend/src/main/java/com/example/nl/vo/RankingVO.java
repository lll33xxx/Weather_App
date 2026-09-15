package com.example.nl.vo;

import lombok.Data;

/**
 * 通用排名VO - 适配5张 ranking 表（降水/风速/气温/日照/湿度）
 * 不同表的指标字段通过 SQL 别名统一映射为 metricValue / days
 */
@Data
public class RankingVO {
    /** 年份 */
    private Integer year;
    /** 全国排名 */
    private Integer rankNum;
    /** 城市 */
    private String city;
    /** 省份 */
    private String province;
    /** 指标值（降水量mm / 风速m/s / 气温℃ / 日照h / 湿度%） */
    private Double metricValue;
    /** 天数（降雨/大风/高温/晴好/高湿天数） */
    private Integer days;
}
