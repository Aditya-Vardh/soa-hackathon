package edu.soa.s09.t09.discoveryservice;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;
@SpringBootApplication
@EnableEurekaServer
public class DiscoveryApplication {
  public static void main(String[] args) { SpringApplication.run(DiscoveryApplication.class, args); }
}
