package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.impl

import it.pagopa.pdnd.interop.uservice.keymanagement.model.Key
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import it.pagopa.pdnd.interop.uservice.keymanagement.api.impl.keyFormat
import spray.json.enrichAny

class KeySerializationSpec extends AnyWordSpecLike with Matchers {

  "serialization of Key" should {
    "contains x5t#s256" in {
      val key = Key(
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
