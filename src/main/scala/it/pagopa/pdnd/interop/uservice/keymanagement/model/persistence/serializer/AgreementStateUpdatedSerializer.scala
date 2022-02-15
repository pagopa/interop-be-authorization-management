package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.serializer

import akka.serialization.SerializerWithStringManifest
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.AgreementStateUpdated
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.serializer.v1._

import java.io.NotSerializableException

class AgreementStateUpdatedSerializer extends SerializerWithStringManifest {

  final val version1: String = "1"

  final val currentVersion: String = version1

  override def identifier: Int = 10008

  override def manifest(o: AnyRef): String = s"${o.getClass.getName}|$currentVersion"

  final val AgreementStateUpdatedManifest: String = classOf[AgreementStateUpdated].getName

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case event: AgreementStateUpdated =>
      serialize(event, AgreementStateUpdatedManifest, currentVersion)
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = manifest.split('|').toList match {
    case AgreementStateUpdatedManifest :: `version1` :: Nil =>
      deserialize(v1.events.AgreementStateUpdatedV1, bytes, manifest, currentVersion)
    case _ =>
      throw new NotSerializableException(
        s"Unable to handle manifest: [[$manifest]], currentVersion: [[$currentVersion]] "
      )
  }

}
