package ch.epfl.bluebrain.nexus.iam

import ch.epfl.bluebrain.nexus.iam.config.Vocabulary.nxv
import ch.epfl.bluebrain.nexus.iam.index.KeyValueStore
import ch.epfl.bluebrain.nexus.iam.types.{Label, Permission, ResourceF, ResourceMetadata}
import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri
import ch.epfl.bluebrain.nexus.sourcing.Aggregate

package object realms {

  /* Type annotations specific to realms */
  type Event     = RealmEvent
  type State     = RealmState
  type Command   = RealmCommand
  type Rejection = RealmRejection
  type Agg[F[_]] = Aggregate[F, String, Event, State, Command, Rejection]

  type EventOrRejection = Either[Rejection, Event]
  type MetaOrRejection  = Either[Rejection, ResourceMetadata]

  type Resource    = ResourceF[Either[DeprecatedRealm, ActiveRealm]]
  type OptResource = Option[Resource]

  type RealmIndex[F[_]] = KeyValueStore[F, Label, Resource]

  /**
    * The constant collection of realm types.
    */
  final val types: Set[AbsoluteIri] = Set(nxv.Realm.value)

  /* Constant permissions */
  final val read  = Permission.unsafe("realms/read")
  final val write = Permission.unsafe("realms/write")

}
