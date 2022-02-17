package it.pagopa.interop.authorizationmanagement.service

import com.nimbusds.jose.util.StandardCharset

import java.util.Base64
import scala.util.Try

package object impl {
  def decodeBase64(encoded: String): Try[String] = Try {
    val decoded: Array[Byte] = Base64.getDecoder.decode(encoded.getBytes(StandardCharset.UTF_8))
    new String(decoded, StandardCharset.UTF_8)
  }
}
