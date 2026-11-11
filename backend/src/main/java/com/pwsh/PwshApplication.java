package com.pwsh;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // 배치 스케줄러(TaskScheduler) — 작업 등록은 BatchService가 batch_job의 주기로 한다(SSE 핑만 @Scheduled)
public class PwshApplication {

	public static void main(String[] args) {
		SpringApplication.run(PwshApplication.class, args);
	}
}
