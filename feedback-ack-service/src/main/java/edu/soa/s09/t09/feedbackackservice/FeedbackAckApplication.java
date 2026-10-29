package edu.soa.s09.t09.feedbackackservice;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.util.TimeZone;

@SpringBootApplication

public class FeedbackAckApplication {
  public static void main(String[] args) { TimeZone.setDefault(TimeZone.getTimeZone("UTC")); SpringApplication.run(FeedbackAckApplication.class, args); }
}
