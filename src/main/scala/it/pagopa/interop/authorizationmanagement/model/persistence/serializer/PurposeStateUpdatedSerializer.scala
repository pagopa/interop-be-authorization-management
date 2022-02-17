package it.pagopa.interop.authorizationmanagement.model.persistence.serializer

import akka.serialization.SerializerWithStringManifest
import it.pagopa.interop.authorizationmanagement.model.persistence.PurposeStateUpdated
import it.pagopa.interop.authorizationmanagement.model.persistence.serializer.v1._

import java.io.NotSerializableException

class PurposeStateUpdatedSerializer extends SerializerWithStringManifest {

  final val version1: String = "1"

  final val currentVersion: String = version1

  override def identifier: Int = 10009

  override def manifest(o: AnyRef): String = s"${o.getClass.getName}|$currentVersion"

  final val PurposeStateUpdatedManifest: String = classOf[PurposeStateUpdated].getName

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case event: PurposeStateUpdated =>
      serialize(event, PurposeStateUpdatedManifest, currentVersion)
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = manifest.split('|').toList match {
    case PurposeStateUpdatedManifest :: `version1` :: Nil =>
      deserialize(v1.events.PurposeStateUpdatedV1, bytes, manifest, currentVersion)
    case _ =>
      throw new NotSerializableException(
        s"Unable to handle manifest: [[$manifest]], currentVersion: [[$currentVersion]] "
      )
  }

}