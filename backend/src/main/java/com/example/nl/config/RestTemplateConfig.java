package com.example.nl.config;

// [技术栈] Spring Core — @Configuration 标记为 Spring 配置类
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
// [技术栈] Spring Web — RestTemplate 用于调用 Coze 开放 API / 和风天气 API
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * RestTemplate 配置 - 供 Coze 智能体 API、和风天气 API 调用使用
 *
 * 默认 StringHttpMessageConverter 使用 ISO-8859-1 解码，中文会乱码，
 * 此处统一改为 UTF-8，保证与 Coze / 和风返回的中文内容正常解析。
 *
 * 同时补全连接超时与读取超时，避免第三方接口慢响应把请求线程拖死。
 *
 * 【整体技术栈】
 * - Spring @Configuration：配置类
 * - Spring @Bean：声明 RestTemplate Bean
 * - StringHttpMessageConverter：HTTP 字符串消息转换器
 * - SimpleClientHttpRequestFactory：设置连接/读取超时
 */
@Configuration
public class RestTemplateConfig {

    /** 连接超时：3 秒 */
    private static final int CONNECT_TIMEOUT_MS = 3000;
    /** 读取超时：10 秒（和风免费版偶发慢响应，留足余量） */
    private static final int READ_TIMEOUT_MS = 10000;

    /**
     * [技术栈] Spring Web — 构建 UTF-8 编码、带超时的 RestTemplate Bean
     */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);

        RestTemplate restTemplate = new RestTemplate(factory);
        // [技术栈] Spring Web — 遍历消息转换器，将 String 转换器默认编码改为 UTF-8
        List<HttpMessageConverter<?>> converters = restTemplate.getMessageConverters();
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof StringHttpMessageConverter) {
                ((StringHttpMessageConverter) converter).setDefaultCharset(StandardCharsets.UTF_8);
            }
        }
        return restTemplate;
    }
}
