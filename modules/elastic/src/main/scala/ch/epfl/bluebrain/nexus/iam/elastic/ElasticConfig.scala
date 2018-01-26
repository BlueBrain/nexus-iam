package ch.epfl.bluebrain.nexus.iam.elastic

import akka.http.scaladsl.model.Uri

/**
  * Collection of configurable settings specific to  the ElasticSearch indexer.
  *
  * @param baseUri     the application base uri for operating on resources
  * @param indexPrefix the name of the index
  * @param docType     the name of the `type`
  */
final case class ElasticConfig(baseUri: Uri, indexPrefix: String, docType: String)
