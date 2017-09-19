package ch.epfl.bluebrain.nexus.iam.core.acls

import cats.Show
import io.circe.{Decoder, Encoder}

import scala.annotation.tailrec

/**
  * Represents an absolute resource path as an optionally empty ordered collection of segments.  The canonical form of
  * a path would be ''/a/b/c/d''.
  */
sealed abstract class Path extends Serializable {

  /**
    * The type of the Head element of this path
    */
  type Head

  /**
    * @return __true__ if this __Path__ has no segments (is Empty), __false__ otherwise
    */
  def isEmpty: Boolean

  /**
    * @return the __head__ element of this __Path__
    */
  def head: Head

  /**
    * @return the remainder of this __Path__ after subtracting its head.
    */
  def tail: Path

  /**
    * @return the number of segments in this __Path__
    */
  def length: Int

  /**
    * Constructs a new __Path__ instance by appending the argument segment as the new head of the resulting path.
    * __This__ path will become the tail of the returned path.
    *
    * @param segment the segment to append as the head of the new path
    * @return a new __Path__ instance constructed from the argument __segment__ as its head and __this__ path as its
    *         tail
    */
  def /(segment: String): Path

  /**
    * Joins __this__ Path with the argument __path__.
    *
    * @param path the path to join with __this__
    * @return a new __Path__ instance constructed by joining __this__ and the argument __path__
    */
  def ++(path: Path): Path

  /**
    * @return a new __Path__ instance constructed by reversing the order of segments of __this__ path
    */
  def reverse: Path

  /**
    * @return a human readable representation of this path in its canonical form.  I.e.: __/a/b/c/d__
    */
  def repr: String

  /**
    * @return a human readable path in its canonical form.  I.e.: __/a/b/c/d__
    */
  override def toString: String = repr
}

object Path {

  implicit val pathEncoder: Encoder[Path] = Encoder.encodeString.contramap(_.repr)

  implicit val pathDecoder: Decoder[Path] = Decoder.decodeString.emap(string => Right(Path(string)))

  implicit val pathShow: Show[Path] = Show.show(_.repr)

  /**
    * Constructs a path instance from the argument ''string'' using ''/'' as segment separator.
    *
    * @param string the source string to convert to a [[ch.epfl.bluebrain.nexus.iam.core.acls.Path]]
    * @return a new [[ch.epfl.bluebrain.nexus.iam.core.acls.Path]] instance built from the argument ''string''
    */
  def apply(string: String): Path = {
    @tailrec
    def inner(acc: Path, remaining: String): Path =
      if (remaining.length == 0) acc
      else {
        val index = remaining.indexOf("/")
        if (index == -1) acc / remaining
        else if (index == 0) inner(acc, remaining.substring(1))
        else inner(acc / remaining.substring(0, index), remaining.substring(index + 1, remaining.length))
      }
    inner(Empty, string)
  }

  /**
    * The constant empty path; it contains no segments.
    */
  val / : Path = Empty

  /**
    * Construct a new __Path__ containing a single segment provided as the argument of this application.
    *
    * @param segment the singleton segment of the path
    * @return a new __Path__ containing a single segment provided as the argument of this application
    */
  def /(segment: String): Path = Segment(segment, Empty)

  /**
    * The empty __Path__ implementation.  It contains no segments, but facilitates path construction.
    */
  case object Empty extends Path {
    override type Head = this.type

    override val isEmpty: Boolean = true

    override def head: Head = this

    override def tail: Path = this

    override val length: Int = 0

    override def /(segment: String): Path = Segment(segment, Empty)

    override def ++(path: Path): Path = path

    override val reverse: this.type = Empty

    override val repr: String = "/"
  }

  /**
    * The non empty __Path__ implementation.  It contains a __Head__ element of the fixed type __String__ and an
    * optionally empty __tail__.
    *
    * Note: the head of a __Path__ is the last segment as represented in the canonical form.
    *
    * @param head the head of this __Path__
    * @param tail the tail of this __Path__
    */
  final case class Segment(head: String, tail: Path) extends Path {

    override type Head = String

    override val isEmpty: Boolean = false

    override def length: Int = tail.length + 1

    override def /(segment: String): Segment = Segment(segment, this)

    override def ++(path: Path): Path = {
      def inner(acc: Path, remaining: Path): Path = remaining match {
        case Empty         => acc
        case Segment(h, t) => inner(Segment(h, acc), t)
      }
      inner(this, path.reverse)
    }

    override def reverse: Path = {
      @tailrec
      def inner(acc: Path, remaining: Path): Path = remaining match {
        case Empty         => acc
        case Segment(h, t) => inner(Segment(h, acc), t)
      }
      inner(Empty, this)
    }

    override def repr: String = tail match {
      case Empty => "/" + head
      case _     => tail.repr + "/" + head
    }
  }

  /**
    * Syntax sugar that allows constructing __Path__ instances omitting the __Empty__ tail.  Example:
    * {{{
    *   val path = "a" / "b" / "c" / "d"
    * }}}
    *
    * @param string the first element of the path (in its canonical form)
    */
  implicit class FromString(val string: String) extends AnyVal {

    /**
      * Constructs a new __Path__ instance by appending the argument segment as the new head of the resulting path.
      * __This__ string will become the tail of the returned path.
      *
      * @param segment the segment to append as the head of the new path
      * @return a new __Path__ instance constructed from the argument __segment__ as its head and __this__ string as its
      *         tail
      */
    def /(segment: String) = Empty / string / segment
  }
}
