package com.example.nl.enums;

import lombok.Getter;

/**
 * 气象指标枚举 - 统一管理5类指标对应的表名、字段名、显示名与单位
 */
@Getter
public enum WeatherMetric {

    RAIN("rain", "降水", "年总降水量", "mm",
            "ads_city_ranking", "ads_trend_analysis",
            "total_rain", "rainy_days", "降雨天数",
            "dwd_rain_daily", "precip", "日降水量"),

    WIND("wind", "风速", "年平均风速", "m/s",
            "ads_wind_ranking", "ads_wind_trend",
            "avg_wind", "windy_days", "大风天数",
            "dwd_wind_daily", "avg_wind", "日平均风速"),

    TEMP("temp", "气温", "年平均气温", "℃",
            "ads_temp_ranking", "ads_temp_trend",
            "avg_temp", "high_temp_days", "高温天数",
            "dwd_temp_daily", "avg_temp", "日平均气温"),

    SUN("sun", "日照", "年总日照时长", "h",
            "ads_sun_ranking", "ads_sun_trend",
            "total_sun", "sunny_days", "晴好天数",
            "dwd_sun_daily", "sunshine", "日日照时长"),

    HUMIDITY("humidity", "湿度", "年平均相对湿度", "%",
            "ads_humidity_ranking", "ads_humidity_trend",
            "avg_humidity", "high_hum_days", "高湿天数",
            "dwd_humidity_daily", "humidity", "日平均湿度");

    /** 指标编码（前端传参用） */
    private final String code;
    /** 指标简称 */
    private final String shortName;
    /** 指标显示全称 */
    private final String displayName;
    /** 指标单位 */
    private final String unit;
    /** 排名表名 */
    private final String rankingTable;
    /** 趋势表名 */
    private final String trendTable;
    /** 指标值列名 */
    private final String metricCol;
    /** 天数列名 */
    private final String daysCol;
    /** 天数显示名 */
    private final String daysName;
    /** 日度明细表名（Hive dwd层） */
    private final String dailyTable;
    /** 日度指标列名 */
    private final String dailyCol;
    /** 日度指标显示名 */
    private final String dailyColName;

    WeatherMetric(String code, String shortName, String displayName, String unit,
                  String rankingTable, String trendTable,
                  String metricCol, String daysCol, String daysName,
                  String dailyTable, String dailyCol, String dailyColName) {
        this.code = code;
        this.shortName = shortName;
        this.displayName = displayName;
        this.unit = unit;
        this.rankingTable = rankingTable;
        this.trendTable = trendTable;
        this.metricCol = metricCol;
        this.daysCol = daysCol;
        this.daysName = daysName;
        this.dailyTable = dailyTable;
        this.dailyCol = dailyCol;
        this.dailyColName = dailyColName;
    }

    /** 根据 code 查找枚举，找不到返回 null */
    public static WeatherMetric fromCode(String code) {
        if (code == null) return null;
        for (WeatherMetric m : values()) {
            if (m.code.equalsIgnoreCase(code)) return m;
        }
        return null;
    }
}
