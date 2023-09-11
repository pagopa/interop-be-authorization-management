package it.pagopa.interop.authorizationmanagement.jwk.converter

import com.nimbusds.jose.jwk._
import com.nimbusds.jose.util.{StandardCharset, X509CertUtils}
import cats.syntax.all._
import it.pagopa.interop.authorizationmanagement.jwk.model.Models._
import it.pagopa.interop.authorizationmanagement.jwk.model.Adapters._

import java.security.cert.X509Certificate
import scala.annotation.nowarn
import scala.jdk.CollectionConverters.{ListHasAsScala, SetHasAsScala}
import java.util.Base64
import scala.util.Try

class ParsingException(message: String)              extends IllegalArgumentException(message)
class NotAllowedPrivateKeyException(message: String) extends IllegalArgumentException(message)

trait KeyConverter {
  def calculateKid(key: JWK): Either[Throwable, String]
  def fromBase64encodedPEM(base64PEM: String): Either[Throwable, JWK]
  def publicKeyOnly(key: JWK): Either[Throwable, Boolean]
  def fromBase64encodedPEMToAPIKey(
    kid: String,
    base64PEM: String,
    use: JwkKeyUse,
    algorithm: String
  ): Either[Throwable, JwkKey]
}

object KeyConverter extends KeyConverter {

  override def calculateKid(key: JWK): Either[Throwable, String] = Try {
    key.computeThumbprint().toString
  }.toEither

  override def fromBase64encodedPEM(base64PEM: String): Either[Throwable, JWK] =
    decodeBase64(base64PEM).toEither.flatMap(fromPEM)

  private def decodeBase64(encoded: String): Try[String] = Try {
    val decoded: Array[Byte] = Base64.getDecoder.decode(encoded.getBytes(StandardCharset.UTF_8))
    new String(decoded, StandardCharset.UTF_8)
  }

  private def fromPEM(pem: String): Either[Throwable, JWK] = {
    def parseAsCertificate: Either[ParsingException, Option[X509Certificate]] =
      Try(Option(X509CertUtils.parse(pem))).toEither.leftMap(err =>
        new ParsingException(s"PEM parse failed. Reason: ${err.getMessage}")
      )

    def parseAsPEM: Either[ParsingException, JWK] =
      Try(JWK.parseFromPEMEncodedObjects(pem)).toEither.leftMap(err =>
        new ParsingException(s"PEM parse failed. Reason: ${err.getMessage}")
      )

    for {
      certificate <- parseAsCertificate
      result      <- certificate.fold(parseAsPEM)(_ =>
        new ParsingException("The platform does not allow to upload certificates").asLeft[JWK]
      )
    } yield result
  }

  override def publicKeyOnly(key: JWK): Either[Throwable, Boolean] = {
    Either.cond(!key.isPrivate, true, new NotAllowedPrivateKeyException("This contains a private key!"))
  }

  override def fromBase64encodedPEMToAPIKey(
    kid: String,
    base64PEM: String,
    use: JwkKeyUse,
    algorithm: String
  ): Either[Throwable, JwkKey] = {
    val key = for {
      jwk <- fromBase64encodedPEM(base64PEM)
    } yield jwk.getKeyType match {
      case KeyType.RSA => rsa(kid, jwk.toRSAKey)
      case KeyType.EC  => ec(kid, jwk.toECKey)
      case KeyType.OKP => okp(kid, jwk.toOctetKeyPair)
      case KeyType.OCT => oct(kid, jwk.toOctetSequenceKey)
      case _           => throw new RuntimeException(s"Unknown KeyType ${jwk.getKeyType}")
    }

    key.map(_.copy(alg = Some(algorithm), use = Some(use.toRfcValue)))
  }

  private def rsa(kid: String, key: RSAKey): JwkKey           = {
    val otherPrimes = Option(key.getOtherPrimes)
      .map(list =>
        list.asScala
          .map(entry =>
            JwkOtherPrimeInfo(
              r = entry.getPrimeFactor.toString,
              d = entry.getFactorCRTExponent.toString,
              t = entry.getFactorCRTCoefficient.toString
            )
          )
          .toSeq
      )
      .filter(_.nonEmpty)

    JwkKey(
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
  private def ec(kid: String, key: ECKey): JwkKey             = JwkKey(
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
  private def okp(kid: String, key: OctetKeyPair): JwkKey     = {
    JwkKey(
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
  private def oct(kid: String, key: OctetSequenceKey): JwkKey = {
    JwkKey(
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
