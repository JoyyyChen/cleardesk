package com.cleardesk.controller;

import com.cleardesk.annotation.AuthCheck;
import com.cleardesk.common.BaseResponse;
import com.cleardesk.common.PageResult;
import com.cleardesk.common.ResultUtils;
import com.cleardesk.model.dto.UserDeleteRequest;
import com.cleardesk.model.dto.UserLoginRequest;
import com.cleardesk.model.dto.UserQueryRequest;
import com.cleardesk.model.dto.UserRegisterRequest;
import com.cleardesk.model.vo.UserVO;
import com.cleardesk.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * 用户接口
 */
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@Valid @RequestBody UserRegisterRequest userRegisterRequest) {
        long result = userService.userRegister(userRegisterRequest);
        return ResultUtils.success(result);
    }

    /**
     * 登录成功后 Session 只写入用户 id。
     */
    @PostMapping("/login")
    public BaseResponse<UserVO> userLogin(@Valid @RequestBody UserLoginRequest userLoginRequest,
                                          HttpServletRequest request) {
        UserVO userVO = userService.userLogin(userLoginRequest, request);
        return ResultUtils.success(userVO);
    }

    /**
     * 使当前 Session 失效。
     */
    @PostMapping("/logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
        boolean result = userService.userLogout(request);
        return ResultUtils.success(result);
    }

    /**
     * 需登录。返回当前用户脱敏信息。
     */
    @AuthCheck
    @GetMapping("/current")
    public BaseResponse<UserVO> getCurrentUser() {
        UserVO userVO = userService.getCurrentUser();
        return ResultUtils.success(userVO);
    }

    /**
     * 需管理员。角色以数据库为准。
     */
    @AuthCheck(mustAdmin = true)
    @GetMapping("/search")
    public BaseResponse<PageResult<UserVO>> searchUsers(UserQueryRequest queryRequest) {
        PageResult<UserVO> result = userService.searchUsers(queryRequest);
        return ResultUtils.success(result);
    }

    /**
     * 需管理员。逻辑删除。
     */
    @AuthCheck(mustAdmin = true)
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteUser(@Valid @RequestBody UserDeleteRequest deleteRequest) {
        boolean result = userService.deleteUser(deleteRequest.getId());
        return ResultUtils.success(result);
    }
}
