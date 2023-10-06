package com.security.jwttest.dto.request;

import com.security.jwttest.model.entity.Role;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RegisterRequest {
    private String name;
    private String email;
    private String password;
    private Role role;
}
