package com.dndmaster.relay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AgentConnectionRelayApplication {
  public static void main(String[] args) {
    SpringApplication.run(AgentConnectionRelayApplication.class, args);
  }
}
