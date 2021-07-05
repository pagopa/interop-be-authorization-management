package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.impl

import cats.data.Validated.{Invalid, Valid}
import com.nimbusds.jose.util.StandardCharset
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.Base64

object ValidationTest extends Validation

object Base64Encoder {
  def encode(pem: String): String =
    new String(Base64.getEncoder.encode(pem.getBytes(StandardCharset.UTF_8)), StandardCharset.UTF_8)
}

class ValidationSpec extends AnyWordSpecLike with Matchers {

  "given a sequence of JWK keys" should {

    "return a valid object when all the keys are valid" in {
      val key = Base64Encoder.encode("""-----BEGIN PUBLIC KEY-----
                                       |MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtXlEMP0Ra/cGRONtRibZ
                                       |ikQarU/cjjiJCNcc0SuuKXRYvLdCJErpKuQcReXKW0HLcBwtbndWp8VSnQnHTcAi
                                       |ok/K+K8KnQ7+zDTyRi6Wcra+gmB/JjxXxoYn9DlZAskc8kCbA/tcgsYl/pft2uc0
                                       |RsQtFLmf7qeHc41kgi8sJN7Al2nbeCoq3XktbpgBEOrvqFkm2CeloO+7O7IvOwsy
                                       |cJabgZvgMZJn3yaLxlpTiMjqmB79BrpdCLHvEh8jgiyv2vbgpXMPNV5axZZckNzQ
                                       |xMQhpG8ycd3hbkWK5ofdus04BtOG7z0fl3gTZxs7NX2CT63ODdrvJHZpaIjnK55T
                                       |lQIDAQAB
                                       |-----END PUBLIC KEY-----""".stripMargin)

      val validation = ValidationTest.validateKeys(Seq(key))
      validation shouldBe a[Valid[_]]
    }

    "return a invalid object when some of the keys are invalid" in {
      val key = Base64Encoder.encode("""-----BEGIN RSA PRIVATE KEY-----
                                       |MIICXAIBAAKBgQCqGKukO1De7zhZj6+H0qtjTkVxwTCpvKe4eCZ0FPqri0cb2JZfXJ/DgYSF6vUp
                                       |wmJG8wVQZKjeGcjDOL5UlsuusFncCzWBQ7RKNUSesmQRMSGkVb1/3j+skZ6UtW+5u09lHNsj6tQ5
                                       |1s1SPrCBkedbNf0Tp0GbMJDyR4e9T04ZZwIDAQABAoGAFijko56+qGyN8M0RVyaRAXz++xTqHBLh
                                       |3tx4VgMtrQ+WEgCjhoTwo23KMBAuJGSYnRmoBZM3lMfTKevIkAidPExvYCdm5dYq3XToLkkLv5L2
                                       |pIIVOFMDG+KESnAFV7l2c+cnzRMW0+b6f8mR1CJzZuxVLL6Q02fvLi55/mbSYxECQQDeAw6fiIQX
                                       |GukBI4eMZZt4nscy2o12KyYner3VpoeE+Np2q+Z3pvAMd/aNzQ/W9WaI+NRfcxUJrmfPwIGm63il
                                       |AkEAxCL5HQb2bQr4ByorcMWm/hEP2MZzROV73yF41hPsRC9m66KrheO9HPTJuo3/9s5p+sqGxOlF
                                       |L0NDt4SkosjgGwJAFklyR1uZ/wPJjj611cdBcztlPdqoxssQGnh85BzCj/u3WqBpE2vjvyyvyI5k
                                       |X6zk7S0ljKtt2jny2+00VsBerQJBAJGC1Mg5Oydo5NwD6BiROrPxGo2bpTbu/fhrT8ebHkTz2epl
                                       |U9VQQSQzY1oZMVX8i1m5WUTLPz2yLJIBQVdXqhMCQBGoiuSoSjafUhV7i1cEGpb88h5NBYZzWXGZ
                                       |37sJ5QsW+sJyoNde3xH8vdXhzU7eT82D6X/scw9RZz+/6rCJ4p0=
                                       |-----END RSA PRIVATE KEY-----""".stripMargin)

      val validation = ValidationTest.validateKeys(Seq(key))
      validation shouldBe a[Invalid[_]]
    }
  }
}
