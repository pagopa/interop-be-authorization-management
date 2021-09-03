package it.pagopa.pdnd.interop.uservice.keymanagement.service

import java.util.UUID

trait UUIDSupplier {
  def get: UUID
}
