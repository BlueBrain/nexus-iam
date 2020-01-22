package ch.epfl.bluebrain.nexus.iam.client.types

import ch.epfl.bluebrain.nexus.iam.client.config.Contexts._
import ch.epfl.bluebrain.nexus.iam.client.config.IamClientConfig
import ch.epfl.bluebrain.nexus.iam.client.config.Vocabulary._
import ch.epfl.bluebrain.nexus.rdf.Iri.Path
import ch.epfl.bluebrain.nexus.rdf.implicits._
import io.circe._
import io.circe.syntax._

import scala.collection.immutable.ListMap

/**
  * Type definition representing a mapping of Paths to AccessControlList for a specific resource.
  *
  * @param value a map of path and AccessControlList
  */
final case class AccessControlLists(value: Map[Path, ResourceAccessControlList]) {

  /**
    * Adds a key pair of Path and [[ResourceAccessControlList]] to the current ''value'' and returns a new [[AccessControlLists]] with the added acl.
    *
    * @param entry the key pair of Path and ACL to be added
    */
  def +(entry: (Path, ResourceAccessControlList)): AccessControlLists = {
    val (path, aclResource) = entry
    val toAdd               = aclResource.copy(value = value.get(path).map(_.value ++ aclResource.value).getOrElse(aclResource.value))
    AccessControlLists(value + (path -> toAdd))
  }

  /**
    * @return new [[AccessControlLists]] with the same elements as the current one but sorted by [[Path]] (alphabetically)
    */
  def sorted: AccessControlLists =
    AccessControlLists(ListMap(value.toSeq.sortBy { case (path, _) => path.asString }: _*))

  /**
    * Generates a new [[AccessControlLists]] only containing the provided ''identities''.
    *
    * @param identities the identities to be filtered
    */
  def filter(identities: Set[Identity]): AccessControlLists =
    value.foldLeft(AccessControlLists.empty) {
      case (acc, (p, aclResource)) =>
        val list = aclResource.copy(value = aclResource.value.filter(identities))
        acc + (p -> list)
    }

}

object AccessControlLists {

  /**
    * An empty [[AccessControlLists]].
    */
  val empty: AccessControlLists = AccessControlLists(Map.empty[Path, ResourceAccessControlList])

  /**
    * Convenience factory method to build an ACLs from var args of ''Path'' to ''AccessControlList'' tuples.
    */
  final def apply(tuple: (Path, ResourceAccessControlList)*): AccessControlLists = AccessControlLists(tuple.toMap)

  implicit def aclsEncoder(implicit http: IamClientConfig): Encoder[AccessControlLists] = Encoder.encodeJson.contramap {
    case AccessControlLists(value) =>
      val arr = value.map {
        case (path, acl) =>
          Json.obj("_path" -> Json.fromString(path.asString)) deepMerge acl.asJson.removeKeys("@context")
      }
      Json
        .obj(nxv.total.prefix -> Json.fromInt(arr.size), nxv.results.prefix -> Json.arr(arr.toSeq: _*))
        .addContext(resourceCtxUri)
        .addContext(iamCtxUri)
        .addContext(searchCtxUri)
  }

  implicit def aclsDecoder: Decoder[AccessControlLists] = {
    import cats.implicits._

    def jsonToPathedAcl(hc: HCursor): Either[DecodingFailure, (Path, ResourceAccessControlList)] =
      for {
        path <- hc.get[Path]("_path")
        acl  <- hc.value.as[ResourceAccessControlList]
      } yield path -> acl

    Decoder.instance { hc =>
      hc.downField(nxv.results.prefix)
        .focus
        .flatMap(_.asArray)
        .toRight(DecodingFailure(s"'${nxv.results.prefix}' field not found", hc.history))
        .flatMap { results =>
          results
            .foldM(Map.empty[Path, ResourceAccessControlList]) { (acc, json) =>
              jsonToPathedAcl(json.hcursor).map(acc + _)
            }
            .map(AccessControlLists(_))
        }
    }
  }
}
