package com.tikitaka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class TikitakaBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(TikitakaBackendApplication.class, args);
	}

}
