package com.example.nl.vo;

import lombok.Data;

/**
 * 7 天预报单日（左侧图表柱状数据）
 */
@Data
public class DailyForecastVO {
    /** 预报日期（yyyy-MM-dd） */
    private String date;
    /** 星期几（周二/周三...） */
    private String week;
    /** 白天天气现象 */
    private String dayText;
    /** 白天天气图标代码 */
    private String dayIcon;
    /** 夜间天气现象 */
    private String nightText;
    /** 夜间天气图标代码 */
    private String nightIcon;
    /** 最高温度（℃） */
    private Double tempMax;
    /** 最低温度（℃） */
    private Double tempMin;
    /** 风向中文 */
    private String windDir;
    /** 蒲福风级 */
    private Integer windScale;
    /** 日出时间 HH:mm */
    private String sunrise;
    /** 日落时间 HH:mm */
    private String sunset;
}
