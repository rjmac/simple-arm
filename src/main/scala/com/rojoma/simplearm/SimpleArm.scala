package com.rojoma
package simplearm

import util._

abstract class SimpleArm[A] { self =>
  def foreach[U](f: A => U): Unit = flatMap(f)
  def map[B](f: A => B): B = flatMap(f)
  def withFilter(f: A => Boolean) = new FilteredArm(this, f)

  def flatMap[B](f: A => B): B

  def andThen[B: Resource](xform: A => B) = new SimpleArm[B] {
    def flatMap[C](f: B => C): C = {
      self.flatMap { a: A =>
        using(xform(a))(f)
      }
    }
  }
}

class UntransformedSimpleArm[A](resource: => A)(implicit val ev: Resource[A]) extends SimpleArm[A] {
  def flatMap[B](f: A => B): B = {
    val res = resource
    ev.open(res)
    val result = try {
      f(res)
    } catch {
      case e: Throwable =>
        try {
          ev.closeAbnormally(res, e)
        } catch {
          case secondaryException: Exception =>
            ev.handleSecondaryException(e, secondaryException)
        }
        throw e
    }
    ev.close(res)
    result
  }
}

class FilteredArm[A](arm: SimpleArm[A], pred: A => Boolean) {
  def foreach[U](f: A => U): Unit = flatMap(f)
  def map[B](f: A => B): Unit = flatMap(f)
  def withFilter(f: A => Boolean): FilteredArm[A] = new FilteredArm(arm, a => pred(a) && f(a))

  def flatMap[B](f: A => B): Unit = arm.flatMap { a =>
    if(pred(a)) f(a)
  }
}
