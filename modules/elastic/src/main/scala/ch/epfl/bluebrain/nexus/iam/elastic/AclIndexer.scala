package ch.epfl.bluebrain.nexus.iam.elastic

import java.net.URLEncoder
import java.util.regex.Pattern.quote

import cats.{MonadError, Traverse}
import cats.instances.list._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.show._
import ch.epfl.bluebrain.nexus.commons.es.client.ElasticClient
import ch.epfl.bluebrain.nexus.commons.iam.acls.Event._
import ch.epfl.bluebrain.nexus.commons.iam.acls.{Event, Path}
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity
import ch.epfl.bluebrain.nexus.commons.iam.io.serialization.SimpleIdentitySerialization._
import ch.epfl.bluebrain.nexus.commons.test.Resources
import ch.epfl.bluebrain.nexus.commons.types.ConcurrentSetBuilder
import io.circe.{Encoder, Json, Printer}
import io.circe.generic.semiauto.deriveEncoder
import io.circe.java8.time._
import io.circe.syntax._
import io.circe.parser._
import journal.Logger

/**
  * Event incremental indexing logic that pushes data into an ElasticSearch indexer.
  *
  * @param client   the ElasticSearch client to use for communicating with the ElasticSearch indexer
  * @param settings the indexing settings
  * @tparam F the monadic effect type
  */
class AclIndexer[F[_]](client: ElasticClient[F], settings: ElasticConfig)(implicit F: MonadError[F, Throwable])
    extends Resources {

  private val log            = Logger[this.type]
  private val indices        = ConcurrentSetBuilder[String]()
  private lazy val indexJson = jsonContentOf("/elastic-mappings.json", Map(quote("{{type}}") -> settings.docType))
  private val printer        = Printer.noSpaces.copy(dropNullValues = true)

  /**
    * Indexes the event by pushing it's json ld representation into the ElasticSearch indexer while also updating the
    * existing content.
    *
    * @param event the event to index
    * @return a Unit value in the ''F[_]'' context
    */
  final def apply(event: Event): F[Unit] = event match {
    case PermissionsAdded(path, acls, m) =>
      log.debug(s"Indexing 'PermissionsAdded' event for path '${path.show}' with acls '$acls'")
      Traverse[List].sequence_(acls.toMap.foldLeft(List(F.pure(()))) {
        case (acc, (identity, perms)) =>
          val eventUpdate = AclDocument(path, identity, perms, updated = m.instant)
          val eventCreate = eventUpdate.copy(created = Some(m.instant))
          val updateQuery = Json.obj("doc" -> eventUpdate.asJson, "upsert" -> eventCreate.asJson)
          val index       = indexId(identity)
          createIndexIfNotExist(index)
            .flatMap(_ => client.update(index, settings.docType, id(path), updateQuery)) :: acc
      })

    case PermissionsSubtracted(path, identity, perms, m) =>
      log.debug(
        s"Indexing 'PermissionsSubtracted' event for path '${path.show}' with identity '$identity' and permisions '$perms'")
      val patchQuery = Json.obj(
        "script" -> Json.obj(
          "source" -> Json.fromString(
            "ctx._source.permissions.removeAll(params.permissions);ctx._source.updated = params.updated"),
          "params" -> Json.obj("permissions" -> perms.set.asJson, "updated" -> Json.fromString(m.instant.toString))
        ))
      client.update(indexId(identity), settings.docType, id(path), patchQuery)

    case PermissionsRemoved(path, identity, _) =>
      log.debug(s"Indexing 'PermissionsRemoved' event for path '${path.show}' and identity $identity")
      client.delete(indexId(identity), settings.docType, id(path))

    case PermissionsCleared(path, _) =>
      log.debug(s"Indexing 'PermissionsCleared' event for path '${path.show}'")
      client.deleteDocuments(
        query = Json.obj("query" -> Json.obj("term" -> Json.obj("path" -> Json.fromString(path.show)))))
  }

  private def indexId(identity: Identity): String =
    URLEncoder.encode(s"${settings.indexPrefix}_${identity.id.show}", "UTF-8").toLowerCase

  private def id(path: Path): String =
    URLEncoder.encode(path.show, "UTF-8").toLowerCase

  private def createIndexIfNotExist(index: String): F[Unit] =
    if (!indices(index))
      client.createIndexIfNotExist(index, indexJson).map(_ => indices += index)
    else
      F.pure(())

  private implicit val encodeRemoveNullKeys: Encoder[AclDocument] = {
    val enc = deriveEncoder[AclDocument]
    Encoder.instance(v => parse(printer.pretty(enc(v))).getOrElse(Json.obj()))
  }

}

object AclIndexer {

  /**
    * Constructs a event incremental indexer that pushes data into an ElasticSearch indexer.
    *
    * @param client   the ElasticSearch client to use for communicating with the ElasticSearch indexer
    * @param settings the indexing settings
    * @tparam F the monadic effect type
    */
  final def apply[F[_]](client: ElasticClient[F], settings: ElasticConfig)(
      implicit F: MonadError[F, Throwable]): AclIndexer[F] =
    new AclIndexer[F](client, settings)
}
