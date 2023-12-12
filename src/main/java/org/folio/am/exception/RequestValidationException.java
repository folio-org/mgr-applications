package org.folio.am.exception;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.folio.common.domain.model.error.ErrorCode.VALIDATION_ERROR;

import java.io.Serial;
import java.util.List;
import lombok.Getter;
import org.folio.common.domain.model.error.ErrorCode;
import org.folio.common.domain.model.error.Parameter;

@Getter
public class RequestValidationException extends RuntimeException {

  @Serial private static final long serialVersionUID = -7707872159451492901L;

  private final List<Parameter> errorParameters;
  private final ErrorCode errorCode;

  /**
   * Creates {@link RequestValidationException} object for given message, key and value.
   *
   * @param message - validation error message
   * @param key - validation key as field or parameter name
   * @param value - invalid parameter value
   */
  public RequestValidationException(String message, String key, String value) {
    super(message);

    this.errorCode = VALIDATION_ERROR;
    this.errorParameters = singletonList(new Parameter().key(key).value(value));
  }

  /**
   * Creates {@link RequestValidationException} object for given message.
   *
   * @param message - validation error message
   */
  public RequestValidationException(String message) {
    super(message);

    this.errorCode = VALIDATION_ERROR;
    this.errorParameters = emptyList();
  }

  public RequestValidationException(String message, List<Parameter> errorParameters) {
    super(message);

    this.errorCode = VALIDATION_ERROR;
    this.errorParameters = errorParameters;
  }
}
