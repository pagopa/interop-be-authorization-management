package it.pagopa.interop.authorizationmanagement.model.persistence.serializer

import akka.serialization.SerializerWithStringManifest
import it.pagopa.interop.authorizationmanagement.model.persistence.ClientDeleted
import it.pagopa.interop.authorizationmanagement.model.persistence.serializer.v1._

import java.io.NotSerializableException

class ClientDeletedSerializer extends SerializerWithStringManifest {

  final val version1: String = "1"

  final val currentVersion: String = version1

  override def identifier: Int = 10003

  override def manifest(o: AnyRef): String = s"${o.getClass.getName}|$currentVersion"

  final val ClientDeletedManifest: String = classOf[ClientDeleted].getName

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case event: ClientDeleted =>
      serialize(event, ClientDeletedManifest, currentVersion)
    case _ =>
      throw new NotSerializableException(
        s"Unable to handle manifest: [[${manifest(o)}]], currentVersion: [[$currentVersion]] "
      )
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = manifest.split('|').toList match {
    case ClientDeletedManifest :: `version1` :: Nil =>
      deserialize(v1.events.ClientDeletedV1, bytes, manifest, currentVersion)
    case _ =>
      throw new NotSerializableException(
        s"Unable to handle manifest: [[$manifest]], currentVersion: [[$currentVersion]] "
      )
  }

}
