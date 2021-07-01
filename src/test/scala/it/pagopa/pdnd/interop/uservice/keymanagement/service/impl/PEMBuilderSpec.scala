package it.pagopa.pdnd.interop.uservice.keymanagement.service.impl

import it.pagopa.pdnd.interop.uservice.keymanagement.model.Key
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class PEMBuilderSpec extends AnyWordSpecLike with Matchers {

  "given a JWK key" should {

    "return a PEM object when the JWK is valid" in {
      val key = Key(
        kty = "RSA",
        alg = "RS256",
        use = "x",
        kid = "2011-04-29",
        n = Some("""0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx
                   |4cbbfAAtVT86zwu1RK7aPFFxuhDR1L6tSoc_BJECPebWKRXjBZCiFV4n3oknjhMs
                   |tn64tZ_2W-5JsGY4Hc5n9yBXArwl93lqt7_RN5w6Cf0h4QyQ5v-65YGjQR0_FDW2
                   |QvzqY368QQMicAtaSqzs8KJZgnYb9c7d0zgdAZHzu6qMQvRL5hajrn1n91CbOpbI
                   |SD08qNLyrdkt-bFTWhAI4vMQFh6WeZu0fM4lFd2NcRwr3XPksINHaQ-G_xBniIqb
                   |w0Ls1jF44-csFCur-kEgU8awapJzKnqDKgw""".stripMargin),
        e = Some("AQAB"),
        crv = None,
        x = None,
        y = None
      )
      KeyProcessor.toPEM(key).isRight shouldBe true
    }

    "return a failure when the JWK is invalid" in {

      val invalidAlgorithm = "RS"

      val key = Key(
        kty = invalidAlgorithm,
        alg = "RS256",
        use = "x",
        kid = "2011-04-29",
        n = Some("""0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx
                   |4cbbfAAtVT86zwu1RK7aPFFxuhDR1L6tSoc_BJECPebWKRXjBZCiFV4n3oknjhMs
                   |tn64tZ_2W-5JsGY4Hc5n9yBXArwl93lqt7_RN5w6Cf0h4QyQ5v-65YGjQR0_FDW2
                   |QvzqY368QQMicAtaSqzs8KJZgnYb9c7d0zgdAZHzu6qMQvRL5hajrn1n91CbOpbI
                   |SD08qNLyrdkt-bFTWhAI4vMQFh6WeZu0fM4lFd2NcRwr3XPksINHaQ-G_xBniIqb
                   |w0Ls1jF44-csFCur-kEgU8awapJzKnqDKgw""".stripMargin),
        e = Some("AQAB"),
        crv = None,
        x = None,
        y = None
      )
      KeyProcessor.toPEM(key).isLeft shouldBe true
    }

    "validate the public key of an elliptic Key" in {
      val key = Key(
        kty = "EC",
        alg = "",
        use = "enc",
        kid = "1",
        n = None,
        e = None,
        crv = Some("P-256"),
        x = Some("MKBCTNIcKUSDii11ySs3526iDZ8AiTo7Tu6KPAqv7D4"),
        y = Some("4Etl6SRW2YiLUrN5vfvVHuhp7x8PxltmWWlbbM4IFyM")
      )

      KeyProcessor.validation(key).isRight shouldBe true
    }

    "build the public key of an elliptic Key" in {
      val key = Key(
        kty = "EC",
        alg = "",
        use = "enc",
        kid = "1",
        n = None,
        e = None,
        crv = Some("P-256"),
        x = Some("MKBCTNIcKUSDii11ySs3526iDZ8AiTo7Tu6KPAqv7D4"),
        y = Some("4Etl6SRW2YiLUrN5vfvVHuhp7x8PxltmWWlbbM4IFyM")
      )

      KeyProcessor.toPEM(key).isRight shouldBe true
    }

    "not validate the public key of an elliptic Key when some parameters are missing (e.g.: x)" in {
      val key = Key(
        kty = "EC",
        alg = "",
        use = "enc",
        kid = "1",
        n = None,
        e = None,
        crv = Some("P-256"),
        x = None,
        y = Some("4Etl6SRW2YiLUrN5vfvVHuhp7x8PxltmWWlbbM4IFyM")
      )

      KeyProcessor.toPEM(key).isLeft shouldBe true
    }

  }

}
