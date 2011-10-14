package com.rojoma.simplearm
package util

object unmanaged {
  def apply[A](a: A): SimpleArm[A] = new SimpleArm[A] {
    def flatMap[B](f: A => B) = f(a)
  }
}
