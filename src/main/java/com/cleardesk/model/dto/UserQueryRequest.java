package com.cleardesk.model.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户查询请求
 */
@Data
public class UserQueryRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String username;

    private Long current;

    private Long pageSize;
}
