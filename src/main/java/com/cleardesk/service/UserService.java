package com.cleardesk.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cleardesk.common.PageResult;
import com.cleardesk.model.domain.User;
import com.cleardesk.model.dto.UserLoginRequest;
import com.cleardesk.model.dto.UserQueryRequest;
import com.cleardesk.model.dto.UserRegisterRequest;
import com.cleardesk.model.vo.UserVO;

import javax.servlet.http.HttpServletRequest;

/**
 * 用户服务
 */
public interface UserService extends IService<User> {

    /**
     * 用户注册
     *
     * @return 新用户 id
     */
    long userRegister(UserRegisterRequest userRegisterRequest);

    /**
     * 用户登录
     *
     * @return 脱敏后的用户信息
     */
    UserVO userLogin(UserLoginRequest userLoginRequest, HttpServletRequest request);

    /**
     * 用户注销
     */
    boolean userLogout(HttpServletRequest request);

    /**
     * 获取当前登录用户（脱敏）
     */
    UserVO getCurrentUser();

    /**
     * 管理员分页搜索用户
     */
    PageResult<UserVO> searchUsers(UserQueryRequest queryRequest);

    /**
     * 管理员删除用户（逻辑删除）
     */
    boolean deleteUser(long id);

    /**
     * 实体转脱敏 VO
     */
    UserVO getUserVO(User originUser);
}
