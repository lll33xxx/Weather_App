package com.example.nl.mapper;

import com.example.nl.vo.RankingVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 天气数据 AI 模块 Mapper
 * 供 Coze 智能体插件调用：单城市汇总、省份筛选排名、省份/城市/年份元信息
 * 通过 ${} 动态传入表名与字段名，适配 5 组 ranking 表（表名/列名来自 WeatherMetric 枚举，非用户输入）
 */
@Mapper
public interface WeatherAiMapper {

    /**
     * 查询指定城市、年份在单张排名表中的指标值（取前1条）
     *
     * @param table     排名表名
     * @param metricCol 指标列名
     * @param daysCol   天数列名
     * @param city      城市名
     * @param year      年份
     */
    @Select("SELECT city, province, ROUND(${metricCol}, 2) AS metricValue, ${daysCol} AS days " +
            "FROM ${table} WHERE city LIKE CONCAT(#{city}, '%') AND year = #{year} ORDER BY rank_num ASC LIMIT 1")
    Map<String, Object> selectSummary(@Param("table") String table,
                                      @Param("metricCol") String metricCol,
                                      @Param("daysCol") String daysCol,
                                      @Param("city") String city,
                                      @Param("year") Integer year);

    /**
     * 查询指定年份、指定指标的城市排名，支持省份筛选
     *
     * @param table     排名表名
     * @param metricCol 指标列名
     * @param daysCol   天数列名
     * @param year      年份
     * @param province  省份筛选（可为null/空，为空时返回全国排名）
     * @param limit     取前N名
     */
    @Select("<script>" +
            "SELECT year, rank_num AS rankNum, city, province, " +
            "ROUND(${metricCol}, 2) AS metricValue, ${daysCol} AS days " +
            "FROM ${table} " +
            "<where>" +
            "  year = #{year}" +
            "  <if test='province != null and province != \"\"'> AND province LIKE CONCAT(#{province}, '%') </if>" +
            "</where>" +
            "ORDER BY rank_num ASC LIMIT #{limit}" +
            "</script>")
    List<RankingVO> selectRankingWithProvince(@Param("table") String table,
                                              @Param("metricCol") String metricCol,
                                              @Param("daysCol") String daysCol,
                                              @Param("year") Integer year,
                                              @Param("province") String province,
                                              @Param("limit") Integer limit);

    /**
     * 查询排名表中所有可用省份（去重升序）
     */
    @Select("SELECT DISTINCT province FROM ${table} ORDER BY province ASC")
    List<String> selectProvinces(@Param("table") String table);

    /**
     * 查询排名表中所有可用年份（去重升序）
     */
    @Select("SELECT DISTINCT year FROM ${table} ORDER BY year ASC")
    List<Integer> selectYears(@Param("table") String table);

    /**
     * 查询指定省份、指定年份的城市列表（去重升序）
     * 使用 LIKE 前缀匹配，支持"云南"匹配"云南省"、"广西"匹配"广西壮族自治区"
     */
    @Select("SELECT DISTINCT city FROM ${table} WHERE year = #{year} AND province LIKE CONCAT(#{province}, '%') ORDER BY city ASC")
    List<String> selectCitiesByProvince(@Param("table") String table,
                                        @Param("year") Integer year,
                                        @Param("province") String province);

    /**
     * 查询指定城市、指定日期的单指标日度数据（走 MySQL dwd 层）
     *
     * @param table    日度表名（dwd_xxx_daily）
     * @param dailyCol 日度指标列名
     * @param city     城市名（LIKE 前缀匹配）
     * @param date     日期（yyyy-MM-dd）
     */
    @Select("SELECT date_date, city, ROUND(${dailyCol}, 2) AS metricValue " +
            "FROM ${table} WHERE city LIKE CONCAT(#{city}, '%') AND date_date = #{date} LIMIT 1")
    Map<String, Object> selectDailyDetail(@Param("table") String table,
                                           @Param("dailyCol") String dailyCol,
                                           @Param("city") String city,
                                           @Param("date") String date);

    /**
     * 查询指定城市、年份、指标的全年逐日数据（走 MySQL dwd 层）
     */
    @Select("SELECT date_date, ROUND(${dailyCol}, 2) AS metricValue " +
            "FROM ${table} WHERE city LIKE CONCAT(#{city}, '%') AND date_date >= #{startDate} AND date_date < #{endDate} " +
            "ORDER BY date_date ASC")
    List<Map<String, Object>> selectDailyYear(@Param("table") String table,
                                               @Param("dailyCol") String dailyCol,
                                               @Param("city") String city,
                                               @Param("startDate") String startDate,
                                               @Param("endDate") String endDate);

    /**
     * 查询指定城市、指定指标的历年趋势数据（2000-2020）
     */
    @Select("SELECT city, year, ROUND(${metricCol}, 2) AS metricValue, ${daysCol} AS days, trend_label AS trendLabel " +
            "FROM ${table} WHERE city LIKE CONCAT(#{city}, '%') ORDER BY year ASC")
    List<Map<String, Object>> selectTrendData(@Param("table") String table,
                                               @Param("metricCol") String metricCol,
                                               @Param("daysCol") String daysCol,
                                               @Param("city") String city);

    /**
     * 查询指定城市、月份、指标的逐日数据
     */
    @Select("SELECT date_date, ROUND(${dailyCol}, 2) AS metricValue " +
            "FROM ${table} WHERE city LIKE CONCAT(#{city}, '%') AND date_date >= #{startDate} AND date_date <= #{endDate} " +
            "ORDER BY date_date ASC")
    List<Map<String, Object>> selectMonthlyDaily(@Param("table") String table,
                                                   @Param("dailyCol") String dailyCol,
                                                   @Param("city") String city,
                                                   @Param("startDate") String startDate,
                                                   @Param("endDate") String endDate);
}
