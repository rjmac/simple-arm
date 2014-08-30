package com.rojoma.simplearm.v2

import java.lang.AutoCloseable
import scala.concurrent.duration.{Duration, FiniteDuration}
import java.util.concurrent.{ExecutorService, TimeUnit}

trait Resource[A] {
  /** Called after the resource is opened but before the managed value
    * is passed to the code which wants to use it.  If this throws (or
    * otherwise exits abnormally) the resource is NOT closed. */
  def openBeforeTry(a: A) {}
  /** Called after the resource is opened but before the managed value
    * is passed to the code which wants to use it.  If this throws (or
    * otherwise exits abormally) the resource is closed. */
  def openAfterTry(a: A) {}

  /** Closes the resource when the user code exits normally or via
    * `ControlException` (e.g., `return`). */
  def close(a: A)

  /** Closes the resource when the user code exits via any
    * non-`ControlException`. */
  def closeAbnormally(a: A, cause: Throwable) { close(a) }
}

sealed trait LowPriorityImplicits {
  // for legacy classes that have close() but do not implement AutoCloseable
  type ReflectiveCloseable = { def close() }
  implicit def duckCloseResource[A <: ReflectiveCloseable] = new Resource[A] {
    import scala.language.reflectiveCalls
    def close(r: A) = r.close()
    override def toString = "Resource[{ def close() : Unit }]"
  }
}

/** An instance of this must be implicitly visible in order to manage an ExecutorService. */
abstract class ExecutorShutdownTimeout(val duration: Duration) {
  def onTimeout(executorService: ExecutorService)
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
    def close(a: Any) {}
  }

  def executorShutdownTimeout(duration: FiniteDuration)(onTimeout: ExecutorService => Any): ExecutorShutdownTimeout = {
    val ot = onTimeout
    new ExecutorShutdownTimeout(duration) {
      def onTimeout(executorService: ExecutorService) = ot(executorService)
    }
  }

  val executorShutdownNoTimeout: ExecutorShutdownTimeout = new ExecutorShutdownTimeout(Duration.Inf) {
    def onTimeout(executorService: ExecutorService) {}
  }
}
