package ch.epfl.bluebrain.nexus.iam.elastic

import java.net.URLEncoder

import cats.syntax.show._
import ch.epfl.bluebrain.nexus.iam.types.Identity
import ch.epfl.bluebrain.nexus.iam.core.acls.types.Permission
import ch.epfl.bluebrain.nexus.service.http.Path

object ElasticIds {

  /**
    * Generates the ElasticSearch index id from the provided ''identity''.
    *
    * @param identity the identity from where to generate the index.
    *                 Ids will look as follows: {prefix}_{identity_id_url_encoded}
    * @param config   the configuration from where to take the prefix for the id
    */
  private[elastic] implicit def indexId(identity: Identity)(implicit config: ElasticConfig): String =
    s"${config.indexPrefix}_${identity.id.show}".toLowerCase

  /**
    * Generates the ElasticSearch Document id from the provided ''path'' and ''permission''.
    *
    * @param path the path from where to generate the id
    * @param permission the permission from where to generate the id
    */
  private[elastic] def id(path: Path, permission: Permission): String =
    URLEncoder.encode(s"${path.show}_${permission.show}", "UTF-8").toLowerCase
}
