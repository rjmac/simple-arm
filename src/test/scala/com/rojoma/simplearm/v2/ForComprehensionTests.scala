package com.rojoma.simplearm.v2

import org.scalatest.FunSuite
import org.scalatest.MustMatchers

import Break._
import SomeCloseableResource._

class ForComprehensionTests extends FunSuite with MustMatchers {
  def makeResource() = new TestResource[String]

  test("A resource is acquired and released") {
    implicit val res = makeResource()
    for(f <- managed("hello")) {
      res.mark("inner")
    }
    (res.result) must equal (List("opening hello before", "opening hello after", "inner", "closing hello normally"))
  }

  test("Resources are acquired and released in the right order") {
    implicit val res = makeResource()
    for {
      f <- managed("hello")
      g <- managed("goodbye")
    } {
      res.mark("inner")
    }
    (res.result) must equal (List("opening hello before", "opening hello after", "opening goodbye before", "opening goodbye after", "inner", "closing goodbye normally", "closing hello normally"))
  }

  test(".and's side effects occur in the right order") {
    implicit val res = makeResource()
    for {
      f <- managed("a").and { _ => res.mark("and a") }
      g <- managed("b").and { _ => res.mark("and b") }
    } {
      res.mark("inner")
    }
    (res.result) must equal (List("opening a before", "opening a after", "and a", "opening b before", "opening b after", "and b", "inner", "closing b normally", "closing a normally"))
  }

  test("A resource is closed normally if its .and returns") {
    implicit val res = makeResource()
    def foo() {
      for {
        f <- managed("a").and { _ => return }
      } {
        res.mark("inner")
      }
    }
    foo()
    (res.result) must equal (List("opening a before", "opening a after", "closing a normally"))
  }

  test("A resource is closed abnormally normally if its .and throws") {
    implicit val res = makeResource()
    breaking {
      for {
        f <- managed("a").and { _ => break() }
      } {
        res.mark("inner")
      }
    }
    (res.result) must equal (List("opening a before", "opening a after", "closing a due to com.rojoma.simplearm.v2.Break"))
  }

  test("Non-local return counts as a normal close") {
    implicit val res = makeResource()
    def foo() {
      for {
        f <- managed("hello")
        g <- managed("goodbye")
      } {
        res.mark("inner")
        return
      }
    }
    foo()
    (res.result) must equal (List("opening hello before", "opening hello after", "opening goodbye before", "opening goodbye after", "inner", "closing goodbye normally", "closing hello normally"))
  }

  test("Unmanaged resources do not break the flow") {
    implicit val res = makeResource()
    for {
      f <- managed("hello")
      gnu <- unmanaged("gnu")
      g <- managed("goodbye")
    } {
      gnu must equal ("gnu")
      res.mark("inner")
    }
    (res.result) must equal (List("opening hello before", "opening hello after", "opening goodbye before", "opening goodbye after", "inner", "closing goodbye normally", "closing hello normally"))
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
    (res.result) must equal (List("opening hello before", "opening hello after", "opening goodbye before", "opening goodbye after", "inner", "closing goodbye due to com.rojoma.simplearm.v2.Break", "closing hello due to com.rojoma.simplearm.v2.Break"))
  }

  test("Unmanaged resources do not break the flow of abnormal release") {
    implicit val res = makeResource()
    breaking {
      for {
        f <- managed("hello")
        gnu <- unmanaged("gnu")
        g <- managed("goodbye")
      } {
        gnu must equal ("gnu")
        res.mark("inner")
        break()
      }
    }
    (res.result) must equal (List("opening hello before", "opening hello after", "opening goodbye before", "opening goodbye after", "inner", "closing goodbye due to com.rojoma.simplearm.v2.Break", "closing hello due to com.rojoma.simplearm.v2.Break"))
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
    (res.result) must equal (List("opening hello before", "opening hello after", "closing hello due to com.rojoma.simplearm.v2.Break"))
  }

  test("a resource is not closed if its openBeforeTry fails") {
    implicit val res = new TestResource[String] {
      override def openBeforeTry(x: String) = break()
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

  test("previous resources are closed if an openBeforeTry fails") {
    implicit val res = new TestResource[String] {
      override def openBeforeTry(x: String) = if(x == "goodbye") break() else super.openBeforeTry(x)
    }

    breaking {
      for {
        f <- managed("hello")
        g <- managed("goodbye")
      } {
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
      for {
        f <- managed("hello")
        g <- managed("goodbye")
        h <- managed("..what else is there?")
      } {
        res.mark("inner")
      }
    }

    (res.result) must equal (List("opening hello before", "opening hello after", "closing hello due to com.rojoma.simplearm.v2.Break"))
  }


  test("a resource is closed if its openAfterTry fails") {
    implicit val res = new TestResource[String] {
      override def openAfterTry(x: String) = break()
    }

    breaking {
      for {
        f <- managed("hello")
      } {
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
      for {
        f <- managed("hello")
        g <- managed("goodbye")
      } {
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
      for {
        f <- managed("hello")
        g <- managed("goodbye")
        h <- managed("..what else is there?")
      } {
        res.mark("inner")
      }
    }

    (res.result) must equal (List("opening hello before", "opening hello after", "opening goodbye before", "closing goodbye due to com.rojoma.simplearm.v2.Break", "closing hello due to com.rojoma.simplearm.v2.Break"))
  }

  test("Importing a better resource works") {
    val marker = new Marker
    using(new SomeCloseable(marker)) { _ => () }
    marker.marked must be (true)
  }
}
