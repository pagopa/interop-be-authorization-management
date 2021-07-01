package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.impl

import cats.data.Validated.{Invalid, Valid}
import it.pagopa.pdnd.interop.uservice.keymanagement.model.Key
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

object ValidationTest extends Validation

class ValidationSpec extends AnyWordSpecLike with Matchers {

  "given a sequence of JWK keys" should {

    "return a valid object when all the keys are valid" in {
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

      val key1 = Key(
        kty = "RSA",
        alg = "RS256",
        use = "xxxx",
        kid = "1999-04-29",
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

      val key2 = Key(
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

      val key3 = Key(
        kty = "EC",
        alg = "",
        use = "enc",
        kid = "2",
        n = None,
        e = None,
        crv = Some("P-256"),
        x = Some("MKBCTNIcKUSDii11ySs3526iDZ8AiTo7Tu6KPAqv7D4"),
        y = Some("4Etl6SRW2YiLUrN5vfvVHuhp7x8PxltmWWlbbM4IFyM")
      )

      val validation = ValidationTest.validateKeys(Seq(key, key1, key2, key3))
      validation shouldBe a[Valid[_]]
    }

    "return a invalid object when some of the keys are invalid" in {
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

      val invalidAlgorithm = "RSU"

      val key1 = Key(
        kty = invalidAlgorithm,
        alg = "RS256",
        use = "xxxx",
        kid = "1999-04-29",
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

      val key2 = Key(
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

      val key3 = Key(
        kty = "EC",
        alg = "",
        use = "enc",
        kid = "2",
        n = None,
        e = None,
        crv = Some("P-256"),
        x = None, //missing x
        y = Some("4Etl6SRW2YiLUrN5vfvVHuhp7x8PxltmWWlbbM4IFyM")
      )

      val validation = ValidationTest.validateKeys(Seq(key, key1, key2, key3))
      validation shouldBe a[Invalid[_]]
    }
  }
}
