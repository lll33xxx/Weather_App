package com.example.nl.service;

import com.example.nl.vo.LivableCityVO;

import java.util.List;

/**
 * 宜居城市推荐业务层接口
 * 基于气候指标对指定省份城市进行多维度加权评分
 */
public interface LivableService {

    /**
     * 推荐指定省份最宜居的 TopN 城市
     *
     * @param province 省份（必填）
     * @param year     年份（默认2020）
     * @param limit    返回数量（默认3，用户指定时以用户为准）
     */
    List<LivableCityVO> recommend(String province, Integer year, Integer limit);

    /**
     * 查询指定省份最不宜居的 TopN 城市（评分最低）
     *
     * @param province 省份（必填）
     * @param year     年份（默认2020）
     * @param limit    返回数量（默认3）
     */
    List<LivableCityVO> recommendWorst(String province, Integer year, Integer limit);
}
