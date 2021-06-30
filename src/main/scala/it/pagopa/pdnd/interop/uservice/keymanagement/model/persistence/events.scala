package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence

sealed trait Event extends Persistable

final case class KeysAdded(clientId: String, keys: Keys)        extends Event

