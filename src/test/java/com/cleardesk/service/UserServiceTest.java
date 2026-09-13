package com.cleardesk.service;

import com.cleardesk.common.ErrorCode;
import com.cleardesk.exception.BusinessException;
import com.cleardesk.model.dto.UserRegisterRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.UUID;

/**
 * 用户注册校验测试。需要本地 MySQL（库名默认 cleardesk）。
 */
@SpringBootTest
@Transactional
class UserServiceTest {

    @Resource
    private UserService userService;

    @Test
    void userRegisterShouldRejectBlankPassword() {
        UserRegisterRequest request = buildRequest("tester01", "", "12345678");
        BusinessException exception = Assertions.assertThrows(BusinessException.class,
                () -> userService.userRegister(request));
        Assertions.assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
    }

    @Test
    void userRegisterShouldRejectShortAccount() {
        UserRegisterRequest request = buildRequest("ab", "12345678", "12345678");
        Assertions.assertThrows(BusinessException.class, () -> userService.userRegister(request));
    }

    @Test
    void userRegisterShouldRejectSpecialAccount() {
        UserRegisterRequest request = buildRequest("yu pi", "12345678", "12345678");
        Assertions.assertThrows(BusinessException.class, () -> userService.userRegister(request));
    }

    @Test
    void userRegisterShouldRejectPasswordMismatch() {
        UserRegisterRequest request = buildRequest("tester01", "12345678", "12345679");
        BusinessException exception = Assertions.assertThrows(BusinessException.class,
                () -> userService.userRegister(request));
        Assertions.assertEquals("两次输入的密码不一致", exception.getDescription());
    }

    @Test
    void userRegisterShouldSucceedAndRejectDuplicateAccount() {
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String account = "u" + token;
        UserRegisterRequest request = buildRequest(account, "12345678", "12345678");
        long userId = userService.userRegister(request);
        Assertions.assertTrue(userId > 0);

        BusinessException duplicateAccount = Assertions.assertThrows(BusinessException.class,
                () -> userService.userRegister(buildRequest(account, "12345678", "12345678")));
        Assertions.assertEquals("账号重复", duplicateAccount.getDescription());

    }

    private UserRegisterRequest buildRequest(String account, String password, String checkPassword) {
        UserRegisterRequest request = new UserRegisterRequest();
        request.setUserAccount(account);
        request.setUserPassword(password);
        request.setCheckPassword(checkPassword);
        return request;
    }
}
