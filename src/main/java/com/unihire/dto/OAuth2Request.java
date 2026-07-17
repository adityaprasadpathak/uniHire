package com.unihire.dto;

import lombok.Data;

@Data
public class OAuth2Request {
    private String provider; // e.g., GOOGLE, LINKEDIN
    private String idToken;
}
