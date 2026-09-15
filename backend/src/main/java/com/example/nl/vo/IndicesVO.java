package com.example.nl.vo;

import lombok.Data;

import java.util.List;

/**
 * 生活指数聚合（右侧轮播卡片二）
 * 只保留用户指定的 4 项：穿衣、紫外线、运动、感冒
 */
@Data
public class IndicesVO {
    private List<Item> items;

    @Data
    public static class Item {
        /** 指数名称：穿衣/紫外线/运动/感冒 */
        private String name;
        /** 等级描述：较不宜/适宜 等 */
        private String category;
        /** 详细建议 */
        private String text;
    }
}
