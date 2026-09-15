package com.example.nl.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 天气数据管理 Mapper
 * 面向后台数据管理，支持分页、年份/省份/城市筛选
 */
@Mapper
public interface WeatherDataMapper {

    /**
     * 分页查询排名表数据
     * 分页由 PageHelper 插件自动完成，此处无需传 offset/pageSize
     *
     * @param table     排名表名（动态）
     * @param metricCol 指标列名（动态）
     * @param daysCol   天数列名（动态）
     * @param year      年份筛选（可为null）
     * @param province  省份筛选（可为null）
     * @param city      城市模糊搜索（可为null）
     */
    @Select("<script>" +
            "SELECT year, rank_num AS rankNum, city, province, " +
            "ROUND(${metricCol}, 2) AS metricValue, ${daysCol} AS days " +
            "FROM ${table} " +
            "<where>" +
            "  <if test='year != null'> AND year = #{year} </if>" +
            "  <if test='province != null and province != \"\"'> AND province = #{province} </if>" +
            "  <if test='city != null and city != \"\"'> AND city LIKE CONCAT('%', #{city}, '%') </if>" +
            "</where>" +
            "ORDER BY year DESC, rank_num ASC" +
            "</script>")
    List<Map<String, Object>> selectDataPage(@Param("table") String table,
                                              @Param("metricCol") String metricCol,
                                              @Param("daysCol") String daysCol,
                                              @Param("year") Integer year,
                                              @Param("province") String province,
                                              @Param("city") String city);

    /**
     * 查询指定排名表的所有可用年份（去重降序）
     */
    @Select("SELECT DISTINCT year FROM ${table} ORDER BY year DESC")
    List<Integer> selectYears(@Param("table") String table);

    /**
     * 查询指定排名表的所有可用省份（去重升序）
     */
    @Select("SELECT DISTINCT province FROM ${table} ORDER BY province ASC")
    List<String> selectProvinces(@Param("table") String table);
}
