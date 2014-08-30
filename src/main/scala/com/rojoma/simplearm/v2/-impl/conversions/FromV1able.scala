package com.rojoma.simplearm.v2
package `-impl`.conversions

import com.rojoma.simplearm.{v2 => sav2}
import com.rojoma.{simplearm => sav1}

class FromV1Managed[A](x: sav1.Managed[A]) {
  def toV2: sav2.Managed[A] = new sav2.Managed[A] {
    override def run[B](f: A => B): B = x.flatMap(f)
  }
}
