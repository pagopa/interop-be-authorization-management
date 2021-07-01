package it.pagopa.pdnd.interop.uservice.keymanagement.service.impl

import com.nimbusds.jose.jwk.{JWK, KeyType}
import it.pagopa.pdnd.interop.uservice.keymanagement.api.impl.keyFormat
import it.pagopa.pdnd.interop.uservice.keymanagement.model.Key
import org.bouncycastle.util.io.pem.{PemObject, PemWriter}
import spray.json._

import java.io.StringWriter
import java.security.PublicKey
import scala.util.{Failure, Try}

trait KeyProcessor {
  def toPEM(key: Key): Either[Throwable, String]
  def validation(key: Key): Either[Throwable, Key]
}

object KeyProcessor {

  def validation(key: Key): Either[Throwable, Key] = {
    val serializedKey: String = key.toJson.compactPrint
    val validKey = for {
      serialized <- Try(JWK.parse(serializedKey))
      _          <- publicKeyByAdmittableType(serialized)
    } yield key
    validKey.toEither
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
