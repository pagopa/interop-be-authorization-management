package it.pagopa.interop.authorizationmanagement.model.persistence

import it.pagopa.interop.authorizationmanagement.errors.KeyManagementErrors.ThumbprintCalculationError
import it.pagopa.interop.authorizationmanagement.model.key.{Enc, PersistentKey, PersistentKeyUse, Sig}
import it.pagopa.interop.authorizationmanagement.jwk.model.Models._
import it.pagopa.interop.authorizationmanagement.model.{ClientKey, KeyUse, Key, OtherPrimeInfo}
import it.pagopa.interop.authorizationmanagement.jwk.converter.KeyConverter

object KeyAdapters {

  implicit class PersistentKeyWrapper(private val p: PersistentKey) extends AnyVal {
    def toApi: Either[Throwable, ClientKey] =
      KeyConverter
        .fromBase64encodedPEMToAPIKey(p.kid, p.encodedPem, p.use.toJwk, p.algorithm)
        .map(k => ClientKey(k.toApi, p.relationshipId, p.name, p.createdAt))
  }

  implicit class PersistentKeyObjectWrapper(private val p: PersistentKey.type) extends AnyVal {
    def toPersistentKey(validKey: ValidKey): Either[ThumbprintCalculationError, PersistentKey] =
      for {
        kid <- KeyConverter
          .calculateKid(validKey._2)
          .left
          .map(ex => ThumbprintCalculationError(ex.getLocalizedMessage()))
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

  implicit class PersistentKeyUseObjectWrapper(private val p: PersistentKeyUse.type) extends AnyVal {
    def fromApi(value: KeyUse): PersistentKeyUse = value match {
      case KeyUse.SIG => Sig
      case KeyUse.ENC => Enc
    }
  }
  implicit class PersistentKeyUseWrapper(private val p: PersistentKeyUse)            extends AnyVal {
    def toJwk: JwkKeyUse = p match {
      case Sig => JwkSig
      case Enc => JwkEnc
    }
  }

  implicit class KeyUseWrapper(private val p: JwkKey)                    extends AnyVal {
    def toApi: Key = Key(
      kty = p.kty,
      keyOps = p.keyOps,
      use = p.use,
      alg = p.alg,
      kid = p.kid,
      x5u = p.x5u,
      x5t = p.x5t,
      x5tS256 = p.x5tS256,
      x5c = p.x5c,
      crv = p.crv,
      x = p.x,
      y = p.y,
      d = p.d,
      k = p.k,
      n = p.n,
      e = p.e,
      p = p.p,
      q = p.q,
      dp = p.dp,
      dq = p.dq,
      qi = p.qi,
      oth = p.oth.map(_.map(_.toApi))
    )
  }
  implicit class OtherPrimeInfoWrapper(private val o: JwkOtherPrimeInfo) extends AnyVal {
    def toApi: OtherPrimeInfo = OtherPrimeInfo(r = o.r, d = o.d, t = o.t)
  }
}
