package com.example.nl.vo;

import lombok.Data;

/**
 * 天气指标汇总VO - 单城市单年 5 类指标一次性返回（供 Coze 天气咨询使用）
 *
 * 由 WeatherAiServiceImpl 从 5 张 ranking 表（ads_*_ranking）按 city + year 各查一条组装
 */
@Data
public class WeatherSummaryVO {

    /** 城市名 */
    private String city;
    /** 省份 */
    private String province;
    /** 年份 */
    private Integer year;
    /** 5类指标汇总（key 为指标编码 temp/rain/sun/humidity/wind，value 为指标明细） */
    private java.util.Map<String, MetricItem> metrics;

    /**
     * 单指标明细
     */
    @Data
    public static class MetricItem {
        /** 指标编码 */
        private String code;
        /** 指标简称 */
        private String name;
        /** 指标显示全称 */
        private String displayName;
        /** 指标单位 */
        private String unit;
        /** 指标值 */
        private Double value;
        /** 天数值（高温/降雨/晴好/高湿/大风天数） */
        private Integer days;
        /** 天数显示名 */
        private String daysName;
    }
}
