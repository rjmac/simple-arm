package com.rojoma.simplearm.v2
package `-impl`.conversions

import com.rojoma.simplearm.{v2 => sav2}
import com.rojoma.{simplearm => sav1}

class ToV1Managed[A](x: sav2.Managed[A]) {
  def toV1: sav1.Managed[A] = new sav1.SimpleArm[A] {
    override def flatMap[B](f: A => B): B = x.run(f)
  }
}
