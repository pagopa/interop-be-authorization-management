package it.pagopa.pdnd.interop.uservice.keymanagement.service.impl

import com.nimbusds.jose.jwk._
import it.pagopa.pdnd.interop.uservice.keymanagement.api.impl.keyFormat
import it.pagopa.pdnd.interop.uservice.keymanagement.model.{Key, OtherPrimeInfo}
import it.pagopa.pdnd.interop.uservice.keymanagement.service.utils.decodeBase64
import org.bouncycastle.util.io.pem.{PemObject, PemWriter}
import spray.json._

import java.io.StringWriter
import java.security.PublicKey
import scala.annotation.nowarn
import scala.jdk.CollectionConverters.{ListHasAsScala, SetHasAsScala}
import scala.util.{Failure, Try}

trait KeyProcessor {
  def fromBase64encodedPEM(base64PEM: String): Either[Throwable, JWK]
  def fromPEM(pem: String): Either[Throwable, JWK]
  def toPEM(key: Key): Either[Throwable, String]
  def validation(key: Key): Either[Throwable, Key]
  def validatePEM(base64PEM: String): Either[Throwable, String]
}

object KeyProcessor {

  lazy val SIGATURE_USAGE = "sig" //TODO so far it's uncoded for the POC

  def calculateKid(key: JWK): Either[Throwable, String] = Try {
    key.computeThumbprint().toString
  }.toEither

  def fromBase64encodedPEM(base64PEM: String): Either[Throwable, JWK] = {
    for {
      decodedPem <- decodeBase64(base64PEM).toEither
      key        <- fromPEM(decodedPem)
    } yield key
  }

  def fromPEM(pem: String): Either[Throwable, JWK] = Try {
    JWK.parseFromPEMEncodedObjects(pem)
  }.toEither

  def validation(key: Key): Either[Throwable, Key] = {
    val serializedKey: String = key.toJson.compactPrint
    val validKey = for {
      serialized <- Try(JWK.parse(serializedKey))
      _          <- publicKeyByAdmittableType(serialized)
    } yield key
    validKey.toEither
  }

  def usableJWK(key: JWK): Either[Throwable, Boolean] = {
    key.getKeyUse match {
      case KeyUse.SIGNATURE  => Right[Throwable, Boolean](true)
      case KeyUse.ENCRYPTION => Right[Throwable, Boolean](true)
      case _                 => Left[Throwable, Boolean](new RuntimeException("Key use not valid for this key"))
    }
  }

  def publicKeyOnly(key: JWK): Either[Throwable, Boolean] = {
    Either.cond(!key.isPrivate, true, new RuntimeException("This contains a private key!"))
  }

  def toPEM(key: Key): Either[Throwable, String] = {
    val serializedKey: String = key.toJson.compactPrint
    val pem = for {
      key       <- Try(JWK.parse(serializedKey))
      publicKey <- publicKeyByAdmittableType(key)
      pem       <- publicKeyToPem(publicKey)
    } yield pem

    pem.toEither
  }

  def fromBase64encodedPEMToAPIKey(kid: String, base64PEM: String): Either[Throwable, Key] = {
    for {
      jwk <- fromBase64encodedPEM(base64PEM)
    } yield jwk.getKeyType match {
      case KeyType.RSA => rsa(kid, jwk.toRSAKey)
      case KeyType.EC  => ec(kid, jwk.toECKey)
      case KeyType.OKP => okp(kid, jwk.toOctetKeyPair)
      case KeyType.OCT => oct(kid, jwk.toOctetSequenceKey)
    }
  }

  private def rsa(kid: String, key: RSAKey): Key = {

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
      kty = key.getKeyType.getValue,
      keyOps = Option(key.getKeyOperations).map(list => list.asScala.map(op => op.toString).toSeq),
      use = Option(SIGATURE_USAGE),
      alg = Option(key.getAlgorithm).map(_.toString),
      kid = kid,
      x5u = Option(key.getX509CertURL).map(_.toString),
      x5t = getX5T(key), //Option(key.getX509CertThumbprint).map(_.toString),
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

  @nowarn
  private def getX5T(key: JWK): Option[String] = Option(key.getX509CertThumbprint).map(_.toString)

  private def ec(kid: String, key: ECKey): Key = Key(
    kty = key.getKeyType.getValue,
    keyOps = Option(key.getKeyOperations).map(list => list.asScala.map(op => op.toString).toSeq),
    use = Option(SIGATURE_USAGE),
    alg = Option(key.getAlgorithm).map(_.toString),
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

  private def okp(kid: String, key: OctetKeyPair): Key = {

    Key(
      kty = key.getKeyType.getValue,
      keyOps = Option(key.getKeyOperations).map(list => list.asScala.map(op => op.toString).toSeq),
      use = Option(SIGATURE_USAGE),
      alg = Option(key.getAlgorithm).map(_.toString),
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
      kty = key.getKeyType.getValue,
      keyOps = Option(key.getKeyOperations).map(list => list.asScala.map(op => op.toString).toSeq),
      use = Option(SIGATURE_USAGE),
      alg = Option(key.getAlgorithm).map(_.toString),
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

  private def publicKeyByAdmittableType(key: JWK): Try[PublicKey] = {
    key.getKeyType match {
      case KeyType.RSA => Try(key.toRSAKey.toPublicKey)
      case KeyType.EC  => Try(key.toECKey.toPublicKey)
      case KeyType.OKP => Try(key.toOctetKeyPair.toPublicKey)
      case _           => Failure[PublicKey](new RuntimeException("No valid kty provided"))
    }
  }

  private def publicKeyToPem(publicKey: PublicKey): Try[String] = Try {
    val output    = new StringWriter
    val pemWriter = new PemWriter(output)
    val pem       = new PemObject("PUBLIC KEY", publicKey.getEncoded)
    pemWriter.writeObject(pem)
    pemWriter.close()
    output.toString
  }

}
