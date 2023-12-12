package org.folio.am.transaction.context;

import org.springframework.beans.factory.config.CustomScopeConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.SimpleTransactionScope;

@Configuration
public class TransactionScopeConfiguration {

  public static final String SCOPE_TRANSACTION = "transaction";

  @Bean
  public static CustomScopeConfigurer transactionScopeConfigurer() {
    var configurer = new CustomScopeConfigurer();
    configurer.addScope(SCOPE_TRANSACTION, new SimpleTransactionScope());
    return configurer;
  }
}
