package com.cleardesk.model.dto;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 删除用户请求
 */
@Data
public class UserDeleteRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "用户 id 不能为空")
    @Min(value = 1, message = "用户 id 不合法")
    private Long id;
}
