package com.unihire.services.auth;

public interface SmsService {
    void sendOtp(String phone, String otp);
}
