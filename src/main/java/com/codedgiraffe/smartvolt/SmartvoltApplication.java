package com.codedgiraffe.smartvolt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SmartvoltApplication {

	public static void main(String[] args) {
		SpringApplication.run(SmartvoltApplication.class, args);
	}

}
