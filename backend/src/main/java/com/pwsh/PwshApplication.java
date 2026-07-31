package com.pwsh;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // 고아파일 정리 등 스케줄 배치
public class PwshApplication {

	public static void main(String[] args) {
		SpringApplication.run(PwshApplication.class, args);
	}
}
