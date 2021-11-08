package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.client

import it.pagopa.pdnd.interop.uservice.keymanagement.model.{ACTIVE, ClientStatusEnum, SUSPENDED}

sealed trait ClientStatus {
  def toApi: ClientStatusEnum = this match {
    case Active    => ACTIVE
    case Suspended => SUSPENDED
  }
}

object ClientStatus {
  def fromApi(value: ClientStatusEnum): ClientStatus = value match {
    case ACTIVE    => Active
    case SUSPENDED => Suspended
  }
}

case object Active    extends ClientStatus
case object Suspended extends ClientStatus
