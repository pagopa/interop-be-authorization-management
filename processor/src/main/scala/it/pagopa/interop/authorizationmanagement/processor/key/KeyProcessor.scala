package it.pagopa.interop.authorizationmanagement.processor.key

import com.nimbusds.jose.jwk._
import com.nimbusds.jose.util.X509CertUtils
import com.nimbusds.jose.util.StandardCharset

import java.util.Base64
import scala.util.Try

trait KeyProcessor {
  def calculateKid(key: JWK): Either[Throwable, String]
  def fromBase64encodedPEM(base64PEM: String): Either[Throwable, JWK]
  def publicKeyOnly(key: JWK): Either[Throwable, Boolean]
}

object KeyProcessor extends KeyProcessor {

  override def calculateKid(key: JWK): Either[Throwable, String] = Try {
    key.computeThumbprint().toString
  }.toEither

  override def fromBase64encodedPEM(base64PEM: String): Either[Throwable, JWK] =
    decodeBase64(base64PEM).toEither.flatMap(fromPEM)

  private def decodeBase64(encoded: String): Try[String] = Try {
    val decoded: Array[Byte] = Base64.getDecoder.decode(encoded.getBytes(StandardCharset.UTF_8))
    new String(decoded, StandardCharset.UTF_8)
  }

  private def fromPEM(pem: String): Either[Throwable, JWK] = Try {
    Option(X509CertUtils.parse(pem)) match {
      case None    => Try { JWK.parseFromPEMEncodedObjects(pem) }.toEither
      case Some(_) => Left[Throwable, JWK](new RuntimeException("The platform does not allow to upload certificates"))
    }

  }.toEither.flatten

  override def publicKeyOnly(key: JWK): Either[Throwable, Boolean] = {
    Either.cond(!key.isPrivate, true, new RuntimeException("This contains a private key!"))
  }
}
