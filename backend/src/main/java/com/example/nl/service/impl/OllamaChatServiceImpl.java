package com.example.nl.service.impl;

import com.example.nl.service.LivableService;
import com.example.nl.service.OllamaChatService;
import com.example.nl.service.WeatherAiService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ollama 本地大模型对话服务实现
 *
 * 流程（Function Calling）：
 * 1. 构造 system prompt + user 消息，带上 tools 定义
 * 2. 调用 Ollama /v1/chat/completions
 * 3. 若模型返回 tool_calls，执行对应查询，将结果追加为 tool 消息
 * 4. 再次调用模型获取最终自然语言回答
 *
 * 多轮对话：用 conversationId 作为 sessionId，维护消息历史
 */
@Service
public class OllamaChatServiceImpl implements OllamaChatService {

    private static final Logger log = LoggerFactory.getLogger(OllamaChatServiceImpl.class);

    @Value("${ollama.api-url:http://localhost:11434/v1/chat/completions}")
    private String apiUrl;

    @Value("${ollama.model:qwen2.5:7b}")
    private String model;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private WeatherAiService weatherAiService;

    @Autowired
    private LivableService livableService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 系统提示词
     */
    private static final String SYSTEM_PROMPT = """
            你是一个专业的天气数据智能助手，基于 2000-2020 年全国城市历史气象数据回答问题。

            ## 你的能力
            1. 天气指标咨询：查询某城市某年的降水量、风速、气温、日照、湿度
            2. 指标排名查询：查询某年某指标的城市排名，支持按省份筛选
            3. 宜居城市推荐：根据天气因素推荐某省份最适合居住的城市
            4. 不宜居城市查询：查询某省份最不适合居住的城市（气候评分最低）
            5. 单日天气详情：查询某城市某一天的具体天气指标值
            6. 历年趋势分析：查询某城市某指标2000-2020年的逐年变化趋势
            7. 月逐日分析：查询某城市某年月某指标的逐日数据，分析月内天气变化

            ## 数据范围
            - 时间：2000-2020 年，用户未指定时默认 2020 年
            - 指标：降水(mm)、风速(m/s)、气温(℃)、日照(h)、湿度(%)

            ## 回答规则
            1. 必须调用工具获取真实数据，禁止编造任何数据或评分
            2. 用户未指定年份时默认使用 2020 年
            3. 用户未指定推荐数量时默认推荐 3 个城市
            4. 回答简洁清晰，数据带上单位
            5. 宜居推荐要说明评分依据，列出每个城市的主要指标（气温、降水、湿度等具体数值）
            6. 超出天气范围的问题礼貌拒绝

            ## 工具使用规则
            - get_city_summary：查询单城天气指标（降水/风速/气温/日照/湿度），不返回宜居评分
            - get_ranking：查询指标排名，metric 必须用编码(rain/wind/temp/sun/humidity)
            - recommend_livable_city：推荐宜居城市，返回评分排名列表。如果用户问某个具体城市的宜居评分，设置较大的 limit（如20），然后在结果中找该城市；如果找不到，如实告知该城市未在评分列表中
            - recommend_least_livable_city：查询最不适合居住的城市（评分最低），用户问"不适合居住""最差""气候最差"等问题时使用
            - get_daily_detail：查询某城市某一天的全部5个天气指标（气温、降水、湿度、风速、日照），用户问某一天的天气时直接调用此工具，它会一次性返回全部指标，不要问用户想知道哪个指标
            - get_trend_analysis：查询某城市某指标的历年变化趋势，用户问"趋势""变化""逐年""20年"等问题时使用，metric 必须用编码(rain/wind/temp/sun/humidity)
            - get_monthly_daily：查询某城市某年月某指标的逐日数据，用户问"某月份变化""1月份风速""月内趋势"时使用
            - get_meta：获取支持的指标、省份和年份范围

            ## 常见问题示例
            - "北京2020年天气怎么样" → get_city_summary
            - "上海降水量多少" → get_city_summary（未指定年份默认2020）
            - "2020年降水最多的城市" → get_ranking(metric=rain)
            - "福建省气温排名前十" → get_ranking(metric=temp, province=福建省, limit=10)
            - "云南哪个城市最适合居住" → recommend_livable_city(province=云南省)
            - "广东气候最差的城市" → recommend_least_livable_city(province=广东省)
            - "成都宜居评分多少" → recommend_livable_city(province=四川省, limit=20)，在结果中找成都
            - "2020年3月15日杭州天气" → get_daily_detail(date=2020-03-15)
            - "深圳近20年气温变化" → get_trend_analysis(metric=temp)
            - "2019年7月武汉气温怎么变的" → get_monthly_daily(year=2019, month=7, metric=temp)
            - "那广州呢"（接上文）→ 结合上下文理解，调用对应工具
            - "上海和北京哪个更宜居" → recommend_livable_city 分别查询后对比

            ## 重要提醒
            - 城市名和省份名可以用简称（如"福州"="福州市"，"福建"="福建省"）
            - 绝对不要编造数据，工具返回数据就用什么数据来组织自然语言
            - 用户提到"那它呢""那XX呢"时，结合上下文理解用户在问什么
            - 比较两个城市时，分别调用工具查询后再对比
            """;

