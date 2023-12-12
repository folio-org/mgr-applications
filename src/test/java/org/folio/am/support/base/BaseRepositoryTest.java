package org.folio.am.support.base;

import lombok.extern.log4j.Log4j2;
import org.folio.am.support.extensions.EnablePostgres;
import org.folio.spring.cql.JpaCqlConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

/**
 * Base repository test class with required annotations.
 */
@Log4j2
@EnablePostgres
@DataJpaTest
@Import(JpaCqlConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class BaseRepositoryTest { }
