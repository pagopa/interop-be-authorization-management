package it.pagopa.interop.authorizationmanagement.model.persistence

import com.nimbusds.jose.jwk._
import it.pagopa.interop.authorizationmanagement.errors.KeyManagementErrors.ThumbprintCalculationError
import it.pagopa.interop.authorizationmanagement.model.key.{Enc, PersistentKey, PersistentKeyUse, Sig}
import it.pagopa.interop.authorizationmanagement.model.{ClientKey, KeyUse, Key, OtherPrimeInfo}
import it.pagopa.interop.authorizationmanagement.processor.key.KeyProcessor

import scala.annotation.nowarn
import scala.jdk.CollectionConverters.{ListHasAsScala, SetHasAsScala}

object KeyAdapters {

  implicit class PersistentKeyWrapper(private val p: PersistentKey) extends AnyVal {
    def toApi: Either[Throwable, ClientKey] =
      fromBase64encodedPEMToAPIKey(p.kid, p.encodedPem, p.use, p.algorithm)
        .map(ClientKey(_, p.relationshipId, p.name, p.createdAt))
  }

  implicit class PersistentKeyObjectWrapper(private val p: PersistentKey.type) extends AnyVal {
    def toPersistentKey(validKey: ValidKey): Either[ThumbprintCalculationError, PersistentKey] =
      for {
        kid <- KeyProcessor
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

  def fromBase64encodedPEMToAPIKey(
    kid: String,
    base64PEM: String,
    use: PersistentKeyUse,
    algorithm: String
  ): Either[Throwable, Key] = {
    val key = for {
      jwk <- KeyProcessor.fromBase64encodedPEM(base64PEM)
    } yield jwk.getKeyType match {
      case KeyType.RSA => rsa(kid, jwk.toRSAKey)
      case KeyType.EC  => ec(kid, jwk.toECKey)
      case KeyType.OKP => okp(kid, jwk.toOctetKeyPair)
      case KeyType.OCT => oct(kid, jwk.toOctetSequenceKey)
      case _           => throw new RuntimeException(s"Unknown KeyType ${jwk.getKeyType}")
    }

    key.map(_.copy(alg = Some(algorithm), use = Some(use.toRfcValue)))
  }

  private def rsa(kid: String, key: RSAKey): Key           = {
    val otherPrimes = Option(key.getOtherPrimes)
      .map(list =>
        list.asScala
          .map(entry =>
            OtherPrimeInfo(
              r = entry.getPrimeFactor.toString,
              d = entry.getFactorCRTExponent.toString,
              t = entry.getFactorCRTCoefficient.toString
            )
          )
          .toSeq
      )
      .filter(_.nonEmpty)

    Key(
      use = None,
      alg = None,
      kty = key.getKeyType.getValue,
      keyOps = Option(key.getKeyOperations).map(list => list.asScala.map(op => op.toString).toSeq),
      kid = kid,
      x5u = Option(key.getX509CertURL).map(_.toString),
      x5t = getX5T(key),
      x5tS256 = Option(key.getX509CertSHA256Thumbprint).map(_.toString),
      x5c = Option(key.getX509CertChain).map(list => list.asScala.map(op => op.toString).toSeq),
      crv = None,
      x = None,
      y = None,
      d = Option(key.getPrivateExponent).map(_.toString),
      k = None,
      n = Option(key.getModulus).map(_.toString),
      e = Option(key.getPublicExponent).map(_.toString),
      p = Option(key.getFirstPrimeFactor).map(_.toString),
      q = Option(key.getSecondPrimeFactor).map(_.toString),
      dp = Option(key.getFirstFactorCRTExponent).map(_.toString),
      dq = Option(key.getSecondFactorCRTExponent).map(_.toString),
      qi = Option(key.getFirstCRTCoefficient).map(_.toString),
      oth = otherPrimes
    )
  }
  private def ec(kid: String, key: ECKey): Key             = Key(
    use = None,
    alg = None,
    kty = key.getKeyType.getValue,
    keyOps = Option(key.getKeyOperations).map(list => list.asScala.map(op => op.toString).toSeq),
    kid = kid,
    x5u = Option(key.getX509CertURL).map(_.toString),
    x5t = getX5T(key),
    x5tS256 = Option(key.getX509CertSHA256Thumbprint).map(_.toString),
    x5c = Option(key.getX509CertChain).map(list => list.asScala.map(op => op.toString).toSeq),
    crv = Option(key.getCurve).map(_.toString),
    x = Option(key.getX).map(_.toString),
    y = Option(key.getY).map(_.toString),
    d = Option(key.getD).map(_.toString),
    k = None,
    n = None,
    e = None,
    p = None,
    q = None,
    dp = None,
    dq = None,
    qi = None,
    oth = None
  )
  private def okp(kid: String, key: OctetKeyPair): Key     = {
    Key(
      use = None,
      alg = None,
      kty = key.getKeyType.getValue,
      keyOps = Option(key.getKeyOperations).map(list => list.asScala.map(op => op.toString).toSeq),
      kid = kid,
      x5u = Option(key.getX509CertURL).map(_.toString),
      x5t = getX5T(key),
      x5tS256 = Option(key.getX509CertSHA256Thumbprint).map(_.toString),
      x5c = Option(key.getX509CertChain).map(list => list.asScala.map(op => op.toString).toSeq),
      crv = Option(key.getCurve).map(_.toString),
      x = Option(key.getX).map(_.toString),
      y = None,
      d = Option(key.getD).map(_.toString),
      k = None,
      n = None,
      e = None,
      p = None,
      q = None,
      dp = None,
      dq = None,
      qi = None,
      oth = None
    )
  }
  private def oct(kid: String, key: OctetSequenceKey): Key = {
    Key(
      use = None,
      alg = None,
      kty = key.getKeyType.getValue,
      keyOps = Option(key.getKeyOperations).map(list => list.asScala.map(op => op.toString).toSeq),
      kid = kid,
      x5u = Option(key.getX509CertURL).map(_.toString),
      x5t = getX5T(key),
      x5tS256 = Option(key.getX509CertSHA256Thumbprint).map(_.toString),
      x5c = Option(key.getX509CertChain).map(list => list.asScala.map(op => op.toString).toSeq),
      crv = None,
      x = None,
      y = None,
      d = None,
      k = Option(key.getKeyValue).map(_.toString),
      n = None,
      e = None,
      p = None,
      q = None,
      dp = None,
      dq = None,
      qi = None,
      oth = None
    )

  }

  // encapsulating in a method to avoid compilation errors because of Nimbus deprecated method
  @nowarn
  @inline private def getX5T(key: JWK): Option[String] = Option(key.getX509CertThumbprint).map(_.toString)
}
