package com.example.nl.service;

import com.example.nl.vo.PageResult;
import com.example.nl.vo.UserVO;

/**
 * 用户认证与管理业务层接口
 */
public interface UserService {

    /**
     * 登录校验
     *
     * @param username 用户名
     * @param password 明文密码
     * @return 登录成功返回用户信息（不含密码），失败返回 null
     */
    UserVO login(String username, String password);

    /**
     * 注册普通用户（管理员不可注册）
     *
     * @param username 用户名
     * @param password 明文密码
     * @return 注册成功返回用户信息，失败抛出异常
     */
    UserVO register(String username, String password);

    /**
     * 根据ID查询用户
     */
    UserVO getById(Long id);

    /**
     * 分页查询用户列表
     *
     * @param pageNum  页码（从1开始）
     * @param pageSize 每页条数
     * @param keyword  用户名搜索关键词（可为空）
     */
    PageResult<UserVO> getUserPage(int pageNum, int pageSize, String keyword);

    /**
     * 修改用户角色
     *
     * @param id   用户ID
     * @param role 新角色（user/admin）
     */
    boolean updateRole(Long id, String role);

    /**
     * 删除用户
     *
     * @param id 用户ID
     */
    boolean deleteUser(Long id);

    UserVO updateUsername(Long id, String newUsername);

    void changePassword(Long id, String oldPassword, String newPassword);

    UserVO updateAvatar(Long id, String avatarUrl);
}
