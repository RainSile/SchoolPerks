package com.campus.perks.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
    scanBasePackages = "com.campus.perks",
    exclude = org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration.class)
public class CampusApplication {
  public static void main(String[] args) {
    SpringApplication.run(CampusApplication.class, args);
  }
}
