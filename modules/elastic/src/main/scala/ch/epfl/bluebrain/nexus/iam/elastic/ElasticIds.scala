package ch.epfl.bluebrain.nexus.iam.elastic

import java.net.URLEncoder

import cats.syntax.show._
import ch.epfl.bluebrain.nexus.commons.iam.acls.Path
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity

object ElasticIds {

  /**
    * Generates the ElasticSearch index id from the provided ''identity''.
    *
    * @param identity the identity from where to generate the index.
    *                 Ids will look as follows: {prefix}_{identity_id_url_encoded}
    * @param config   the configuration from where to take the prefix for the id
    */
  private[elastic] implicit def indexId(identity: Identity)(implicit config: ElasticConfig): String =
    URLEncoder.encode(s"${config.indexPrefix}_${identity.id.show}", "UTF-8").toLowerCase

  /**
    * Generates the ElasticSearch Document id from the provided ''path''.
    *
    * @param path the path from where to generate the id
    */
  private[elastic] def id(path: Path): String =
    URLEncoder.encode(path.show, "UTF-8").toLowerCase
}
