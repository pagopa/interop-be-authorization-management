package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.serializer

import akka.serialization.SerializerWithStringManifest
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.RelationshipRemoved
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.serializer.v1._

import java.io.NotSerializableException

class RelationshipRemovedSerializer extends SerializerWithStringManifest {

  final val version1: String = "1"

  final val currentVersion: String = version1

  override def identifier: Int = 10005

  override def manifest(o: AnyRef): String = s"${o.getClass.getName}|$currentVersion"

  final val RelationshipRemovedManifest: String = classOf[RelationshipRemoved].getName

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case event: RelationshipRemoved =>
      serialize(event, RelationshipRemovedManifest, currentVersion)
  }

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = manifest.split('|').toList match {
    case RelationshipRemovedManifest :: `version1` :: Nil =>
      deserialize(v1.events.RelationshipRemovedV1, bytes, manifest, currentVersion)
    case _ =>
      throw new NotSerializableException(
        s"Unable to handle manifest: [[$manifest]], currentVersion: [[$currentVersion]] "
      )
  }

}
