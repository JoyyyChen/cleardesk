package com.cleardesk.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cleardesk.common.ErrorCode;
import com.cleardesk.common.PageResult;
import com.cleardesk.constant.UserConstant;
import com.cleardesk.context.UserHolder;
import com.cleardesk.exception.BusinessException;
import com.cleardesk.mapper.UserMapper;
import com.cleardesk.model.domain.User;
import com.cleardesk.model.dto.UserLoginRequest;
import com.cleardesk.model.dto.UserQueryRequest;
import com.cleardesk.model.dto.UserRegisterRequest;
import com.cleardesk.model.vo.UserVO;
import com.cleardesk.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.cleardesk.constant.UserConstant.DEFAULT_PAGE_NUM;
import static com.cleardesk.constant.UserConstant.DEFAULT_PAGE_SIZE;
import static com.cleardesk.constant.UserConstant.MAX_PAGE_SIZE;
import static com.cleardesk.constant.UserConstant.USER_LOGIN_STATE;
import static com.cleardesk.constant.UserConstant.USER_STATUS_NORMAL;

/**
 * 用户服务实现
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private static final Pattern ACCOUNT_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{4,16}$");

    @Resource
    private PasswordEncoder passwordEncoder;

    @Override
    public long userRegister(UserRegisterRequest userRegisterRequest) {
        if (userRegisterRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();
        if (StringUtils.isAnyBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (!ACCOUNT_PATTERN.matcher(userAccount).matches()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号仅支持 4-16 位字母、数字或下划线");
        }
        if (userPassword.length() < 8 || userPassword.length() > 32) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码长度必须为 8-32 位");
        }
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }
        long accountCount = this.count(new QueryWrapper<User>().eq("userAccount", userAccount));
        if (accountCount > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号重复");
        }
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(passwordEncoder.encode(userPassword));
        user.setUserRole(UserConstant.DEFAULT_ROLE);
        user.setUserStatus(USER_STATUS_NORMAL);
        try {
            boolean saved = this.save(user);
            if (!saved || user.getId() == null) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败");
            }
        } catch (DuplicateKeyException e) {
            throw duplicateRegisterException(e);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号已存在");
        }
        return user.getId();
    }

    @Override
    public UserVO userLogin(UserLoginRequest userLoginRequest, HttpServletRequest request) {
        if (userLoginRequest == null || request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();
        if (StringUtils.isAnyBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (!ACCOUNT_PATTERN.matcher(userAccount).matches() || userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号或密码错误");
        }
        User user = this.getOne(new QueryWrapper<User>().eq("userAccount", userAccount));
        if (user == null || !passwordEncoder.matches(userPassword, user.getUserPassword())) {
            log.info("user login failed, userAccount={}", userAccount);
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号或密码错误");
        }
        if (user.getUserStatus() == null || user.getUserStatus() != USER_STATUS_NORMAL) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被禁用");
        }
        request.getSession(true).setAttribute(USER_LOGIN_STATE, user.getId());
        return getUserVO(user);
    }

    @Override
    public boolean userLogout(HttpServletRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return true;
    }

    @Override
    public UserVO getCurrentUser() {
        User currentUser = UserHolder.get();
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
        User user = this.getById(currentUser.getId());
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN, "登录态无效");
        }
        return getUserVO(user);
    }

    @Override
    public PageResult<UserVO> searchUsers(UserQueryRequest queryRequest) {
        long current = queryRequest == null || queryRequest.getCurrent() == null
                ? DEFAULT_PAGE_NUM : queryRequest.getCurrent();
        long pageSize = queryRequest == null || queryRequest.getPageSize() == null
                ? DEFAULT_PAGE_SIZE : queryRequest.getPageSize();
        if (current < 1) {
            current = DEFAULT_PAGE_NUM;
        }
        if (pageSize < 1) {
            pageSize = DEFAULT_PAGE_SIZE;
        }
        if (pageSize > MAX_PAGE_SIZE) {
            pageSize = MAX_PAGE_SIZE;
        }
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        String username = queryRequest == null ? null : queryRequest.getUsername();
        if (StringUtils.isNotBlank(username)) {
            queryWrapper.like("username", username);
        }
        queryWrapper.orderByDesc("id");
        Page<User> page = this.page(new Page<>(current, pageSize), queryWrapper);
        List<UserVO> records = page.getRecords().stream().map(this::getUserVO).collect(Collectors.toList());
        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public boolean deleteUser(long id) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户 id 不合法");
        }
        User loginUser = UserHolder.get();
        if (loginUser != null && loginUser.getId() != null && loginUser.getId().equals(id)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不能删除当前登录账号");
        }
        boolean removed = this.removeById(id);
        if (!removed) {
            throw new BusinessException(ErrorCode.NULL_ERROR, "用户不存在");
        }
        return true;
    }

    @Override
    public UserVO getUserVO(User originUser) {
        if (originUser == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(originUser, userVO);
        return userVO;
    }

    private BusinessException duplicateRegisterException(DuplicateKeyException e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        if (message.contains("uk_userAccount")) {
            return new BusinessException(ErrorCode.PARAMS_ERROR, "账号重复");
        }
        return new BusinessException(ErrorCode.PARAMS_ERROR, "账号已存在");
    }
}
