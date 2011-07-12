package com.rojoma
package simplearm

import scala.collection.mutable.ListBuffer

class TestResource[A] extends Resource[A] {
  private val buffer = new ListBuffer[String]

  def result = buffer.toList

  def mark(s: String) = buffer += s

  override def open(a: A) { mark("opening " + a) }
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
