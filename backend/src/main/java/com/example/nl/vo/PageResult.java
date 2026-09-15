package com.example.nl.vo;

import lombok.Data;

import java.util.List;

/**
 * 通用分页结果
 */
@Data
public class PageResult<T> {
    /** 当前页码（从1开始） */
    private Integer pageNum;
    /** 每页条数 */
    private Integer pageSize;
    /** 总记录数 */
    private Long total;
    /** 总页数 */
    private Integer totalPages;
    /** 当前页数据 */
    private List<T> list;

    public static <T> PageResult<T> of(Integer pageNum, Integer pageSize, Long total, List<T> list) {
        PageResult<T> p = new PageResult<>();
        p.pageNum = pageNum;
        p.pageSize = pageSize;
        p.total = total;
        p.totalPages = (total == 0 || pageSize == 0) ? 0 : (int) Math.ceil((double) total / pageSize);
        p.list = list;
        return p;
    }
}
