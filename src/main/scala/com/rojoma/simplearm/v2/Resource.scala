package com.rojoma.simplearm.v2

import java.lang.AutoCloseable
import java.sql.Connection
import scala.concurrent.duration.{Duration, FiniteDuration}
import java.util.concurrent.{ExecutorService, TimeUnit}

trait Resource[A] {
  /** Called after the resource is opened but before the managed value
    * is passed to the code which wants to use it.  If this throws (or
    * otherwise exits abnormally) the resource is NOT closed. */
  def openBeforeTry(a: A): Unit = {}
  /** Called after the resource is opened but before the managed value
    * is passed to the code which wants to use it.  If this throws (or
    * otherwise exits abormally) the resource is closed. */
  def openAfterTry(a: A): Unit = {}

  /** Closes the resource when the user code exits normally or via
    * `ControlException` (e.g., `return`). */
  def close(a: A): Unit

  /** Closes the resource when the user code exits via any
    * non-`ControlException`. */
  def closeAbnormally(a: A, cause: Throwable): Unit = { close(a) }
}

sealed trait LowPriorityImplicits {
  // for legacy classes that have close() but do not implement AutoCloseable
  type ReflectiveCloseable = { def close(): Unit }

  @deprecated(message="Implement a specific instance of Resource for this type", since="2.3.1") // because of https://github.com/scala/bug/issues/11656
  implicit def duckCloseResource[A <: ReflectiveCloseable] = new Resource[A] {
    import scala.language.reflectiveCalls
    def close(r: A) = r.close()
    override def toString = "Resource[{ def close() : Unit }]"
  }
}

/** An instance of this must be implicitly visible in order to manage an ExecutorService. */
abstract class ExecutorShutdownTimeout(val duration: Duration) {
  def onTimeout(executorService: ExecutorService): Unit
}

sealed trait MediumPriorityImplicits extends LowPriorityImplicits {
  private[this] val autoClosableResourceInstance = new Resource[AutoCloseable] {
    def close(r: AutoCloseable) = r.close()
    override def toString = "Resource[java.lang.AutoCloseable]"
  }

  implicit def autoCloseableResource[A <: AutoCloseable]: Resource[A] = autoClosableResourceInstance.asInstanceOf[Resource[A]]

  implicit def executorServiceResource[A <: ExecutorService](implicit executorShutdownTimeout: ExecutorShutdownTimeout) = new Resource[A] {
    def close(r: A) = {
      r.shutdown()
      if(executorShutdownTimeout.duration.isFinite) {
        if(!r.awaitTermination(executorShutdownTimeout.duration.toMillis, TimeUnit.MILLISECONDS)) {
          executorShutdownTimeout.onTimeout(r)
        }
      } else {
        while(!r.awaitTermination(100000000, TimeUnit.MILLISECONDS)) {}
      }
    }
    override def toString = "Resource[java.util.concurrent.ExecutorService]"
  }
}

object Resource extends MediumPriorityImplicits {
  object Noop extends Resource[Any] {
    def close(a: Any): Unit = {}
  }

  /** A Resource for a JDBC Connection.
    *
    * It is implementation-defined behavior to close a JDBC connection
    * with an open transaction, and at least one database interprets
    * that as "throw an exception if there is a pending transaction".
    * This rolls back any pending transaction before closing the
    * connection.  If the rollback throws, the close will still be
    * attempted, and if the close also throws its exception will be
    * added to the rollback exception's suppressed list.
    */
  implicit object connectionResource extends Resource[Connection] {
    def close(conn: Connection): Unit = {
      try {
        if(!conn.getAutoCommit) conn.rollback()
      } catch {
        case e: Throwable =>
          try {
            conn.close()
          } catch {
            case e2: Throwable =>
              e.addSuppressed(e2)
          }
          throw e
      }
      conn.close()
    }
  }

  def executorShutdownTimeout(duration: FiniteDuration)(onTimeout: ExecutorService => Any): ExecutorShutdownTimeout = {
    val ot = onTimeout
    new ExecutorShutdownTimeout(duration) {
      def onTimeout(executorService: ExecutorService) = ot(executorService)
    }
  }

  val executorShutdownNoTimeout: ExecutorShutdownTimeout = new ExecutorShutdownTimeout(Duration.Inf) {
    def onTimeout(executorService: ExecutorService): Unit = {}
  }
}
