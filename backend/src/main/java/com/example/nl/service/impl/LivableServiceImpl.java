package com.example.nl.service.impl;

import com.example.nl.enums.WeatherMetric;
import com.example.nl.mapper.WeatherAiMapper;
import com.example.nl.service.LivableService;
import com.example.nl.vo.LivableCityVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 宜居城市推荐业务层实现
 *
 * 评分说明（参考 GB/T 42072-2022《气候宜居指数》、QX/T 570-2020《气候宜居城镇》简化）：
 * - 5 项指标等权重，每项满分 20 分，总分 100 分；
 * - 气温：年平均气温越接近 18℃ 得分越高，高温天数超过 30 天扣分；
 * - 湿度：年平均相对湿度 50%-75% 得满分，偏离扣分；
 * - 降水：年总降水量 800-1600mm 得满分，偏离扣分；
 * - 日照：年总日照时长 1800-2600h 得满分，偏离扣分；
 * - 风速：年平均风速越小得分越高，大风天数超过 20 天扣分。
 * 任一指标准确值缺失时跳过该城市，保证评分公平。
 */
@Service
public class LivableServiceImpl implements LivableService {

    /** 各指标满分（等权重） */
    private static final double FULL_SCORE = 20.0;

    @Autowired
    private WeatherAiMapper weatherAiMapper;

    /**
     * 推荐指定省份最宜居的 TopN 城市
     * 以气温表为基准获取省份城市列表，逐城计算 5 项评分后按总分排序
     */
    @Cacheable(value = "ai:livable", key = "#province + '_' + #year + '_' + #limit")
    @Override
    public List<LivableCityVO> recommend(String province, Integer year, Integer limit) {
        int targetYear = (year == null) ? 2020 : year;
        int lim = (limit == null || limit <= 0) ? 3 : limit;

        List<String> cities = weatherAiMapper.selectCitiesByProvince(
                WeatherMetric.TEMP.getRankingTable(), targetYear, province);
        if (cities == null || cities.isEmpty()) {
            return new ArrayList<>();
        }

        List<LivableCityVO> results = new ArrayList<>();
        for (String city : cities) {
            LivableCityVO vo = buildScore(city, province, targetYear);
            if (vo != null) {
                results.add(vo);
            }
        }

        results.sort(Comparator.comparing(LivableCityVO::getScore).reversed());
        List<LivableCityVO> top = new ArrayList<>(results.subList(0, Math.min(lim, results.size())));
        for (int i = 0; i < top.size(); i++) {
            LivableCityVO vo = top.get(i);
            vo.setRank(i + 1);
            vo.setReason(buildReason(vo.getScores()));
        }
        return top;
    }

    /**
     * 查询指定省份最不宜居的 TopN 城市（评分最低）
     */
    @Cacheable(value = "ai:livable_worst", key = "#province + '_' + #year + '_' + #limit")
    @Override
    public List<LivableCityVO> recommendWorst(String province, Integer year, Integer limit) {
        int targetYear = (year == null) ? 2020 : year;
        int lim = (limit == null || limit <= 0) ? 3 : limit;

        List<String> cities = weatherAiMapper.selectCitiesByProvince(
                WeatherMetric.TEMP.getRankingTable(), targetYear, province);
        if (cities == null || cities.isEmpty()) {
            return new ArrayList<>();
        }

        List<LivableCityVO> results = new ArrayList<>();
        for (String city : cities) {
            LivableCityVO vo = buildScore(city, province, targetYear);
            if (vo != null) {
                results.add(vo);
            }
        }

        // 按总分升序（评分最低=最不宜居）
        results.sort(Comparator.comparing(LivableCityVO::getScore));
        List<LivableCityVO> worst = new ArrayList<>(results.subList(0, Math.min(lim, results.size())));
        for (int i = 0; i < worst.size(); i++) {
            LivableCityVO vo = worst.get(i);
            vo.setRank(i + 1);
            vo.setReason(buildWorstReason(vo.getScores()));
        }
        return worst;
    }