    /**
     * 定义可用工具（Function Calling tools schema）
     */
    private ArrayNode buildTools() {
        ArrayNode tools = objectMapper.createArrayNode();

        // 工具1：查询单城天气汇总
        ObjectNode tool1 = objectMapper.createObjectNode();
        tool1.put("type", "function");
        ObjectNode fn1 = tool1.putObject("function");
        fn1.put("name", "get_city_summary");
        fn1.put("description", "查询指定城市指定年份的全部天气指标汇总，包括降水量、风速、气温、日照、湿度");
        ObjectNode params1 = fn1.putObject("parameters");
        params1.put("type", "object");
        ObjectNode props1 = params1.putObject("properties");
        props1.putObject("city").put("type", "string").put("description", "城市名称，如北京市、上海市、保山市");
        props1.putObject("year").put("type", "integer").put("description", "年份，默认2020，范围2000-2020");
        ArrayNode req1 = params1.putArray("required");
        req1.add("city");
        tools.add(tool1);

        // 工具2：查询指标排名
        ObjectNode tool2 = objectMapper.createObjectNode();
        tool2.put("type", "function");
        ObjectNode fn2 = tool2.putObject("function");
        fn2.put("name", "get_ranking");
        fn2.put("description", "查询指定年份指定天气指标的城市排名，支持按省份筛选。指标编码：rain降水、wind风速、temp气温、sun日照、humidity湿度");
        ObjectNode params2 = fn2.putObject("parameters");
        params2.put("type", "object");
        ObjectNode props2 = params2.putObject("properties");
        props2.putObject("metric").put("type", "string").put("description", "指标编码：rain/wind/temp/sun/humidity");
        props2.putObject("year").put("type", "integer").put("description", "年份，默认2020");
        props2.putObject("province").put("type", "string").put("description", "省份筛选，如云南、广东省，不传则全国");
        props2.putObject("limit").put("type", "integer").put("description", "返回条数，默认10");
        ArrayNode req2 = params2.putArray("required");
        req2.add("metric");
        tools.add(tool2);

        // 工具3：推荐宜居城市
        ObjectNode tool3 = objectMapper.createObjectNode();
        tool3.put("type", "function");
        ObjectNode fn3 = tool3.putObject("function");
        fn3.put("name", "recommend_livable_city");
        fn3.put("description", "根据天气因素推荐指定省份最适合居住的城市，基于气温、湿度、降水、日照、风速五项综合评分");
        ObjectNode params3 = fn3.putObject("parameters");
        params3.put("type", "object");
        ObjectNode props3 = params3.putObject("properties");
        props3.putObject("province").put("type", "string").put("description", "省份名称，如云南、四川省");
        props3.putObject("year").put("type", "integer").put("description", "年份，默认2020");
        props3.putObject("limit").put("type", "integer").put("description", "推荐数量，默认3");
        ArrayNode req3 = params3.putArray("required");
        req3.add("province");
        tools.add(tool3);

        // 工具4：查询最不宜居城市
        ObjectNode tool5 = objectMapper.createObjectNode();
        tool5.put("type", "function");
        ObjectNode fn5 = tool5.putObject("function");
        fn5.put("name", "recommend_least_livable_city");
        fn5.put("description", "查询指定省份最不适合居住的城市（气候评分最低），基于气温、湿度、降水、日照、风速五项综合评分");
        ObjectNode params5 = fn5.putObject("parameters");
        params5.put("type", "object");
        ObjectNode props5 = params5.putObject("properties");
        props5.putObject("province").put("type", "string").put("description", "省份名称，如云南、四川省");
        props5.putObject("year").put("type", "integer").put("description", "年份，默认2020");
        props5.putObject("limit").put("type", "integer").put("description", "返回数量，默认3");
        ArrayNode req5 = params5.putArray("required");
        req5.add("province");
        tools.add(tool5);

        // 工具6：查询单日详情
        ObjectNode tool6 = objectMapper.createObjectNode();
        tool6.put("type", "function");
        ObjectNode fn6 = tool6.putObject("function");
        fn6.put("name", "get_daily_detail");
        fn6.put("description", "查询指定城市某一天的全部天气指标（气温、降水、风速、日照、湿度），用户问具体某一天的数据时使用");
        ObjectNode params6 = fn6.putObject("parameters");
        params6.put("type", "object");
        ObjectNode props6 = params6.putObject("properties");
        props6.putObject("city").put("type", "string").put("description", "城市名称，如北京市、福州市");
        props6.putObject("date").put("type", "string").put("description", "日期，格式yyyy-MM-dd，如2020-07-15");
        ArrayNode req6 = params6.putArray("required");
        req6.add("city");
        req6.add("date");
        tools.add(tool6);

        // 工具7：趋势分析
        ObjectNode tool7 = objectMapper.createObjectNode();
        tool7.put("type", "function");
        ObjectNode fn7 = tool7.putObject("function");
        fn7.put("name", "get_trend_analysis");
        fn7.put("description", "查询指定城市某指标在2000-2020年的历年变化趋势，用户问趋势、变化、逐年对比时使用");
        ObjectNode params7 = fn7.putObject("parameters");
        params7.put("type", "object");
        ObjectNode props7 = params7.putObject("properties");
        props7.putObject("city").put("type", "string").put("description", "城市名称，如北京市、福州市");
        props7.putObject("metric").put("type", "string").put("description", "指标编码：rain/wind/temp/sun/humidity");
        ArrayNode req7 = params7.putArray("required");
        req7.add("city");
        req7.add("metric");
        tools.add(tool7);

        // 工具8：月逐日数据
        ObjectNode tool8 = objectMapper.createObjectNode();
        tool8.put("type", "function");
        ObjectNode fn8 = tool8.putObject("function");
        fn8.put("name", "get_monthly_daily");
        fn8.put("description", "查询指定城市某年月某指标的逐日数据，用于分析一个月内的天气变化趋势");
        ObjectNode params8 = fn8.putObject("parameters");
        params8.put("type", "object");
        ObjectNode props8 = params8.putObject("properties");
        props8.putObject("city").put("type", "string").put("description", "城市名称");
        props8.putObject("year").put("type", "integer").put("description", "年份");
        props8.putObject("month").put("type", "integer").put("description", "月份1-12");
        props8.putObject("metric").put("type", "string").put("description", "指标编码：rain/wind/temp/sun/humidity");
        ArrayNode req8 = params8.putArray("required");
        req8.add("city");
        req8.add("year");
        req8.add("month");
        req8.add("metric");
        tools.add(tool8);

        // 工具5：获取元信息
        ObjectNode tool4 = objectMapper.createObjectNode();
        tool4.put("type", "function");
        ObjectNode fn4 = tool4.putObject("function");
        fn4.put("name", "get_meta");
        fn4.put("description", "获取系统支持的天气指标列表、省份列表和年份范围");
        ObjectNode params4 = fn4.putObject("parameters");
        params4.put("type", "object");
        tools.add(tool4);

        return tools;
    }

