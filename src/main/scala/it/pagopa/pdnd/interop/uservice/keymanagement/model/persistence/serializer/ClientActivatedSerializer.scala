package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.serializer

import akka.serialization.SerializerWithStringManifest
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.ClientActivated
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.serializer.v1._

import java.io.NotSerializableException

class ClientActivatedSerializer extends SerializerWithStringManifest {

  final val version1: String = "1"

  final val currentVersion: String = version1

  override def identifier: Int = 10006

  override def manifest(o: AnyRef): String = s"${o.getClass.getName}|$currentVersion"

  final val ClientActivatedManifest: String = classOf[ClientActivated].getName

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case event: ClientActivated =>
      serialize(event, ClientActivatedManifest, currentVersion)
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = manifest.split('|').toList match {
    case ClientActivatedManifest :: `version1` :: Nil =>
      deserialize(v1.events.ClientActivatedV1, bytes, manifest, currentVersion)
    case _ =>
      throw new NotSerializableException(
        s"Unable to handle manifest: [[$manifest]], currentVersion: [[$currentVersion]] "
      )
  }

}
