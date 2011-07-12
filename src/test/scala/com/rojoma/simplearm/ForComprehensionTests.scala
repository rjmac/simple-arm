package com.rojoma
package simplearm

import org.scalatest.FunSuite
import org.scalatest.matchers.MustMatchers

import util._

import Break._

class ForComprehensionTests extends FunSuite with MustMatchers {
  def makeResource() = new TestResource[String]

  test("A resource is acquired and released") {
    implicit val res = makeResource()
    for(f <- managed("hello")) {
      res.mark("inner")
    }
    (res.result) must equal (List("opening hello", "inner", "closing hello normally"))
  }

  test("Resources are acquired and released in the right order") {
    implicit val res = makeResource()
    for {
      f <- managed("hello")
      g <- managed("goodbye")
    } {
      res.mark("inner")
    }
    (res.result) must equal (List("opening hello", "opening goodbye", "inner", "closing goodbye normally", "closing hello normally"))
  }

  test("resources are released abrnomally") {
    implicit val res = makeResource()
    breaking {
      for {
        f <- managed("hello")
        g <- managed("goodbye")
      } {
        res.mark("inner")
        break()
      }
    }
    (res.result) must equal (List("opening hello", "opening goodbye", "inner", "closing goodbye due to com.rojoma.simplearm.Break", "closing hello due to com.rojoma.simplearm.Break"))
  }

  test("resources are released abrnomally if a resource-constructor throws") {
    implicit val res = makeResource()
    breaking {
      for {
        f <- managed("hello")
        g <- managed(break(): String)
      } {
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
      for {
        f <- managed("hello")
      } {
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
      for {
        f <- managed("hello")
        g <- managed("goodbye")
      } {
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
      for {
        f <- managed("hello")
        g <- managed("goodbye")
        h <- managed("..what else is there?")
      } {
        res.mark("inner")
      }
    }

    (res.result) must equal (List("opening hello", "closing hello due to com.rojoma.simplearm.Break"))
  }

  test("true guards pass") {
    implicit val res = makeResource()
    for {
      f <- managed("hello")
      if f != "gnu"
    } {
      res.mark("inner")
    }
    (res.result) must equal (List("opening hello", "inner", "closing hello normally"))
  }

  test("more resources can be acquired after true guards") {
    implicit val res = makeResource()
    for {
      f <- managed("hello")
      if f != "gnu"
      g <- managed("goodbye")
    } {
      res.mark("inner")
    }
    (res.result) must equal (List("opening hello", "opening goodbye", "inner", "closing goodbye normally", "closing hello normally"))
  }

  test("false guards do not pass") {
    implicit val res = makeResource()
    for {
      f <- managed("hello")
      if f != "hello"
    } {
      res.mark("inner")
    }
    (res.result) must equal (List("opening hello", "closing hello normally"))
  }

  test("more resources are not acquired after false guards") {
    implicit val res = makeResource()
    for {
      f <- managed("hello")
      if f != "hello"
      g <- managed("goodbye")
    } {
      res.mark("inner")
    }
    (res.result) must equal (List("opening hello", "closing hello normally"))
  }

  test("resources are released if a guard throws") {
    implicit val res = makeResource()
    breaking {
      for {
        f <- managed("hello")
        if (break(): Boolean)
      } {
        res.mark("inner")
      }
    }
    (res.result) must equal (List("opening hello", "closing hello due to com.rojoma.simplearm.Break"))
  }

  test("no more resources are acquired if a guard throws") {
    implicit val res = makeResource()
    breaking {
      for {
        f <- managed("hello")
        if (break(): Boolean)
        g <- managed("goodbye")
      } {
        res.mark("inner")
      }
    }
    (res.result) must equal (List("opening hello", "closing hello due to com.rojoma.simplearm.Break"))
  }
}
