package com.example.nl.vo;

import lombok.Data;

import java.util.List;

/**
 * 天气预警聚合（右侧轮播卡片一）
 */
@Data
public class WarningVO {
    /** 是否有生效预警 */
    private boolean hasWarning;
    /** 预警列表（无预警时为空数组） */
    private List<Item> alerts;

    @Data
    public static class Item {
        /** 预警标题，例如"临桂区气象台更新大风蓝色预警信号" */
        private String headline;
        /** 预警事件名称，例如"大风" */
        private String eventType;
        /** 颜色代码：blue/yellow/orange/red */
        private String colorCode;
        /** 严重程度：minor/moderate/severe/extreme */
        private String severity;
        /** 发布时间 */
        private String issuedTime;
        /** 详细描述 */
        private String description;
    }
}
