package com.example.nl.vo;

import lombok.Data;

import java.util.Map;

/**
 * 宜居城市推荐VO - 按省份筛选、多指标加权评分后的推荐结果
 *
 * 评分参考 GB/T 42072-2022《气候宜居指数》与 QX/T 570-2020《气候宜居城镇》，
 * 简化为 5 项等权重（各20分）评分，详见 LivableServiceImpl
 */
@Data
public class LivableCityVO {

    /** 推荐排名 */
    private Integer rank;
    /** 城市 */
    private String city;
    /** 省份 */
    private String province;
    /** 宜居总分（0-100） */
    private Double score;
    /** 各分项得分（key 为指标编码 temp/rain/sun/humidity/wind，value 为 0-20 分） */
    private Map<String, Double> scores;
    /** 各指标实际值（key 为指标编码，value 为指标值） */
    private Map<String, Double> metrics;
    /** 简要推荐理由（基于优势项生成） */
    private String reason;
}
