package com.example.nl.vo;

import lombok.Data;

/**
 * 主要城市实时天气概览（首页地图散点用）
 */
@Data
public class CityOverviewVO {
    /** 城市名 */
    private String city;
    /** 经度 */
    private Double lon;
    /** 纬度 */
    private Double lat;
    /** 当前温度（℃） */
    private Double temp;
    /** 天气现象文字 */
    private String text;
    /** 天气图标代码 */
    private String icon;
}
