package com.unihire.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MockSmsServiceImpl implements SmsService {

    private static final Logger logger = LoggerFactory.getLogger(MockSmsServiceImpl.class);

    @Override
    public void sendOtp(String phone, String otp) {
        logger.info("=========================================");
        logger.info("MOCK SMS SERVICE");
        logger.info("Sending OTP to {}: {}", phone, otp);
        logger.info("=========================================");
    }
}
