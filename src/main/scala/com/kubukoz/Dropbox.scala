package com.kubukoz

//todo: some parameters somewhere might be redundant

import cats.Functor
import cats.data.NonEmptyList
import cats.implicits._
import fs2.Stream

trait Dropbox[F[_]] {
  def listFolder(path: Dropbox.Path, recursive: Boolean): F[Paginable[File]]
  def listFolderContinue(cursor: String): F[Paginable[File]]
}

object Dropbox {
  def apply[F[_]](implicit F: Dropbox[F]): Dropbox[F] = F

  sealed trait Path extends Product with Serializable

  object Path {
    //todo: we're not really using this
    final case class Relative(segments: NonEmptyList[String]) extends Path
    case object Root extends Path
  }

  def paginate[F[_]: Functor, Element](fetch: Option[String] => F[Paginable[Element]]): Stream[F, Element] = Stream
    .unfoldLoopEval[F, Option[String], List[Element]](Option.empty[String]) {
      fetch(_).map(pagin => (pagin.entries, pagin.cursor.some.filter(_ => pagin.hasMore).map(_.some)))
    }
    .flatMap(fs2.Stream.emits)

}

trait DropboxFileStream[F[_]] {
  def streamFolder(path: Dropbox.Path): Stream[F, File]
}

object DropboxFileStream {

  def instance[F[_]: Functor: Dropbox]: DropboxFileStream[F] = path =>
    Dropbox.paginate {
      case None         => Dropbox[F].listFolder(path, recursive = true)
      case Some(cursor) => Dropbox[F].listFolderContinue(cursor)
    }

}

final case class Paginable[Element](entries: List[Element], cursor: String, hasMore: Boolean)

final case class File(tag: File.Tag, name: String, pathLower: String, pathDisplay: String, id: String)

object File {

  sealed trait Tag extends Product with Serializable

  object Tag {
    case object Folder extends Tag
    case object File extends Tag
  }

}
