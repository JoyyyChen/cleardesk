package com.cleardesk.model.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 用户实体
 */
@TableName(value = "user")
@Data
public class User implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String userAccount;

    private String avatarUrl;

    private Integer gender;

    private String userPassword;

    private String phone;

    private String email;

    /**
     * 状态 0-正常 1-禁用
     */
    private Integer userStatus;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer isDelete;

    /**
     * 角色 0-普通用户 1-管理员
     */
    private Integer userRole;


    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
