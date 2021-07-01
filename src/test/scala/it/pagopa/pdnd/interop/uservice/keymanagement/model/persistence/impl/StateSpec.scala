package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.impl

import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.State
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.key.{Active, PersistentKey}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.time.OffsetDateTime

class StateSpec extends AnyWordSpecLike with Matchers {

  "given an application state" should {

    "return a list of already existing kids when other kids are provided" in {

      val fooBarKeys = Map(
        "1" -> PersistentKey(
          kty = "1",
          alg = "1",
          use = "1",
          kid = "1",
          n = None,
          e = None,
          crv = None,
          x = None,
          y = None,
          creationTimestamp = OffsetDateTime.now(),
          deactivationTimestamp = None,
          status = Active
        ),
        "2" -> PersistentKey(
          kty = "1",
          alg = "1",
          use = "1",
          kid = "2",
          n = None,
          e = None,
          crv = None,
          x = None,
          y = None,
          creationTimestamp = OffsetDateTime.now(),
          deactivationTimestamp = None,
          status = Active
        ),
        "3" -> PersistentKey(
          kty = "1",
          alg = "1",
          use = "1",
          kid = "3",
          n = None,
          e = None,
          crv = None,
          x = None,
          y = None,
          creationTimestamp = OffsetDateTime.now(),
          deactivationTimestamp = None,
          status = Active
        ),
        "4" -> PersistentKey(
          kty = "1",
          alg = "1",
          use = "1",
          kid = "4",
          n = None,
          e = None,
          crv = None,
          x = None,
          y = None,
          creationTimestamp = OffsetDateTime.now(),
          deactivationTimestamp = None,
          status = Active
        )
      )
      val keys = Map("fooBarKeys" -> fooBarKeys)

      val state = State(keys = keys)

      val result = state.containsKeys("fooBarKeys", List("1", "4", "3", "40"))
      result shouldBe a[Some[_]]
      result.get should contain allOf ("1", "3", "4")
      result shouldNot contain("40")

      state.containsKeys("fooBarKeys", List("1000", "4000", "30000", "40")) shouldBe (None)
      state.containsKeys("anotherClient", List("1", "4", "3", "40")) shouldBe (None)
    }

  }

}
