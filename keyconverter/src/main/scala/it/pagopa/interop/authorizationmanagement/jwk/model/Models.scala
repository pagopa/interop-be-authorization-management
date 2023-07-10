package it.pagopa.interop.authorizationmanagement.jwk.model

object Models {
  final case class JwkKey(
    kty: String,
    keyOps: Option[Seq[String]] = None,
    use: Option[String] = None,
    alg: Option[String] = None,
    kid: String,
    x5u: Option[String] = None,
    x5t: Option[String] = None,
    x5tS256: Option[String] = None,
    x5c: Option[Seq[String]] = None,
    crv: Option[String] = None,
    x: Option[String] = None,
    y: Option[String] = None,
    d: Option[String] = None,
    k: Option[String] = None,
    n: Option[String] = None,
    e: Option[String] = None,
    p: Option[String] = None,
    q: Option[String] = None,
    dp: Option[String] = None,
    dq: Option[String] = None,
    qi: Option[String] = None,
    oth: Option[Seq[JwkOtherPrimeInfo]] = None
  )
  final case class JwkOtherPrimeInfo(r: String, d: String, t: String)

  sealed trait JwkKeyUse

  case object JwkSig extends JwkKeyUse
  case object JwkEnc extends JwkKeyUse
}

object Adapters {

  import Models._
  implicit class JwkKeyUseWrapper(private val p: JwkKeyUse) extends AnyVal {
    def toRfcValue: String = p match {
      case JwkSig => "sig"
      case JwkEnc => "enc"
    }
  }
}
