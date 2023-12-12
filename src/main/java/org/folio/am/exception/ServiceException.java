package org.folio.am.exception;

public class ServiceException extends RuntimeException {

  /**
   * Creates {@link ServiceException}  with error message and error cause.
   *
   * @param message - error message as {@link String} object
   * @param cause - error cause as {@link Throwable} object
   */
  public ServiceException(String message,  Throwable cause) {
    super(message, cause);
  }
}
