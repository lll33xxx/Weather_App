package com.example.nl.service.impl;

import com.example.nl.service.LivableService;
import com.example.nl.service.OllamaChatService;
import com.example.nl.service.RealtimeWeatherService;
import com.example.nl.service.WeatherAiService;
import com.example.nl.util.CityCoordUtil;
import com.example.nl.vo.DailyForecastVO;
import com.example.nl.vo.IndicesVO;
import com.example.nl.vo.RealtimeWeatherVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
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
import java.util.LinkedHashMap;
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

    @Autowired
    private RealtimeWeatherService realtimeWeatherService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 系统提示词
     */
    private static final String SYSTEM_PROMPT = """
            你是一个专业的天气数据智能助手，具备两类能力：
            A. 历史气象数据查询（2000-2020年全国城市）
            B. 实时天气与外出建议（和风天气实时API）

            ## 你的能力
            1. 历史天气指标咨询：查询某城市某年的降水量、风速、气温、日照、湿度
            2. 历史指标排名：查询某年某指标的城市排名，支持按省份筛选
            3. 宜居城市推荐：根据天气因素推荐某省份最适合/最不适合居住的城市
            4. 历史单日详情：查询某城市某一天的具体历史天气指标值
            5. 历年趋势分析：查询某城市某指标2000-2020年的逐年变化趋势
            6. 月逐日分析：查询某城市某年月某指标的逐日数据
            7. 实时天气：查询某城市当前实时天气和生活指数
            8. 未来7天预报：查询某城市未来7天天气预报
            9. 外出建议：根据实时天气和生活指数，回答"今天适合爬山/跑步/晾晒吗"

            ## 数据范围
            - 历史数据：2000-2020年，用户未指定时默认2020年
            - 实时数据：当前天气和未来7天预报，来自和风天气API
            - 指标：降水(mm)、风速(m/s)、气温(℃)、日照(h)、湿度(%)

            ## 回答规则
            1. 必须调用工具获取真实数据，禁止编造任何数据
            2. 用户未指定年份时默认使用2020年
            3. 用户未指定推荐数量时默认推荐3个城市
            4. 回答简洁清晰，数据带上单位
            5. 超出天气范围的问题礼貌拒绝

            ## 工具使用规则
            - get_city_summary：历史单城年度天气指标汇总
            - get_ranking：历史指标排名，metric用编码(rain/wind/temp/sun/humidity)
            - recommend_livable_city：推荐宜居城市
            - recommend_least_livable_city：查询最不宜居城市
            - get_daily_detail：历史某一天的全部5个指标
            - get_trend_analysis：历史历年趋势
            - get_monthly_daily：历史月逐日数据
            - get_realtime_weather：查询当前实时天气+生活指数，用户问"今天天气""现在气温""实时天气"时使用。city参数可选，不传则使用浏览器定位城市
            - get_7day_forecast：查询未来7天天气预报，用户问"未来一周""接下来几天""哪天适合出去玩"时使用
            - get_outdoor_advice：根据实时天气和生活指数给出外出建议，用户问"适合爬山吗""适合跑步吗""适合晾晒吗"时使用
            - get_meta：获取支持的指标、省份和年份范围

            ## 常见问题示例
            - "北京2020年天气怎么样" → get_city_summary
            - "2020年降水最多的城市" → get_ranking(metric=rain)
            - "云南哪个城市最适合居住" → recommend_livable_city
            - "2020年3月15日杭州天气" → get_daily_detail
            - "深圳近20年气温变化" → get_trend_analysis(metric=temp)
            - "今天福州天气怎么样" → get_realtime_weather(city=福州)
            - "现在气温多少" → get_realtime_weather（不传city，使用定位城市）
            - "未来一周北京适合出去玩吗" → get_7day_forecast(city=北京)
            - "今天适合爬山吗" → get_outdoor_advice(activity=爬山)
            - "明天适合跑步吗" → get_outdoor_advice(activity=跑步)
            - "那广州呢"（接上文）→ 结合上下文理解
            - "上海和北京哪个更宜居" → recommend_livable_city分别查询后对比

            ## 重要提醒
            - 城市名可以用简称（如"福州"="福州市"）
            - 绝对不要编造数据，工具返回什么就用什么组织回答
            - 实时天气和外出建议是当前天气，不是历史数据
            - 如果城市不在支持列表中，如实告知"暂不支持该城市的实时天气查询"
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

        // 工具9：实时天气
        ObjectNode tool9 = objectMapper.createObjectNode();
        tool9.put("type", "function");
        ObjectNode fn9 = tool9.putObject("function");
        fn9.put("name", "get_realtime_weather");
        fn9.put("description", "查询某城市当前实时天气和生活指数（穿衣/紫外线/运动/感冒），用户问今天天气、现在气温、实时天气时使用");
        ObjectNode params9 = fn9.putObject("parameters");
        params9.put("type", "object");
        ObjectNode props9 = params9.putObject("properties");
        props9.putObject("city").put("type", "string").put("description", "城市名称，如福州、北京，不传则使用浏览器定位城市");
        tools.add(tool9);

        // 工具10：7天预报
        ObjectNode tool10 = objectMapper.createObjectNode();
        tool10.put("type", "function");
        ObjectNode fn10 = tool10.putObject("function");
        fn10.put("name", "get_7day_forecast");
        fn10.put("description", "查询某城市未来7天天气预报（最高最低温、天气现象、风力），用户问未来一周、接下来几天、哪天适合出去玩时使用");
        ObjectNode params10 = fn10.putObject("parameters");
        params10.put("type", "object");
        ObjectNode props10 = params10.putObject("properties");
        props10.putObject("city").put("type", "string").put("description", "城市名称，如福州、北京");
        tools.add(tool10);

        // 工具11：外出建议
        ObjectNode tool11 = objectMapper.createObjectNode();
        tool11.put("type", "function");
        ObjectNode fn11 = tool11.putObject("function");
        fn11.put("name", "get_outdoor_advice");
        fn11.put("description", "根据实时天气和生活指数给出外出活动建议，用户问适合爬山吗、适合跑步吗、适合晾晒吗时使用");
        ObjectNode params11 = fn11.putObject("parameters");
        params11.put("type", "object");
        ObjectNode props11 = params11.putObject("properties");
        props11.putObject("city").put("type", "string").put("description", "城市名称，不传则使用浏览器定位城市");
        props11.putObject("activity").put("type", "string").put("description", "活动类型：爬山、跑步、晾晒、出行、出游等");
        ArrayNode req11 = params11.putArray("required");
        req11.add("activity");
        tools.add(tool11);

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
                case "get_realtime_weather": {
                    double[] coord = resolveCityCoord(args.path("city").asText(null));
                    if (coord == null) {
                        return "{\"error\": \"暂不支持该城市的实时天气查询，请换一个大城市试试\"}";
                    }
                    RealtimeWeatherVO rt = realtimeWeatherService.getRealtime(coord[0], coord[1]);
                    IndicesVO indices = realtimeWeatherService.getIndices(coord[0], coord[1]);
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("city", args.path("city").asText("当前位置"));
                    result.put("realtime", rt);
                    result.put("indices", indices != null ? indices.getItems() : new ArrayList<>());
                    return objectMapper.writeValueAsString(result);
                }
                case "get_7day_forecast": {
                    double[] coord = resolveCityCoord(args.path("city").asText(null));
                    if (coord == null) {
                        return "{\"error\": \"暂不支持该城市的实时天气查询，请换一个大城市试试\"}";
                    }
                    List<DailyForecastVO> daily = realtimeWeatherService.getDaily(coord[0], coord[1]);
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("city", args.path("city").asText("当前位置"));
                    result.put("forecast", daily);
                    return objectMapper.writeValueAsString(result);
                }
                case "get_outdoor_advice": {
                    double[] coord = resolveCityCoord(args.path("city").asText(null));
                    if (coord == null) {
                        return "{\"error\": \"暂不支持该城市的实时天气查询，请换一个大城市试试\"}";
                    }
                    String activity = args.path("activity").asText("");
                    RealtimeWeatherVO rt = realtimeWeatherService.getRealtime(coord[0], coord[1]);
                    IndicesVO indices = realtimeWeatherService.getIndices(coord[0], coord[1]);
                    List<DailyForecastVO> daily = realtimeWeatherService.getDaily(coord[0], coord[1]);
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("activity", activity);
                    result.put("realtime", rt);
                    result.put("indices", indices != null ? indices.getItems() : new ArrayList<>());
                    result.put("todayForecast", !daily.isEmpty() ? daily.get(0) : null);
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

    /**
     * 解析城市名为经纬度 [lon, lat]
     * 1. 优先用 CityCoordUtil 匹配城市名
     * 2. 未指定城市时从 Redis 读取浏览器定位
     * @return [lon, lat] 或 null
     */
    @SuppressWarnings("unchecked")
    private double[] resolveCityCoord(String city) {
        // 1. 指定了城市名
        if (city != null && !city.isBlank()) {
            double[] coord = CityCoordUtil.getCoord(city);
            if (coord != null) return coord;
        }
        // 2. 从 Redis 读取浏览器定位
        try {
            Object cached = redisTemplate.opsForValue().get("weather:user:location");
            if (cached instanceof Map<?, ?> map) {
                double lon = ((Number) map.get("lon")).doubleValue();
                double lat = ((Number) map.get("lat")).doubleValue();
                return new double[]{lon, lat};
            }
        } catch (Exception e) {
            log.warn("读取浏览器定位失败: {}", e.getMessage());
        }
        return null;
    }
}
