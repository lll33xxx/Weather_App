package com.example.nl.vo;

import lombok.Data;

/**
 * 实时天气视图对象（首页右侧上半卡片）
 */
@Data
public class RealtimeWeatherVO {
    /** 天气现象文字（晴/多云/小雨 等） */
    private String text;
    /** 和风天气图标代码（100 晴 / 101 多云 等） */
    private String icon;
    /** 当前温度（℃） */
    private Double temp;
    /** 体感温度（℃） */
    private Double feelsLike;
    /** 相对湿度（%） */
    private Integer humidity;
    /** 风向中文（北风/东南风 等） */
    private String windDir;
    /** 蒲福风级（0-12） */
    private Integer windScale;
    /** 风速（km/h，和风 v1 返回 m/s，后端统一换算为 km/h） */
    private Double windSpeed;
    /** 观测/更新时间（ISO 字符串） */
    private String obsTime;
}
