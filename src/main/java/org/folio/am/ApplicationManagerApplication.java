package org.folio.am;

import java.security.Security;
import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ApplicationManagerApplication {

  /**
   * Runs spring application.
   *
   * @param args command line arguments.
   */
  public static void main(String[] args) {
    Security.addProvider(new BouncyCastleFipsProvider());
    Security.addProvider(new BouncyCastleJsseProvider("fips:BCFIPS"));
    SpringApplication.run(ApplicationManagerApplication.class, args);
  }
}
