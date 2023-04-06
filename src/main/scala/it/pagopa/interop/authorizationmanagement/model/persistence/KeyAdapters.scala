package it.pagopa.interop.authorizationmanagement.model.persistence

import it.pagopa.interop.authorizationmanagement.errors.KeyManagementErrors.ThumbprintCalculationError
import it.pagopa.interop.authorizationmanagement.model.key.{Enc, PersistentKey, PersistentKeyUse, Sig}
import it.pagopa.interop.authorizationmanagement.model.{ClientKey, KeyUse}
import it.pagopa.interop.authorizationmanagement.service.impl.KeyProcessor

object KeyAdapters {

  implicit class PersistentKeyWrapper(private val p: PersistentKey) extends AnyVal {
    def toApi: Either[Throwable, ClientKey] =
      KeyProcessor
        .fromBase64encodedPEMToAPIKey(p.kid, p.encodedPem, p.use, p.algorithm)
        .map(ClientKey(_, p.relationshipId, p.name, p.createdAt))
  }

  implicit class PersistentKeyObjectWrapper(private val p: PersistentKey.type) extends AnyVal {
    def toPersistentKey(validKey: ValidKey): Either[ThumbprintCalculationError, PersistentKey] =
      for {
        kid <- KeyProcessor.calculateKid(validKey._2)
      } yield PersistentKey(
        relationshipId = validKey._1.relationshipId,
        kid = kid,
        name = validKey._1.name,
        encodedPem = validKey._1.key,
        algorithm = validKey._1.alg,
        use = PersistentKeyUse.fromApi(validKey._1.use),
        createdAt = validKey._1.createdAt
      )
  }

  implicit class PersistentKeyUseWrapper(private val p: PersistentKeyUse) extends AnyVal {
    def toRfcValue: String = p match {
      case Sig => "sig"
      case Enc => "enc"
    }
  }

  implicit class PersistentKeyUseObjectWrapper(private val p: PersistentKeyUse.type) extends AnyVal {
    def fromApi(value: KeyUse): PersistentKeyUse = value match {
      case KeyUse.SIG => Sig
      case KeyUse.ENC => Enc
    }
  }
}
