package com.rojoma.simplearm.v2

import scala.language.implicitConversions

import com.rojoma.simplearm.{v2 => sav2}
import com.rojoma.{simplearm => sav1}
import `-impl`.conversions._

package object conversions {
  implicit def toManagedConversion[T](x: sav2.Managed[T]) = new ToV1Managed(x)
  implicit def fromManagedConversion[T](x: sav1.Managed[T]) = new FromV1Managed(x)
}
