package com.cleardesk.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 对外返回的用户信息，不含密码等敏感字段
 */
@Data
public class UserVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String userAccount;
    private String avatarUrl;
    private Integer gender;
    private String phone;
    private String email;
    private Integer userStatus;
    private Date createTime;
    private Integer userRole;
}
