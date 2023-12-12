package org.folio.am.config;

import org.folio.common.service.TransactionHelper;
import org.folio.security.EnableMgrSecurity;
import org.folio.spring.cql.JpaCqlConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@EnableFeignClients
@EnableMgrSecurity
@Import({JpaCqlConfiguration.class, TransactionHelper.class})
public class AppConfiguration {
}
