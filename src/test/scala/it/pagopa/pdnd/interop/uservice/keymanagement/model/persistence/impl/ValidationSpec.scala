package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.impl

import cats.data.Validated.{Invalid, Valid}
import com.nimbusds.jose.util.StandardCharset
import it.pagopa.pdnd.interop.uservice.keymanagement.model.KeySeed
import it.pagopa.pdnd.interop.uservice.keymanagement.service.impl.KeyProcessor
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.Base64

object ValidationTest extends Validation

object Base64Encoder {
  def encode(pem: String): String =
    new String(Base64.getEncoder.encode(pem.getBytes(StandardCharset.UTF_8)), StandardCharset.UTF_8)
}

class ValidationSpec extends AnyWordSpecLike with Matchers with EitherValues {

  "given a sequence of JWK keys" should {

    "return a valid object when all the keys are valid" in {
      val key = KeySeed(
        key = Base64Encoder.encode("""-----BEGIN PUBLIC KEY-----
                                     |MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtXlEMP0Ra/cGRONtRibZ
                                     |ikQarU/cjjiJCNcc0SuuKXRYvLdCJErpKuQcReXKW0HLcBwtbndWp8VSnQnHTcAi
                                     |ok/K+K8KnQ7+zDTyRi6Wcra+gmB/JjxXxoYn9DlZAskc8kCbA/tcgsYl/pft2uc0
                                     |RsQtFLmf7qeHc41kgi8sJN7Al2nbeCoq3XktbpgBEOrvqFkm2CeloO+7O7IvOwsy
                                     |cJabgZvgMZJn3yaLxlpTiMjqmB79BrpdCLHvEh8jgiyv2vbgpXMPNV5axZZckNzQ
                                     |xMQhpG8ycd3hbkWK5ofdus04BtOG7z0fl3gTZxs7NX2CT63ODdrvJHZpaIjnK55T
                                     |lQIDAQAB
                                     |-----END PUBLIC KEY-----""".stripMargin),
        alg = "123",
        use = "sig"
      )

      val validation = ValidationTest.validateKeys(Seq(key))
      validation shouldBe a[Valid[_]]
    }

    "return a valid object when the certificate is valid" in {
      val key = KeySeed(
        key = Base64Encoder.encode("""-----BEGIN CERTIFICATE-----
                                     |MIIC6jCCAdKgAwIBAgIGAXqAeuDUMA0GCSqGSIb3DQEBCwUAMDYxNDAyBgNVBAMM
                                     |K3FZTUJLWGJRVHE4MUlqWjhoRGQ3S29neEJGdGJUU3MzWm15dFdIamFZYmcwHhcN
                                     |MjEwNzA3MTAxOTM0WhcNMjIwNTAzMTAxOTM0WjA2MTQwMgYDVQQDDCtxWU1CS1hi
                                     |UVRxODFJalo4aERkN0tvZ3hCRnRiVFNzM1pteXRXSGphWWJnMIIBIjANBgkqhkiG
                                     |9w0BAQEFAAOCAQ8AMIIBCgKCAQEAnPeNpzeQaSjURnCMvlcPCNQoYaPwSmVzUooA
                                     |DR8d7C1Wlb45+aYvDgCPgBNIhKSLfvURHd1/17jwJ72c7h9Eh+Qwp0B76PeJuphL
                                     |D7Yzz9nfodaPuplQ7YMnbqFQipDgy7P72yZJBQr1Xr3ttBjiNXQLn278kG6/et7A
                                     |EJg4yPDWmlJgf0iO9zRdoMDI7meFFrBD0JOAm8y7aeJD1w2GQRHcQBvltk3m/B44
                                     |0BiG1+0QWIDgWgYaOGCAl4xiWRsbz+bc+HZDykcwaru5bzAaekYH68Mdbg5ntvh2
                                     |Gxj7Q13uJgovApVb3BBrIRU7uGmKrAYTitHq4Oau6sndrpqoHQIDAQABMA0GCSqG
                                     |SIb3DQEBCwUAA4IBAQCPr4NFr4EDBMB1d8gw/ffYmFW+E/rKQZ6Lnm52Jf+wEx13
                                     |2W+xlL0ov5LlnfC6VUsxpM1aynoeWF/KyDGBN2A0y891f9SJAjGqnwM322u8I01H
                                     |ERxzSQKh1mGg3q+ZNlMx/WUeY0QgskMTKkoGBLxvdDtfbE7RiJ3vvXtgtzewW2zF
                                     |4V6a94UgHQP+ICw/2ZY9YUjXkZeltGttNvNKTzPw4UrRRqYudwOz8E1swwvNMKeB
                                     |hrUciDNkWWWos4GnD8prcU2DNzdn+7VWIsmV9SG6mm85p9Jiv1etvIidAlNk/WtX
                                     |gujPVi0wH0Oo/zXTJ9G+6slRDsFO6LdQrjf04eCP
                                     |-----END CERTIFICATE-----""".stripMargin),
        alg = "123",
        use = "sig"
      )

      val validation = ValidationTest.validateKeys(Seq(key))
      validation shouldBe a[Valid[_]]
      val keys = validation.getOrElse(Seq.empty)
      keys(0)._2.getKeyType.toString shouldBe "RSA"
    }

    "return a valid serialized JWK given a valid certificate" in {
      //given
      val encodedPem = KeySeed(
        key = Base64Encoder.encode("""-----BEGIN CERTIFICATE-----
                                     |MIIC6jCCAdKgAwIBAgIGAXqBCekBMA0GCSqGSIb3DQEBCwUAMDYxNDAyBgNVBAMM
                                     |K1NQcEZnUzFJQzBJZzEyYkVXZDlnc1JNcGpYWGxOOE83alduWm0yYi1VQzAwHhcN
                                     |MjEwNzA3MTI1NTQ4WhcNMjIwNTAzMTI1NTQ4WjA2MTQwMgYDVQQDDCtTUHBGZ1Mx
                                     |SUMwSWcxMmJFV2Q5Z3NSTXBqWFhsTjhPN2pXblptMmItVUMwMIIBIjANBgkqhkiG
                                     |9w0BAQEFAAOCAQ8AMIIBCgKCAQEAg5fQtCnaHyXMPtlXn7l/ZlAGlwR0XFzFsjLR
                                     |8HtUsgsdo7ZY7MToV7Oz2ZkuKayIqrwCtud9/8LijXEOw42fPon04XTOQ3HAl8zT
                                     |22lkV9f7Q0XTl1PaREEewEqOWYhGJUxRcGXqpKQMm40JGNP24+DH8WJZmUTsU83f
                                     |GAr7uats+xQq902yWKNoII2OJvGzHxhK9cDmyfNzPE8w3L6KmOs6BXMYTBOor2Vu
                                     |PeK1s2FByHtR5VuhydmE79mZJZnIBkm7N4odcWGU5qEOgFR3BlV0S51QDsw5tA31
                                     |83D8Utf+k4HjXodwyMwfR4bLJ9SPK5XvC/+3W7JNJH/awH6AsQIDAQABMA0GCSqG
                                     |SIb3DQEBCwUAA4IBAQBd5ipvdbGSg5l+FS+FKUMaATcJ2nN41Bh/eTr7U8fdhDuJ
                                     |Gsi+h7joZbCMSoLSW3z6bxyezQhseQm+Vbm1AgSl88vM47Lb4ldZ1G5Qx+0UB172
                                     |qY1FZ2MQKDKNBfiaciFcDE4kafe4Pzht8nCXUsvf6XUmRtBJoXweiFrhiaP4qXr5
                                     |VNEGKvMn2xcLicSd0DnkhOwTDu5WCp3SO6psze+8sjx7HSUJDStTZNppPnSU3RWw
                                     |KhpRlvg0kUGWnXuYPQcO27LfAtNrkfpUrrmlZ/0emcgPcoE00BjQWQ3SfjywJIdE
                                     |Xq3dfXqqnHpThqcNNnoZX0jSwT/o62zGtvGvybbL
                                     |-----END CERTIFICATE-----""".stripMargin),
        alg = "123",
        use = "sig"
      )

      val key = KeyProcessor.fromBase64encodedPEMToAPIKey("mockKID", encodedPem.key, "enc", "123").value
      key.kty shouldBe "RSA"
      key.keyOps shouldBe None
      key.use shouldBe Some("enc")
      key.alg shouldBe Some("123")
      key.kid shouldBe "mockKID"
      key.x5u shouldBe None
      key.x5t shouldBe None
      key.x5tS256 shouldBe None
      key.x5c shouldBe None
      key.crv shouldBe None
      key.x shouldBe None
      key.y shouldBe None
      key.d shouldBe None
      key.k shouldBe None
      key.n shouldBe Some(
        "g5fQtCnaHyXMPtlXn7l_ZlAGlwR0XFzFsjLR8HtUsgsdo7ZY7MToV7Oz2ZkuKayIqrwCtud9_8LijXEOw42fPon04XTOQ3HAl8zT22lkV9f7Q0XTl1PaREEewEqOWYhGJUxRcGXqpKQMm40JGNP24-DH8WJZmUTsU83fGAr7uats-xQq902yWKNoII2OJvGzHxhK9cDmyfNzPE8w3L6KmOs6BXMYTBOor2VuPeK1s2FByHtR5VuhydmE79mZJZnIBkm7N4odcWGU5qEOgFR3BlV0S51QDsw5tA3183D8Utf-k4HjXodwyMwfR4bLJ9SPK5XvC_-3W7JNJH_awH6AsQ"
      )
      key.e shouldBe Some("AQAB")
      key.p shouldBe None
      key.q shouldBe None
      key.dp shouldBe None
      key.dq shouldBe None
      key.qi shouldBe None
      key.oth shouldBe None
    }

    "return a invalid object when some of the keys are invalid" in {
      val key = KeySeed(
        key = Base64Encoder.encode("""-----BEGIN RSA PRIVATE KEY-----
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
                                     |-----END RSA PRIVATE KEY-----""".stripMargin),
        alg = "123",
        use = "sig"
      )

      val validation = ValidationTest.validateKeys(Seq(key))
      validation shouldBe a[Invalid[_]]
    }
  }

}
