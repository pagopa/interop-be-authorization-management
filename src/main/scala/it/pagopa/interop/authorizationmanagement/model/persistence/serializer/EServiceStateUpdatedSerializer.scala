package it.pagopa.interop.authorizationmanagement.model.persistence.serializer

import akka.serialization.SerializerWithStringManifest
import it.pagopa.interop.authorizationmanagement.model.persistence.EServiceStateUpdated
import it.pagopa.interop.authorizationmanagement.model.persistence.serializer.v1._

import java.io.NotSerializableException

class EServiceStateUpdatedSerializer extends SerializerWithStringManifest {

  final val version1: String = "1"

  final val currentVersion: String = version1

  override def identifier: Int = 10007

  override def manifest(o: AnyRef): String = s"${o.getClass.getName}|$currentVersion"

  final val EServiceStateUpdatedManifest: String = classOf[EServiceStateUpdated].getName

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case event: EServiceStateUpdated =>
      serialize(event, EServiceStateUpdatedManifest, currentVersion)
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = manifest.split('|').toList match {
    case EServiceStateUpdatedManifest :: `version1` :: Nil =>
      deserialize(v1.events.EServiceStateUpdatedV1, bytes, manifest, currentVersion)
    case _ =>
      throw new NotSerializableException(
        s"Unable to handle manifest: [[$manifest]], currentVersion: [[$currentVersion]] "
      )
  }

}