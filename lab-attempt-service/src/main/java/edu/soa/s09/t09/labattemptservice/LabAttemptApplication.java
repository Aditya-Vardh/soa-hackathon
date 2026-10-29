package edu.soa.s09.t09.labattemptservice;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.util.TimeZone;

@SpringBootApplication

public class LabAttemptApplication {
  public static void main(String[] args) { TimeZone.setDefault(TimeZone.getTimeZone("UTC")); SpringApplication.run(LabAttemptApplication.class, args); }
}
