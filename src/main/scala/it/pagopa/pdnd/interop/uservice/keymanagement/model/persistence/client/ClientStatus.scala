package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.client

sealed trait ClientStatus {
  def stringify: String = this match {
    case Active    => "active"
    case Suspended => "suspended"
  }
}

object ClientStatus {
  def fromText(str: String): Either[Throwable, ClientStatus] = str match {
    case "active"    => Right[Throwable, ClientStatus](Active)
    case "suspended" => Right[Throwable, ClientStatus](Suspended)
    case _           => Left[Throwable, ClientStatus](new RuntimeException("Deserialization from protobuf failed"))
  }
}

case object Active    extends ClientStatus
case object Suspended extends ClientStatus
