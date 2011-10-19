package com.rojoma

package object simplearm {
  type Managed[+A] = SimpleArm[_ <: A]
}
