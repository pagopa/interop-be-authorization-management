package it.pagopa.interop.authorizationmanagement.model.persistence

import JsonFormats._
import spray.json._
import it.pagopa.interop.commons.queue.message.ProjectableEvent

object AuthorizationEventsSerde {

  val authToJson: PartialFunction[ProjectableEvent, JsValue] = {
    case x: PurposeStateUpdated               => x.toJson
    case x: RelationshipAdded                 => x.toJson
    case x: UserAdded                         => x.toJson
    case x: AgreementStateUpdated             => x.toJson
    case x: ClientAdded                       => x.toJson
    case x: ClientPurposeAdded                => x.toJson
    case x: ClientPurposeRemoved              => x.toJson
    case x: RelationshipRemoved               => x.toJson
    case x: UserRemoved                       => x.toJson
    case x: AgreementAndEServiceStatesUpdated => x.toJson
    case x: KeyDeleted                        => x.toJson
    case x: ClientDeleted                     => x.toJson
    case x: KeysAdded                         => x.toJson
    case x: EServiceStateUpdated              => x.toJson
    case x: KeyRelationshipToUserMigrated     => x.toJson
  }

  private val purposeStateUpdated: String               = "purpose-state-updated"
  private val relationshipAdded: String                 = "relationship-added"
  private val userAdded: String                         = "user-added"
  private val agreementStateUpdated: String             = "agreement-state-updated"
  private val clientAdded: String                       = "client-added"
  private val clientPurposeAdded: String                = "client-purpose-added"
  private val clientPurposeRemoved: String              = "client-purpose-removed"
  private val relationshipRemoved: String               = "relationship-removed"
  private val userRemoved: String                       = "user-removed"
  private val agreementAndEServiceStatesUpdated: String = "agreement-and-e-service-states-updated"
  private val keyDeleted: String                        = "key-deleted"
  private val clientDeleted: String                     = "client-deleted"
  private val keysAdded: String                         = "keys-added"
  private val eServiceStateUpdated: String              = "e-service-state-updated"
  private val keyRelationshipToUserMigrated: String     = "key-relationship-to-user-migrated"

  val jsonToAuth: PartialFunction[String, JsValue => ProjectableEvent] = {
    case `purposeStateUpdated`               => _.convertTo[PurposeStateUpdated]
    case `relationshipAdded`                 => _.convertTo[RelationshipAdded]
    case `userAdded`                         => _.convertTo[UserAdded]
    case `agreementStateUpdated`             => _.convertTo[AgreementStateUpdated]
    case `clientAdded`                       => _.convertTo[ClientAdded]
    case `clientPurposeAdded`                => _.convertTo[ClientPurposeAdded]
    case `clientPurposeRemoved`              => _.convertTo[ClientPurposeRemoved]
    case `relationshipRemoved`               => _.convertTo[RelationshipRemoved]
    case `userRemoved`                       => _.convertTo[UserRemoved]
    case `agreementAndEServiceStatesUpdated` => _.convertTo[AgreementAndEServiceStatesUpdated]
    case `keyDeleted`                        => _.convertTo[KeyDeleted]
    case `clientDeleted`                     => _.convertTo[ClientDeleted]
    case `keysAdded`                         => _.convertTo[KeysAdded]
    case `eServiceStateUpdated`              => _.convertTo[EServiceStateUpdated]
    case `keyRelationshipToUserMigrated`     => _.convertTo[KeyRelationshipToUserMigrated]
  }

  def getKind(e: Event): String = e match {
    case _: PurposeStateUpdated               => purposeStateUpdated
    case _: RelationshipAdded                 => relationshipAdded
    case _: UserAdded                         => userAdded
    case _: AgreementStateUpdated             => agreementStateUpdated
    case _: ClientAdded                       => clientAdded
    case _: ClientPurposeAdded                => clientPurposeAdded
    case _: ClientPurposeRemoved              => clientPurposeRemoved
    case _: RelationshipRemoved               => relationshipRemoved
    case _: UserRemoved                       => userRemoved
    case _: AgreementAndEServiceStatesUpdated => agreementAndEServiceStatesUpdated
    case _: KeyDeleted                        => keyDeleted
    case _: ClientDeleted                     => clientDeleted
    case _: KeysAdded                         => keysAdded
    case _: EServiceStateUpdated              => eServiceStateUpdated
    case _: KeyRelationshipToUserMigrated     => keyRelationshipToUserMigrated
  }

}
