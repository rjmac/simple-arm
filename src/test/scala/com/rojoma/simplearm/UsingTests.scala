package com.rojoma
package simplearm

import org.scalatest.FunSuite
import org.scalatest.matchers.MustMatchers

import util._

import Break._

class UsingTests extends FunSuite with MustMatchers {
  def makeResource() = new TestResource[String]

  test("a resource is acquired and released") {
    implicit val res = makeResource()
    using("hello") { f =>
      res.mark("inner")
    }
    (res.result) must equal (List("opening hello", "inner", "closing hello normally"))
  }

  test("Resources are acquired and released in the right order") {
    implicit val res = makeResource()
    using("hello", "goodbye") { (f, g) =>
      res.mark("inner")
    }
    (res.result) must equal (List("opening hello", "opening goodbye", "inner", "closing goodbye normally", "closing hello normally"))
  }

  test("resources are released abrnomally") {
    implicit val res = makeResource()
    breaking {
      using("hello", "goodbye"){ (f, g) =>
        res.mark("inner")
        break()
      }
    }
    (res.result) must equal (List("opening hello", "opening goodbye", "inner", "closing goodbye due to com.rojoma.simplearm.Break", "closing hello due to com.rojoma.simplearm.Break"))
  }

  test("resources are released abrnomally if a resource-constructor throws") {
    implicit val res = makeResource()
    breaking {
      using("hello", (break(): String)) { (f, g) =>
        res.mark("inner")
      }
    }
    (res.result) must equal (List("opening hello", "closing hello due to com.rojoma.simplearm.Break"))
  }

  test("a resource is not closed if its open fails") {
    implicit val res = new TestResource[String] {
      override def open(x: String) = break()
    }

    breaking {
      using("hello") { f =>
        res.mark("inner")
      }
    }

    (res.result) must equal (List())
  }

  test("previous resources are closed if an open fails") {
    implicit val res = new TestResource[String] {
      override def open(x: String) = if(x == "goodbye") break() else super.open(x)
    }

    breaking {
      using("hello", "goodbye") { (f, g) =>
        res.mark("inner")
      }
    }

    (res.result) must equal (List("opening hello", "closing hello due to com.rojoma.simplearm.Break"))
  }

  test("subsequent resources are not opened if an open fails") {
    implicit val res = new TestResource[String] {
      override def open(x: String) = if(x == "goodbye") break() else super.open(x)
    }

    breaking {
      using("hello", "goodbye", "..what else is there?") { (f, g, h) =>
        res.mark("inner")
      }
    }

    (res.result) must equal (List("opening hello", "closing hello due to com.rojoma.simplearm.Break"))
  }
}
