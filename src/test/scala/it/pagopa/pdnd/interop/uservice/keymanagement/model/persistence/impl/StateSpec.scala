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
          kid = "1",
          encodedPem = "123",
          creationTimestamp = OffsetDateTime.now(),
          deactivationTimestamp = None,
          status = Active
        ),
        "2" -> PersistentKey(
          kid = "2",
          encodedPem = "123",
          creationTimestamp = OffsetDateTime.now(),
          deactivationTimestamp = None,
          status = Active
        ),
        "3" -> PersistentKey(
          kid = "3",
          encodedPem = "123",
          creationTimestamp = OffsetDateTime.now(),
          deactivationTimestamp = None,
          status = Active
        ),
        "4" -> PersistentKey(
          kid = "4",
          encodedPem = "123",
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
