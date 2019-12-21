package com.rojoma.simplearm.v2

import scala.language.implicitConversions

import java.io.{File, IOException}
import java.nio.file.{Path, Files, SimpleFileVisitor, FileVisitResult}
import java.nio.file.attribute

import com.rojoma.simplearm.v2.{managed => mgd}

object ResourceUtil {
  def withCleanup[T, U](f: => T)(cleanup: T => U): Managed[T] =
    mgd(f)(new Resource[T] { override def close(t: T) = cleanup(t) })

  /** Helpers to create temporary files.
    */
  object Temporary {
    sealed abstract class TempPath
    object TempPath {
      implicit def tempPathOfPath(p: Path): TempPath = PathTempPath(p)
      implicit def tempPathOfFile(f: File): TempPath = PathTempPath(f.toPath)
    }
    case object DefaultTempPath extends TempPath
    case class PathTempPath(path: Path) extends TempPath

    sealed abstract class PathLike[T] {
      private[Temporary] val fileResource: Resource[T]
      private[Temporary] val directoryResource: Resource[T]
    }

    object PathLike {
      implicit case object FilePathLike extends PathLike[File] {
        private[Temporary] val fileResource = new Resource[File] {
          private val ppr = PathPathLike.fileResource
          override def close(dir: File) {
            ppr.close(dir.toPath)
          }
        }
        private[Temporary] val directoryResource = new Resource[File] {
          private val ppr = PathPathLike.directoryResource
          override def close(dir: File) {
            ppr.close(dir.toPath)
          }
        }
      }

      implicit case object PathPathLike extends PathLike[Path] {
        private[Temporary] val fileResource = new Resource[Path] {
          override def close(file: Path) =
            Files.deleteIfExists(file)
        }
        private[Temporary] val directoryResource = new Resource[Path] {
          private val visitor = new SimpleFileVisitor[Path] {
            override def visitFile(file: Path, attrs: attribute.BasicFileAttributes) = {
              Files.deleteIfExists(file)
              FileVisitResult.CONTINUE
            }
            override def postVisitDirectory(dir: Path, e: IOException) = {
              if(e != null) throw e
              Files.deleteIfExists(dir)
              FileVisitResult.CONTINUE
            }
          }

          override def close(dir: Path) {
            Files.walkFileTree(dir, visitor)
          }
        }
      }
    }

    object File {
      private val ppr = PathLike.PathPathLike.fileResource

      private def createTempFile(dir: TempPath, prefix: String, suffix: String): File = {
        val path = createTempPath(dir, prefix, suffix)
        try {
          path.toFile
        } catch {
          case e: Throwable =>
            try {
              ppr.close(path)
            } catch {
              case e2: Throwable =>
                if(e ne e2) e.addSuppressed(e2)
            }
            throw e
        }
      }

      private def createTempPath(dir: TempPath, prefix: String, suffix: String): Path =
        dir match {
          case DefaultTempPath =>
            Files.createTempFile(prefix, suffix)
          case PathTempPath(dir) =>
            Files.createTempFile(dir, prefix, suffix)
        }

      def scoped[T](rs: ResourceScope, dir: TempPath = DefaultTempPath, prefix: String = "tmp", suffix: String = ".tmp")(implicit ev: PathLike[T]): T =
        ev match {
          case PathLike.FilePathLike =>
            rs.open(createTempFile(dir, prefix, suffix))(ev.fileResource)
          case PathLike.PathPathLike =>
            rs.open(createTempPath(dir, prefix, suffix))(ev.fileResource)
        }

      def managed[T](dir: TempPath = DefaultTempPath, prefix: String = "tmp", suffix: String = ".tmp")(implicit ev: PathLike[T]): Managed[T] =
        ev match {
          case PathLike.FilePathLike =>
            mgd(createTempFile(dir, prefix, suffix))(ev.fileResource)
          case PathLike.PathPathLike =>
            mgd(createTempPath(dir, prefix, suffix))(ev.fileResource)
        }
    }

    object Directory {
      private val ppr = PathLike.PathPathLike.directoryResource

      private def createTempDirFile(dir: TempPath, prefix: String): File = {
        val path = createTempDirPath(dir, prefix)
        try {
          path.toFile
        } catch {
          case e: Throwable =>
            try {
              ppr.close(path)
            } catch {
              case e2: Throwable =>
                if(e ne e2) e.addSuppressed(e2)
            }
            throw e
        }
      }

      private def createTempDirPath(dir: TempPath, prefix: String): Path =
        dir match {
          case DefaultTempPath =>
            Files.createTempDirectory(prefix)
          case PathTempPath(dir) =>
            Files.createTempDirectory(dir, prefix)
        }


      def scoped[T](rs: ResourceScope, dir: TempPath = DefaultTempPath, prefix: String = "tmp")(implicit ev: PathLike[T]): T =
        ev match {
          case PathLike.FilePathLike =>
            rs.open(createTempDirFile(dir, prefix))(ev.directoryResource)
          case PathLike.PathPathLike =>
            rs.open(createTempDirPath(dir, prefix))(ev.directoryResource)
        }

      def managed[T](dir: TempPath = DefaultTempPath, prefix: String = "tmp")(implicit ev: PathLike[T]): Managed[T] =
        ev match {
          case PathLike.FilePathLike =>
            mgd(createTempDirFile(dir, prefix))(ev.directoryResource)
          case PathLike.PathPathLike =>
            mgd(createTempDirPath(dir, prefix))(ev.directoryResource)
        }
    }
  }

  @deprecated("use Temporary.File instead", since="2.3.0")
  object TempFile {
    def scoped(dir: Path, rs: ResourceScope): Path =
      Temporary.File.scoped[Path](rs, dir = dir)

    def scoped(rs: ResourceScope): Path =
      Temporary.File.scoped[Path](rs)

    def managed(dir: Path): Managed[Path] =
      Temporary.File.managed[Path](dir = dir)

    def managed(): Managed[Path] =
      Temporary.File.managed[Path]()
  }

  @deprecated("use Temporary.Directory instead", since="2.3.0")
  object TempDir {
    def scoped(dir: Path, rs: ResourceScope): Path =
      Temporary.Directory.scoped[Path](rs, dir = dir)

    def scoped(rs: ResourceScope): Path =
      Temporary.Directory.scoped[Path](rs)

    def managed(dir: Path): Managed[Path] =
      Temporary.Directory.managed[Path](dir = dir)

    def managed(): Managed[Path] =
      Temporary.Directory.managed[Path]()
  }
}
