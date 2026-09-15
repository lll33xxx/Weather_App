package com.example.nl.mapper;

import com.example.nl.entity.User;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 系统用户 Mapper
 */
@Mapper
public interface UserMapper {

    /**
     * 根据用户名查询用户
     */
    @Select("SELECT id, username, password, role, create_time AS createTime, update_time AS updateTime, avatar " +
            "FROM sys_user WHERE username = #{username}")
    User selectByUsername(@Param("username") String username);

    /**
     * 根据ID查询用户
     */
    @Select("SELECT id, username, password, role, create_time AS createTime, update_time AS updateTime, avatar " +
            "FROM sys_user WHERE id = #{id}")
    User selectById(@Param("id") Long id);

    /**
     * 新增用户（useGeneratedKeys 回填主键）
     */
    @Insert("INSERT INTO sys_user (username, password, role) VALUES (#{username}, #{password}, #{role})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    /**
     * 分页查询用户（支持用户名模糊搜索）
     * 分页由 PageHelper 插件自动完成，此处无需传 offset/pageSize
     */
    @Select("<script>" +
            "SELECT id, username, password, role, create_time AS createTime, update_time AS updateTime, avatar " +
            "FROM sys_user " +
            "<where>" +
            "  <if test='keyword != null and keyword != \"\"'>" +
            "    AND username LIKE CONCAT('%', #{keyword}, '%')" +
            "  </if>" +
            "</where>" +
            "ORDER BY id ASC" +
            "</script>")
    List<User> selectPage(@Param("keyword") String keyword);

    /**
     * 更新用户角色
     */
    @Update("UPDATE sys_user SET role = #{role}, update_time = NOW() WHERE id = #{id}")
    int updateRole(@Param("id") Long id, @Param("role") String role);

    /**
     * 根据ID删除用户
     */
    @Delete("DELETE FROM sys_user WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    @Update("UPDATE sys_user SET username = #{username}, update_time = NOW() WHERE id = #{id}")
    int updateUsername(@Param("id") Long id, @Param("username") String username);

    @Update("UPDATE sys_user SET password = #{password}, update_time = NOW() WHERE id = #{id}")
    int updatePassword(@Param("id") Long id, @Param("password") String password);

    @Update("UPDATE sys_user SET avatar = #{avatar}, update_time = NOW() WHERE id = #{id}")
    int updateAvatar(@Param("id") Long id, @Param("avatar") String avatar);
}
