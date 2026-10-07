package com.example.company.forwarder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ForwarderApplication {

	public static void main(String[] args) {
		SpringApplication.run(ForwarderApplication.class, args);
	}

}
