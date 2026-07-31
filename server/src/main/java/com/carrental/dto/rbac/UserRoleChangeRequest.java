package com.carrental.dto.rbac;

import lombok.Getter;
import lombok.Setter;

/** Exactly one of the two should be set: assigning a system role clears customRoleId and vice versa. */
@Getter
@Setter
public class UserRoleChangeRequest {
    private String roleCode;
    private Long customRoleId;
}
