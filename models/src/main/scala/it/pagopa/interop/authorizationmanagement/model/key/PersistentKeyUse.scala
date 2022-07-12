package it.pagopa.interop.authorizationmanagement.model.key

sealed trait PersistentKeyUse
object PersistentKeyUse
case object Sig extends PersistentKeyUse
case object Enc extends PersistentKeyUse
