package com.cleardesk.model.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 用户注册请求
 */
@Data
public class UserRegisterRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "账号不能为空")
    @Size(min = 4, max = 16, message = "账号长度必须为 4-16 位")
    private String userAccount;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 32, message = "密码长度必须为 8-32 位")
    private String userPassword;

    @NotBlank(message = "确认密码不能为空")
    private String checkPassword;

}
