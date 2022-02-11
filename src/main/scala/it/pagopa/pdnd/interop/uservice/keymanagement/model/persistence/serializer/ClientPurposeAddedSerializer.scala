package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.serializer

import akka.serialization.SerializerWithStringManifest
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.ClientPurposeAdded
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.serializer.v1._

import java.io.NotSerializableException

class ClientPurposeAddedSerializer extends SerializerWithStringManifest {

  final val version1: String = "1"

  final val currentVersion: String = version1

  override def identifier: Int = 10002

  override def manifest(o: AnyRef): String = s"${o.getClass.getName}|$currentVersion"

  final val ClientPurposeAddedManifest: String = classOf[ClientPurposeAdded].getName

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case event: ClientPurposeAdded =>
      serialize(event, ClientPurposeAddedManifest, currentVersion)
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = manifest.split('|').toList match {
    case ClientPurposeAddedManifest :: `version1` :: Nil =>
      deserialize(v1.events.ClientPurposeAddedV1, bytes, manifest, currentVersion)
    case _ =>
      throw new NotSerializableException(
        s"Unable to handle manifest: [[$manifest]], currentVersion: [[$currentVersion]] "
      )
  }

}
