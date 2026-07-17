package com.unihire.dto;

import lombok.Data;

@Data
public class TokenRefreshRequest {
    private String refreshToken;
}
