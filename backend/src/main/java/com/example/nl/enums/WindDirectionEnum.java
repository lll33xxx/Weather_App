package com.example.nl.enums;

import java.util.HashMap;
import java.util.Map;

/**
 * 风向方位英文缩写 → 中文映射
 * 和风 v1 接口 wind.direction.compass 返回 16 方位英文缩写
 * 文档：https://dev.qweather.com/docs/api/weather/weather-current/
 */
public enum WindDirectionEnum {
    N("n", "北风"),
    NNE("nne", "东北偏北风"),
    NE("ne", "东北风"),
    ENE("ene", "东北偏东风"),
    E("e", "东风"),
    ESE("ese", "东南偏东风"),
    SE("se", "东南风"),
    SSE("sse", "东南偏南风"),
    S("s", "南风"),
    SSW("ssw", "西南偏南风"),
    SW("sw", "西南风"),
    WSW("wsw", "西南偏西风"),
    W("w", "西风"),
    WNW("wnw", "西北偏西风"),
    NW("nw", "西北风"),
    NNW("nnw", "西北偏北风"),
    NONE("none", "无持续风向"),
    VRB("vrb", "旋转风");

    private final String code;
    private final String label;

    WindDirectionEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() { return code; }
    public String getLabel() { return label; }

    private static final Map<String, WindDirectionEnum> CODE_MAP = new HashMap<>();
    static {
        for (WindDirectionEnum e : values()) {
            CODE_MAP.put(e.code, e);
        }
    }

    /**
     * 根据英文缩写返回中文风向，未识别时原样返回
     */
    public static String of(String code) {
        if (code == null) return "";
        WindDirectionEnum e = CODE_MAP.get(code.toLowerCase());
        return e == null ? code : e.label;
    }
}
