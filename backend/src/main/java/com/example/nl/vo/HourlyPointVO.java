package com.example.nl.vo;

import lombok.Data;

/**
 * 24 小时预报单点（左侧图表折线数据）
 */
@Data
public class HourlyPointVO {
    /** 预报时间（ISO，本地时间） */
    private String time;
    /** 温度（℃） */
    private Double temp;
    /** 天气现象文字 */
    private String text;
    /** 天气图标代码 */
    private String icon;
    /** 降水概率（%，0-100） */
    private Integer precipProb;
}
