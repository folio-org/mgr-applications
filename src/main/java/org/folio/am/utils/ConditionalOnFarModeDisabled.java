package org.folio.am.utils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;

/**
 * {@link Conditional @Conditional} that checks if the Folio Application Registry mode is not activated.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@ConditionalOnProperty(name = "application.far-mode.enabled", havingValue = "false", matchIfMissing = true)
public @interface ConditionalOnFarModeDisabled {}
