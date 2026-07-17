package com.unihire;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class UniHireApplication {

	public static void main(String[] args) {
		SpringApplication.run(UniHireApplication.class, args);
	}

}
