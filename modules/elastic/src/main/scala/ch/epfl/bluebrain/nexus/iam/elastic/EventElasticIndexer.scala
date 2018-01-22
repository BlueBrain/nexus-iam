package ch.epfl.bluebrain.nexus.iam.elastic

import java.net.URLEncoder

import cats.MonadError
import cats.syntax.flatMap._
import cats.syntax.show._
import ch.epfl.bluebrain.nexus.commons.es.client.ElasticClient
import ch.epfl.bluebrain.nexus.commons.iam.acls.Event._
import ch.epfl.bluebrain.nexus.commons.iam.acls.{Event, Path}
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity
import io.circe.Json
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._
import io.circe.syntax._
import journal.Logger

/**
  * Event incremental indexing logic that pushes data into an ElasticSearch indexer.
  *
  * @param client   the ElasticSearch client to use for communicating with the ElasticSearch indexer
  * @param settings the indexing settings
  * @tparam F the monadic effect type
  */
class EventElasticIndexer[F[_]](client: ElasticClient[F], settings: ElasticIndexingSettings)(
    implicit F: MonadError[F, Throwable]) {

  private val log                            = Logger[this.type]
  private implicit val config: Configuration = Configuration.default.withDiscriminator("type")

  /**
    * Indexes the event by pushing it's json ld representation into the ElasticSearch indexer while also updating the
    * existing content.
    *
    * @param event the event to index
    * @return a Unit value in the ''F[_]'' context
    */
  final def apply(event: Event): F[Unit] = event match {
    case PermissionsAdded(path, acls, _) =>
      log.debug(s"Indexing 'PermissionsAdded' event for path '${path.show}' with acls '$acls'")
      acls.toMap.foldLeft(F.pure(())) {
        case (acc, (identity, perms)) =>
          val event       = ElasticPermissions(path, identity, perms)
          val updateQuery = Json.obj("doc" -> event.asJson, "doc_as_upsert" -> Json.fromBoolean(true))
          acc.flatMap(_ => client.update(indexId(identity), settings.docType, id(path), updateQuery))
      }

    case PermissionsSubtracted(path, identity, perms, _) =>
      log.debug(
        s"Indexing 'PermissionsSubtracted' event for path '${path.show}' with identity '$identity' and permisions '$perms'")
      val patchQuery = Json.obj(
        "script" -> Json.obj("source" -> Json.fromString("ctx._source.permissions.removeAll(params.permissions)"),
                             "params" -> Json.obj("permissions" -> perms.set.asJson)))
      client.update(indexId(identity), settings.docType, id(path), patchQuery)

    case PermissionsRemoved(path, identity, _) =>
      log.debug(s"Indexing 'PermissionsRemoved' event for path '${path.show}' and identity $identity")
      client.delete(indexId(identity), settings.docType, id(path))

    case PermissionsCleared(path, _) =>
      log.debug(s"Indexing 'PermissionsCleared' event for path '${path.show}'")
      client.deleteDocuments(
        query = Json.obj("query" -> Json.obj("match" -> Json.obj("path" -> Json.fromString(path.show)))))
  }

  private def indexId(identity: Identity): String =
    URLEncoder.encode(s"${settings.indexPrefix}_${identity.id.show}", "UTF-8").toLowerCase

  private def id(path: Path): String =
    URLEncoder.encode(path.show, "UTF-8").toLowerCase

}

object EventElasticIndexer {

  /**
    * Constructs a event incremental indexer that pushes data into an ElasticSearch indexer.
    *
    * @param client   the ElasticSearch client to use for communicating with the ElasticSearch indexer
    * @param settings the indexing settings
    * @tparam F the monadic effect type
    */
  final def apply[F[_]](client: ElasticClient[F], settings: ElasticIndexingSettings)(
      implicit F: MonadError[F, Throwable]): EventElasticIndexer[F] =
    new EventElasticIndexer[F](client, settings)
}
