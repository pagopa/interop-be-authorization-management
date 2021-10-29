package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.serializer

import akka.serialization.SerializerWithStringManifest
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.RelationshipAdded
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.serializer.v1._

import java.io.NotSerializableException

class RelationshipAddedSerializer extends SerializerWithStringManifest {

  final val version1: String = "1"

  final val currentVersion: String = version1

  override def identifier: Int = 10004

  override def manifest(o: AnyRef): String = s"${o.getClass.getName}|$currentVersion"

  final val RelationshipAddedManifest: String = classOf[RelationshipAdded].getName

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case event: RelationshipAdded =>
      serialize(event, RelationshipAddedManifest, currentVersion)
  }

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = manifest.split('|').toList match {
    case RelationshipAddedManifest :: `version1` :: Nil =>
      deserialize(v1.events.RelationshipAddedV1, bytes, manifest, currentVersion)
    case _ =>
      throw new NotSerializableException(
        s"Unable to handle manifest: [[$manifest]], currentVersion: [[$currentVersion]] "
      )
  }

}
