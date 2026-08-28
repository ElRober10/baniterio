package com.baniterio.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BaniterioApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(BaniterioApiApplication.class, args);
	}

}
