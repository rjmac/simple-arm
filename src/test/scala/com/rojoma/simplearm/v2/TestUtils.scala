package com.rojoma.simplearm.v2

import scala.collection.mutable.ListBuffer

class TestResource[A] extends Resource[A] {
  private val buffer = new ListBuffer[String]

  def result = buffer.toList

  def mark(s: String) = buffer += s

  override def openBeforeTry(a: A) { mark("opening " + a + " before") }
  override def openAfterTry(a: A) { mark("opening " + a + " after") }
  def close(a: A) { mark("closing " + a  + " normally") }
  override def closeAbnormally(a: A, ex: Throwable) { mark("closing " + a + " due to " + ex.getClass.getName) }
}

class Break extends Exception
object Break {
  def breaking[A](f: => A) {
    try {
      f
      throw new Exception("Did not break")
    } catch {
      case _: Break => {}
    }
  }

  def break() = throw new Break
}

class Marker {
  var marked = false
}

class SomeCloseable(val marker: Marker) extends AutoCloseable {
  var closed = false
  def close() {
    closed = false
  }
}

object SomeCloseableResource { // Note NOT companion to SomeCloseable!
  implicit val someClosableResource = new Resource[SomeCloseable] {
    override def openAfterTry(sc: SomeCloseable) = sc.marker.marked = true
    def close(sc: SomeCloseable) = sc.close()
  }
}
