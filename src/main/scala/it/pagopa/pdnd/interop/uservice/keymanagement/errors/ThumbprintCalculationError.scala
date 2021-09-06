package it.pagopa.pdnd.interop.uservice.keymanagement.errors

final case class ThumbprintCalculationError(message: String)
    extends Throwable(s"Error while calculating keys thumbprints: $message")
