package com.example.nl.mapper;

import com.example.nl.vo.RankingVO;
import com.example.nl.vo.TrendVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 气象数据统一 Mapper
 * 通过 ${} 动态传入表名与字段名，适配 5 组 ranking/trend 表
 */
@Mapper
public interface WeatherMapper {

    /**
     * 查询指定年份、指定指标的城市排名（按排名升序，默认取前20）
     *
     * @param table     排名表名，如 ads_city_ranking
     * @param metricCol 指标列名，如 total_rain / avg_wind / avg_temp / total_sun / avg_humidity
     * @param daysCol   天数列名，如 rainy_days / windy_days / high_temp_days / sunny_days / high_hum_days
     * @param year      年份
     * @param limit     取前N名
     */
    @Select("SELECT year, rank_num AS rankNum, city, province, " +
            "ROUND(${metricCol}, 2) AS metricValue, ${daysCol} AS days " +
            "FROM ${table} WHERE year = #{year} ORDER BY rank_num ASC LIMIT #{limit}")
    List<RankingVO> selectRanking(@Param("table") String table,
                                   @Param("metricCol") String metricCol,
                                   @Param("daysCol") String daysCol,
                                   @Param("year") Integer year,
                                   @Param("limit") Integer limit);

    /**
     * 查询指定城市、指定指标的历年趋势
     *
     * @param table     趋势表名，如 ads_trend_analysis
     * @param metricCol 指标列名
     * @param daysCol   天数列名
     * @param city      城市名
     */
    @Select("SELECT city, year, ROUND(${metricCol}, 2) AS metricValue, ${daysCol} AS days, trend_label AS trendLabel " +
            "FROM ${table} WHERE city = #{city} ORDER BY year ASC")
    List<TrendVO> selectTrend(@Param("table") String table,
                               @Param("metricCol") String metricCol,
                               @Param("daysCol") String daysCol,
                               @Param("city") String city);

    /**
     * 查询排名表中所有可用年份（去重升序）
     */
    @Select("SELECT DISTINCT year FROM ${table} ORDER BY year ASC")
    List<Integer> selectYears(@Param("table") String table);

    /**
     * 查询趋势表中所有可用城市（去重）
     */
    @Select("SELECT DISTINCT city FROM ${table} ORDER BY city ASC")
    List<String> selectCities(@Param("table") String table);
}