    @Override
    public Map<String, String> chat(String userId, String message, String conversationId) {
        // 生成或复用会话ID
        String convId = (conversationId == null || conversationId.isBlank())
                ? UUID.randomUUID().toString() : conversationId;
        String redisKey = "nl:chat:" + convId;

        // 从 Redis 读取历史消息（不含 system）
        List<JsonNode> history = new ArrayList<>();
        try {
            String saved = stringRedisTemplate.opsForValue().get(redisKey);
            if (saved != null && !saved.isBlank()) {
                ArrayNode savedArr = (ArrayNode) objectMapper.readTree(saved);
                for (JsonNode node : savedArr) {
                    history.add(node);
                }
            }
        } catch (Exception e) {
            log.warn("读取对话历史失败，将以新会话开始", e);
        }

        // 构造完整消息列表：system + 历史 + 新 user
        List<JsonNode> messages = new ArrayList<>();
        ObjectNode systemMsg = objectMapper.createObjectNode();
        systemMsg.put("role", "system");
        systemMsg.put("content", SYSTEM_PROMPT);
        messages.add(systemMsg);
        messages.addAll(history);

        ObjectNode userMsg = objectMapper.createObjectNode();
        userMsg.put("role", "user");
        userMsg.put("content", message);
        messages.add(userMsg);

        int maxToolRounds = 5;
        String finalReply = null;

        for (int round = 0; round < maxToolRounds; round++) {
            String reply = callOllama(messages);
            try {
                JsonNode root = objectMapper.readTree(reply);
                JsonNode choice = root.path("choices").path(0);
                String finishReason = choice.path("finish_reason").asText();
                JsonNode toolCalls = choice.path("message").path("tool_calls");

                if ("tool_calls".equals(finishReason) && toolCalls.isArray() && toolCalls.size() > 0) {
                    messages.add(choice.path("message"));
                    for (JsonNode toolCall : toolCalls) {
                        String toolName = toolCall.path("function").path("name").asText();
                        String argsJson = toolCall.path("function").path("arguments").asText("{}");
                        String toolCallId = toolCall.path("id").asText();
                        log.info("调用工具: {}, 参数: {}", toolName, argsJson);
                        String toolResult = executeTool(toolName, argsJson);
                        ObjectNode toolMsg = objectMapper.createObjectNode();
                        toolMsg.put("role", "tool");
                        toolMsg.put("tool_call_id", toolCallId);
                        toolMsg.put("name", toolName);
                        toolMsg.put("content", toolResult);
                        messages.add(toolMsg);
                    }
                    continue;
                }

                // 兼容文本格式的工具调用（qwen2.5 有时输出 <tool_call>...</tool_call>）
                String content = choice.path("message").path("content").asText("");
                String toolCallText = extractToolCall(content);
                if (toolCallText != null) {
                    log.info("检测到文本格式工具调用: {}", toolCallText);
                    // 解析工具调用 JSON
                    JsonNode toolCallNode = objectMapper.readTree(toolCallText);
                    String toolName = toolCallNode.path("name").asText();
                    String argsJson = toolCallNode.path("arguments").toString();
                    String toolResult = executeTool(toolName, argsJson);
                    // 把工具结果作为 user 消息追加，让模型基于结果回答
                    ObjectNode toolMsg = objectMapper.createObjectNode();
                    toolMsg.put("role", "user");
                    toolMsg.put("content", "工具 " + toolName + " 返回结果：" + toolResult);
                    messages.add(toolMsg);
                    continue;
                }

                // 没有工具调用，直接返回回答
                finalReply = content;
                if (finalReply.isBlank()) {
                    finalReply = "抱歉，暂时没有获取到答案，请换个问法试试。";
                }
                // 清理 content 中可能残留的工具调用标记
                finalReply = finalReply.replaceAll("<tool_call>.*?</tool_call>", "").trim();
                messages.add(choice.path("message"));
                break;
            } catch (Exception e) {
                log.error("解析 Ollama 响应失败", e);
                throw new IllegalStateException("智能体响应解析失败，请稍后重试");
            }
        }

        if (finalReply == null) {
            throw new IllegalStateException("工具调用次数过多，请换个问法");
        }

        // 保存历史到 Redis（去掉 system 消息，只保留对话部分）
        try {
            ArrayNode historyToSave = objectMapper.createArrayNode();
            // 把 user 消息和后续消息都存回去（跳过第0条 system）
            for (int i = 1; i < messages.size(); i++) {
                historyToSave.add(messages.get(i));
            }
            // 限制最多保留 20 条，防止上下文过长
            if (historyToSave.size() > 20) {
                ArrayNode trimmed = objectMapper.createArrayNode();
                for (int i = historyToSave.size() - 20; i < historyToSave.size(); i++) {
                    trimmed.add(historyToSave.get(i));
                }
                historyToSave = trimmed;
            }
            stringRedisTemplate.opsForValue().set(redisKey, objectMapper.writeValueAsString(historyToSave), Duration.ofMinutes(30));
        } catch (Exception e) {
            log.warn("保存对话历史失败", e);
        }

        return Map.of("reply", finalReply, "conversationId", convId);
    }

