package it.pagopa.interop.authorizationmanagement

import it.pagopa.interop.commons.jwt._

package object util {

  final val existingRoles: Seq[String] = Seq(ADMIN_ROLE, SECURITY_ROLE, API_ROLE, M2M_ROLE, INTERNAL_ROLE)

}
