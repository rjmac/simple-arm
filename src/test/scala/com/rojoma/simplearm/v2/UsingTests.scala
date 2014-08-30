package com.rojoma.simplearm.v2

import org.scalatest.FunSuite
import org.scalatest.MustMatchers

import Break._

class UsingTests extends FunSuite with MustMatchers {
  def makeResource() = new TestResource[String]

  test("a resource is acquired and released") {
    implicit val res = makeResource()
    using("hello") { f =>
      res.mark("inner")
    }
    (res.result) must equal (List("opening hello before", "opening hello after", "inner", "closing hello normally"))
  }

  test("Resources are acquired and released in the right order") {
    implicit val res = makeResource()
    using("hello", "goodbye") { (f, g) =>
      res.mark("inner")
    }
    (res.result) must equal (List("opening hello before", "opening hello after", "opening goodbye before", "opening goodbye after", "inner", "closing goodbye normally", "closing hello normally"))
  }

  test("Non-local return counts as a normal close") {
    implicit val res = makeResource()
    def foo() {
      using("hello", "goodbye") { (f, g) =>
        res.mark("inner")
        return
      }
    }
    foo()
    (res.result) must equal (List("opening hello before", "opening hello after", "opening goodbye before", "opening goodbye after", "inner", "closing goodbye normally", "closing hello normally"))
  }

  test("resources are released abrnomally") {
    implicit val res = makeResource()
    breaking {
      using("hello", "goodbye") { (f, g) =>
        res.mark("inner")
        break()
      }
    }
    (res.result) must equal (List("opening hello before", "opening hello after", "opening goodbye before", "opening goodbye after", "inner", "closing goodbye due to com.rojoma.simplearm.v2.Break", "closing hello due to com.rojoma.simplearm.v2.Break"))
  }

  test("resources are released abrnomally if a resource-constructor throws") {
    implicit val res = makeResource()
    breaking {
      using("hello", (break(): String)) { (f, g) =>
        res.mark("inner")
      }
    }
    (res.result) must equal (List("opening hello before", "opening hello after", "closing hello due to com.rojoma.simplearm.v2.Break"))
  }

  test("a resource is not closed if its openBeforeTry fails") {
    implicit val res = new TestResource[String] {
      override def openBeforeTry(x: String) = break()
    }

    breaking {
      using("hello") { f =>
        res.mark("inner")
      }
    }

    (res.result) must equal (List())
  }

  test("previous resources are closed if an openBeforeTry fails") {
    implicit val res = new TestResource[String] {
      override def openBeforeTry(x: String) = if(x == "goodbye") break() else super.openBeforeTry(x)
    }

    breaking {
      using("hello", "goodbye") { (f, g) =>
        res.mark("inner")
      }
    }

    (res.result) must equal (List("opening hello before", "opening hello after", "closing hello due to com.rojoma.simplearm.v2.Break"))
  }

  test("subsequent resources are not opened if an openBeforeTry fails") {
    implicit val res = new TestResource[String] {
      override def openBeforeTry(x: String) = if(x == "goodbye") break() else super.openBeforeTry(x)
    }

    breaking {
      using("hello", "goodbye", "..what else is there?") { (f, g, h) =>
        res.mark("inner")
      }
    }

    (res.result) must equal (List("opening hello before", "opening hello after", "closing hello due to com.rojoma.simplearm.v2.Break"))
  }

  test("a resource not closed if its openAfterTry fails") {
    implicit val res = new TestResource[String] {
      override def openAfterTry(x: String) = break()
    }

    breaking {
      using("hello") { f =>
        res.mark("inner")
      }
    }

    (res.result) must equal (List("opening hello before", "closing hello due to com.rojoma.simplearm.v2.Break"))
  }

  test("previous resources are closed if an openAfterTry fails") {
    implicit val res = new TestResource[String] {
      override def openAfterTry(x: String) = if(x == "goodbye") break() else super.openAfterTry(x)
    }

    breaking {
      using("hello", "goodbye") { (f, g) =>
        res.mark("inner")
      }
    }

    (res.result) must equal (List("opening hello before", "opening hello after", "opening goodbye before", "closing goodbye due to com.rojoma.simplearm.v2.Break", "closing hello due to com.rojoma.simplearm.v2.Break"))
  }

  test("subsequent resources are not opened if an openAfterTry fails") {
    implicit val res = new TestResource[String] {
      override def openAfterTry(x: String) = if(x == "goodbye") break() else super.openAfterTry(x)
    }

    breaking {
      using("hello", "goodbye", "..what else is there?") { (f, g, h) =>
        res.mark("inner")
      }
    }

    (res.result) must equal (List("opening hello before", "opening hello after", "opening goodbye before", "closing goodbye due to com.rojoma.simplearm.v2.Break", "closing hello due to com.rojoma.simplearm.v2.Break"))
  }
}
