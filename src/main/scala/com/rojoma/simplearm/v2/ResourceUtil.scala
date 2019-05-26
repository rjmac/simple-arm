package com.rojoma.simplearm.v2

import java.io.IOException
import java.nio.file.{Path, Files, SimpleFileVisitor, FileVisitResult}
import java.nio.file.attribute

object ResourceUtil {
  /** Helpers to create temporary files.
    */
  object TempFile {
    private val resource = new Resource[Path] {
      override def close(file: Path) = Files.deleteIfExists(file)
    }

    def scoped(dir: Path, rs: ResourceScope): Path =
      rs.open(Files.createTempFile(dir, "tmp", ".tmp"))(resource)

    def scoped(rs: ResourceScope): Path =
      rs.open(Files.createTempFile("tmp", ".tmp"))(resource)

    def managed(dir: Path): Managed[Path] =
      com.rojoma.simplearm.v2.managed(Files.createTempFile(dir, "tmp", ".tmp"))(resource)

    def managed(): Managed[Path] =
      com.rojoma.simplearm.v2.managed(Files.createTempFile("tmp", ".tmp"))(resource)
  }

  object TempDir {
    private val resource = new Resource[Path] {
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

    def scoped(dir: Path, rs: ResourceScope): Path =
      rs.open(Files.createTempDirectory(dir, "tmp"))(resource)

    def scoped(rs: ResourceScope): Path =
      rs.open(Files.createTempDirectory("tmp"))(resource)

    def managed(dir: Path): Managed[Path] =
      com.rojoma.simplearm.v2.managed(Files.createTempDirectory(dir, "tmp"))(resource)

    def managed(): Managed[Path] =
      com.rojoma.simplearm.v2.managed(Files.createTempDirectory("tmp"))(resource)
  }
}
