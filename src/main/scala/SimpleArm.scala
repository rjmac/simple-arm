package com.rojoma
package simplearm

class SimpleArm[A](resource: => A)(implicit val ev: Resource[A]) {
  def foreach[U](f: A => U): Unit = map(f)
  def map[B](f: A => B): B = withResource(f)
  def flatMap[B](f: A => B): B = withResource(f)

  def withResource[B](f: A => B): B = {
    val res = resource
    ev.open(res)
    val result = try {
      f(res)
    } catch {
      case e: Throwable =>
        ev.closeAbnormally(res, e)
        throw e
    }
    ev.close(res)
    result
  }
}

