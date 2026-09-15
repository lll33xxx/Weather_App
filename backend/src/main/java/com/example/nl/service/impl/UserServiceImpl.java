package com.example.nl.service.impl;

import com.example.nl.entity.User;
import com.example.nl.mapper.UserMapper;
import com.example.nl.service.UserService;
import com.example.nl.util.PasswordUtil;
import com.example.nl.vo.PageResult;
import com.example.nl.vo.UserVO;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户认证与管理业务层实现
 */
@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    /**
     * 登录校验
     *
     * @param username 用户名
     * @param password 明文密码
     * @return 登录成功返回用户信息（不含密码），失败返回 null
     */
    @Override
    public UserVO login(String username, String password) {
        if (username == null || username.isBlank() || password == null) {
            return null;
        }
        User user = userMapper.selectByUsername(username.trim());
        if (user == null) {
            return null;
        }
        if (!PasswordUtil.matches(password, user.getPassword())) {
            return null;
        }
        return toVO(user);
    }

    /**
     * 注册普通用户（管理员不可注册）
     *
     * @param username 用户名
     * @param password 明文密码
     * @return 注册成功返回用户信息，失败抛出异常
     */
    @Override
    public UserVO register(String username, String password) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("密码长度不能少于6位");
        }
        String trimmedName = username.trim();
        // 检查用户名是否已存在
        User existing = userMapper.selectByUsername(trimmedName);
        if (existing != null) {
            throw new IllegalArgumentException("用户名已存在");
        }
        User user = new User();
        user.setUsername(trimmedName);
        user.setPassword(PasswordUtil.encrypt(password));
        user.setRole("user"); // 注册只能是普通用户，管理员不可注册
        userMapper.insert(user);
        return toVO(user);
    }

    /**
     * 根据ID查询用户
     */
    @Override
    public UserVO getById(Long id) {
        if (id == null) return null;
        User user = userMapper.selectById(id);
        return user == null ? null : toVO(user);
    }

    /**
     * 分页查询用户列表
     *
     * @param pageNum  页码（从1开始）
     * @param pageSize 每页条数
     * @param keyword  用户名搜索关键词（可为空）
     */
    @Override
    public PageResult<UserVO> getUserPage(int pageNum, int pageSize, String keyword) {
        int pn = Math.max(pageNum, 1);
        int ps = (pageSize <= 0) ? 10 : Math.min(pageSize, 100);
        // PageHelper 只对紧随其后的第一条 MyBatis 查询生效
        PageHelper.startPage(pn, ps);
        List<User> users = userMapper.selectPage(keyword);
        PageInfo<User> pageInfo = new PageInfo<>(users);
        List<UserVO> voList = users.stream().map(this::toVO).collect(Collectors.toList());
        return PageResult.of(pn, ps, pageInfo.getTotal(), voList);
    }

    /**
     * 修改用户角色
     *
     * @param id   用户ID
     * @param role 新角色（user/admin）
     */
    @Override
    public boolean updateRole(Long id, String role) {
        if (id == null || role == null) return false;
        if (!"user".equals(role) && !"admin".equals(role)) {
            throw new IllegalArgumentException("角色只能是 user 或 admin");
        }
        return userMapper.updateRole(id, role) > 0;
    }

    /**
     * 删除用户
     *
     * @param id 用户ID
     */
    @Override
    public boolean deleteUser(Long id) {
        if (id == null) return false;
        return userMapper.deleteById(id) > 0;
    }
    @Override
    public UserVO updateUsername(Long id, String newUsername) {
        if (newUsername == null || newUsername.isBlank()) throw new IllegalArgumentException("用户名不能为空");
        String trimmed = newUsername.trim();
        User existing = userMapper.selectByUsername(trimmed);
        if (existing != null && !existing.getId().equals(id)) throw new IllegalArgumentException("用户名已被使用");
        userMapper.updateUsername(id, trimmed);
        return toVO(userMapper.selectById(id));
    }

    @Override
    public void changePassword(Long id, String oldPassword, String newPassword) {
        User user = userMapper.selectById(id);
        if (user == null) throw new IllegalArgumentException("用户不存在");
        if (!PasswordUtil.matches(oldPassword, user.getPassword())) throw new IllegalArgumentException("原密码错误");
        if (newPassword == null || newPassword.length() < 6) throw new IllegalArgumentException("新密码长度不能少于6位");
        userMapper.updatePassword(id, PasswordUtil.encrypt(newPassword));
    }
    @Override
    public UserVO updateAvatar(Long id, String avatarUrl) {
        userMapper.updateAvatar(id, avatarUrl);
        return toVO(userMapper.selectById(id));
    }

    /**
     * 实体转VO（剥离密码）
     */
    private UserVO toVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRole(user.getRole());
        vo.setCreateTime(user.getCreateTime());
        vo.setAvatar(user.getAvatar());
        return vo;
    }
}
