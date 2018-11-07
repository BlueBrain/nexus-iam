package ch.epfl.bluebrain.nexus.iam.config

import cats.Show
import cats.syntax.show._
import ch.epfl.bluebrain.nexus.rdf.Iri
import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri
import ch.epfl.bluebrain.nexus.rdf.Node.IriNode
import ch.epfl.bluebrain.nexus.rdf.syntax.node.unsafe._

/**
  * Constant vocabulary values
  */
object Vocabulary {

  /**
    * Nexus vocabulary.
    */
  object nxv {
    val base: Iri.AbsoluteIri          = url"https://bluebrain.github.io/nexus/vocabulary/".value
    private[Vocabulary] implicit val _ = IriNode(base)

    /**
      * @param suffix the segment to suffix to the base
      * @return an [[IriNode]] composed by the ''base'' plus the provided ''suffix''
      */
    def withSuffix(suffix: String): IriNode = IriNode(base + suffix)

    // Metadata vocabulary
    val rev           = Metadata("rev")
    val deprecated    = Metadata("deprecated")
    val createdAt     = Metadata("createdAt")
    val updatedAt     = Metadata("updatedAt")
    val createdBy     = Metadata("createdBy")
    val updatedBy     = Metadata("updatedBy")
    val constrainedBy = Metadata("constrainedBy")
    val self          = Metadata("self")
    val project       = Metadata("project")
    val total         = Metadata("total")
    val results       = Metadata("results")
    val maxScore      = Metadata("maxScore")
    val score         = Metadata("score")
    val uuid          = Metadata("uuid")

    //Resource types
    val Realm             = withSuffix("Realm")
    val Permissions       = withSuffix("Permissions")
    val AccessControlList = withSuffix("AccessControlList")

  }

  /**
    * Metadata vocabulary.
    *
    * @param prefix the prefix associated to this term, used in the Json-LD context
    * @param value  the fully expanded [[AbsoluteIri]] to what the ''prefix'' resolves
    */
  final case class Metadata(prefix: String, value: AbsoluteIri)

  object Metadata {

    /**
      * Constructs a [[Metadata]] vocabulary term from the given ''base'' and the provided ''lastSegment''.
      *
      * @param lastSegment the last segment to append to the ''base'' to build the metadata
      *                    vocabulary term
      */
    def apply(lastSegment: String)(implicit base: IriNode): Metadata =
      Metadata("_" + lastSegment, url"${base.value.show + lastSegment}".value)

    implicit def metadatataIri(m: Metadata): IriNode             = IriNode(m.value)
    implicit def metadatataAbsoluteIri(m: Metadata): AbsoluteIri = m.value
    implicit def metadataToIriF(p: Metadata): IriNode => Boolean = _ == IriNode(p.value)
    implicit val metadatataShow: Show[Metadata]                  = Show.show(_.value.show)
  }
}
