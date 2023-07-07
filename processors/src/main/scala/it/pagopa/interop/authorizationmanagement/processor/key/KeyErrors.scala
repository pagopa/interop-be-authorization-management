package it.pagopa.interop.authorizationmanagement.processor.key

import it.pagopa.interop.commons.utils.errors.ComponentError

object KeyErrors {
  final case class ThumbprintCalculationError(message: String)
      extends ComponentError("0005", s"Error while calculating keys thumbprints: $message")
}
