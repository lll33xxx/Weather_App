package com.example.nl.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 和风天气（QWeather）配置属性
 * 对应 application.properties 中 weather.qweather.* 配置项
 *
 * 【官方文档】https://dev.qweather.com/docs/configuration/api-config/
 * - Host：控制台每个项目分配的独立 API Host
 * - 认证：请求头 X-QW-Api-Key: <API_KEY>
 * - 所有接口均使用 Gzip 压缩传输，RestTemplate 默认 Accept-Encoding 会自动解压
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "weather.qweather")
public class WeatherProperties {

    /**
     * 和风天气 API Host，例如 https://xxxxxxxx.re.qweatherapi.com
     */
    private String host;

    /**
     * API KEY（控制台凭据页获取）
     */
    private String apiKey;

    /**
     * 实时天气缓存秒数（默认 30 分钟）
     */
    private long cacheTtlSeconds = 1800;

    /**
     * 预警 / 生活指数缓存秒数（默认 10 分钟，预警变化较快）
     */
    private long alertCacheTtlSeconds = 600;
}