    /**
     * 计算单个城市的宜居评分
     *
     * @return 评分结果；任一指标准确值缺失时返回 null
     */
    private LivableCityVO buildScore(String city, String province, int year) {
        Map<String, Double> scores = new LinkedHashMap<>();
        Map<String, Double> metrics = new LinkedHashMap<>();

        for (WeatherMetric metric : WeatherMetric.values()) {
            Map<String, Object> row = weatherAiMapper.selectSummary(
                    metric.getRankingTable(), metric.getMetricCol(), metric.getDaysCol(),
                    city, year);
            if (row == null || row.get("metricValue") == null) {
                return null;
            }
            double value = ((Number) row.get("metricValue")).doubleValue();
            Integer days = (Integer) row.get("days");
            metrics.put(metric.getCode(), value);
            scores.put(metric.getCode(), scoreMetric(metric, value, days));
        }

        LivableCityVO vo = new LivableCityVO();
        vo.setCity(city);
        vo.setProvince(province);
        vo.setMetrics(metrics);
        vo.setScores(scores);
        vo.setScore(scores.values().stream().mapToDouble(Double::doubleValue).sum());
        return vo;
    }

    /**
     * 单指标评分（0-20分）
     */
    private double scoreMetric(WeatherMetric metric, double value, Integer days) {
        double score = switch (metric) {
            case TEMP -> {
                double s = FULL_SCORE - Math.abs(value - 18.0) * 1.2;
                if (days != null && days > 30) {
                    s -= (days - 30) * 0.1;
                }
                yield s;
            }
            case HUMIDITY -> {
                double s = FULL_SCORE;
                if (value < 50) {
                    s -= (50 - value) * 0.8;
                } else if (value > 75) {
                    s -= (value - 75) * 0.6;
                }
                yield s;
            }
            case RAIN -> {
                double s = FULL_SCORE;
                if (value < 800) {
                    s -= (800 - value) / 100.0;
                } else if (value > 1600) {
                    s -= (value - 1600) / 100.0;
                }
                yield s;
            }
            case SUN -> {
                double s = FULL_SCORE;
                if (value < 1800) {
                    s -= (1800 - value) / 100.0 * 0.8;
                } else if (value > 2600) {
                    s -= (value - 2600) / 100.0 * 0.8;
                }
                yield s;
            }
            case WIND -> {
                double s = FULL_SCORE - Math.max(0, (value - 2.5) / 0.5) * 2.0;
                if (days != null && days > 20) {
                    s -= (days - 20) * 0.1;
                }
                yield s;
            }
        };
        return Math.max(0, Math.round(score * 10.0) / 10.0);
    }

    /**
     * 基于分项得分生成简要推荐理由（取得分最高的 2 个维度）
     */
    private String buildReason(Map<String, Double> scores) {
        String[] desc = {
                "temp", "气温温和",
                "humidity", "湿度舒适",
                "rain", "降水适中",
                "sun", "日照充足",
                "wind", "风速平缓"
        };
        List<String> highlights = scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(2)
                .map(e -> {
                    for (int i = 0; i < desc.length; i += 2) {
                        if (desc[i].equals(e.getKey())) {
                            return desc[i + 1];
                        }
                    }
                    return e.getKey();
                })
                .toList();
        return String.join("、", highlights) + "，气候宜居";
    }

    /**
     * 基于分项得分生成不适合居住的原因（取得分最低的 2 个维度）
     */
    private String buildWorstReason(Map<String, Double> scores) {
        String[] desc = {
                "temp", "气温不适",
                "humidity", "湿度不佳",
                "rain", "降水异常",
                "sun", "日照不适",
                "wind", "风速较大"
        };
        List<String> lowlights = scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue())
                .limit(2)
                .map(e -> {
                    for (int i = 0; i < desc.length; i += 2) {
                        if (desc[i].equals(e.getKey())) {
                            return desc[i + 1];
                        }
                    }
                    return e.getKey();
                })
                .toList();
        return String.join("、", lowlights) + "，气候条件较差";
    }
}