    /**
     * 调用 Ollama API
     */
    private String callOllama(List<JsonNode> messages) {
        try {
                ObjectNode requestBody = objectMapper.createObjectNode();
                requestBody.put("model", model);
                ArrayNode messagesArr = requestBody.putArray("messages");
                messagesArr.addAll(messages);
                requestBody.put("stream", false);
                requestBody.put("tool_choice", "auto");
                ArrayNode toolsArr = requestBody.putArray("tools");
                toolsArr.addAll(buildTools());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);

            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, request, String.class);
            return response.getBody();
        } catch (HttpClientErrorException e) {
            log.error("Ollama API 错误, status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            int code = e.getStatusCode().value();
            if (code == 404) throw new IllegalStateException("智能体服务地址不存在，请检查 Ollama 是否运行");
            if (code == 401 || code == 403) throw new IllegalStateException("智能体鉴权失败");
            throw new IllegalStateException("智能体服务异常（HTTP " + code + "）");
        } catch (ResourceAccessException e) {
            log.error("Ollama 连接失败", e);
            throw new IllegalStateException("无法连接本地模型服务，请确认 Ollama 已启动");
        } catch (Exception e) {
            log.error("Ollama 调用异常", e);
            throw new IllegalStateException("智能体服务暂时不可用，请稍后重试");
        }
    }

    /**
     * 执行工具调用，返回 JSON 字符串结果
     */
    private String executeTool(String toolName, String argsJson) {
        try {
            JsonNode args = objectMapper.readTree(argsJson);

            switch (toolName) {
                case "get_city_summary": {
                    String city = args.path("city").asText();
                    int year = args.path("year").asInt(2020);
                    var result = weatherAiService.getSummary(city, year);
                    return objectMapper.writeValueAsString(result);
                }
                case "get_ranking": {
                    String metric = args.path("metric").asText();
                    int year = args.path("year").asInt(2020);
                    String province = args.has("province") && !args.path("province").asText().isBlank()
                            ? args.path("province").asText() : null;
                    int limit = args.path("limit").asInt(10);
                    var result = weatherAiService.getRanking(metric, year, province, limit);
                    return objectMapper.writeValueAsString(result);
                }
                case "recommend_livable_city": {
                    String province = args.path("province").asText();
                    int year = args.path("year").asInt(2020);
                    int limit = args.path("limit").asInt(3);
                    var result = livableService.recommend(province, year, limit);
                    return objectMapper.writeValueAsString(result);
                }
                case "recommend_least_livable_city": {
                    String province = args.path("province").asText();
                    int year = args.path("year").asInt(2020);
                    int limit = args.path("limit").asInt(3);
                    var result = livableService.recommendWorst(province, year, limit);
                    return objectMapper.writeValueAsString(result);
                }
                case "get_daily_detail": {
                    String city = args.path("city").asText();
                    String date = args.path("date").asText();
                    var result = weatherAiService.getDailyDetail(city, date);
                    return objectMapper.writeValueAsString(result);
                }
                case "get_trend_analysis": {
                    String city = args.path("city").asText();
                    String metric = args.path("metric").asText();
                    var result = weatherAiService.getTrendAnalysis(city, metric);
                    return objectMapper.writeValueAsString(result);
                }
                case "get_monthly_daily": {
                    String city = args.path("city").asText();
                    int year = args.path("year").asInt();
                    int month = args.path("month").asInt();
                    String metric = args.path("metric").asText();
                    var result = weatherAiService.getMonthlyDaily(city, year, month, metric);
                    return objectMapper.writeValueAsString(result);
                }
                case "get_meta": {
                    var result = weatherAiService.getMeta();
                    return objectMapper.writeValueAsString(result);
                }
                default:
                    return "{\"error\": \"未知工具: " + toolName + "\"}";
            }
        } catch (Exception e) {
            log.error("执行工具 {} 失败", toolName, e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    /**
     * 从模型文本输出中提取工具调用 JSON
     * 兼容多种格式：<tool_call>...</tool_call>、裸 JSON、不完整标签等
     */
    private String extractToolCall(String content) {
        if (content == null) return null;
        // 优先匹配标准 <tool_call>...</tool_call>
        int start = content.indexOf("<tool_call>");
        int end = content.indexOf("</tool_call>");
        if (start >= 0 && end > start) {
            return content.substring(start + 11, end).trim();
        }
        // 只匹配到 </tool_call>，往前找 JSON
        if (end > start) {
            // 从结尾往前找 {
            String beforeEnd = content.substring(0, end);
            int braceStart = beforeEnd.lastIndexOf("{\"name\"");
            if (braceStart >= 0) {
                return beforeEnd.substring(braceStart).trim();
            }
        }
        // 裸 JSON 格式：{"name": "xxx", "arguments": {...}}
        int jsonStart = content.indexOf("{\"name\"");
        if (jsonStart >= 0) {
            // 找到匹配的闭合大括号
            int braceCount = 0;
            for (int i = jsonStart; i < content.length(); i++) {
                char c = content.charAt(i);
                if (c == '{') braceCount++;
                if (c == '}') braceCount--;
                if (braceCount == 0) {
                    return content.substring(jsonStart, i + 1).trim();
                }
            }
        }
        return null;
    }
}
