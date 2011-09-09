package com.rojoma.simplearm
package util

object unmanaged {
  def apply[T](x: T) = new SimpleArm[T](x)(NoopResource)

  private val NoopResource = new Resource[Any] {
    def close(x: Any) {}
  }
}