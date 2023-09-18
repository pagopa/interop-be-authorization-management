package it.pagopa.interop.authorizationmanagement.model.persistence.impl

import it.pagopa.interop.authorizationmanagement.api.impl.jwkKeyFormat
import it.pagopa.interop.authorizationmanagement.model.JWKKey
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import spray.json.enrichAny

class KeySerializationSpec extends AnyWordSpecLike with Matchers {

  "serialization of Key" should {
    "contain x5t#s256" in {
      val key       = JWKKey(
        kty = "1",
        keyOps = None,
        use = Some("sig"),
        alg = None,
        kid = "12",
        x5u = None,
        x5t = None,
        x5tS256 = Some("hello"),
        x5c = None,
        crv = None,
        x = None,
        y = None,
        d = None,
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
      val keyString = key.toJson.compactPrint
      keyString shouldBe "{\"kid\":\"12\",\"kty\":\"1\",\"use\":\"sig\",\"x5t#S256\":\"hello\"}"
    }
  }

}
