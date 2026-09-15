package com.example.nl.vo;

import lombok.Data;

/**
 * 实时空气质量（右侧轮播卡片三）
 */
@Data
public class AirQualityVO {
    /** AQI 数值 */
    private Integer aqi;
    /** AQI 显示值（兼容非数字） */
    private String aqiDisplay;
    /** 等级类别（优/良/轻度污染...） */
    private String category;
    /** 等级数字 1-6 */
    private String level;
    /** 颜色 hex（#RRGGBB），用于卡片配色 */
    private String color;
    /** 首要污染物名称（PM2.5 / PM10 等） */
    private String primaryPollutant;
    /** PM2.5 浓度 μg/m³ */
    private Double pm25;
    /** PM10 浓度 μg/m³ */
    private Double pm10;
    /** CO 浓度 μg/m³ */
    private Double co;
    /** 健康建议 */
    private String advice;
}
