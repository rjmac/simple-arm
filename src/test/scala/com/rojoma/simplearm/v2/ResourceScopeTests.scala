package com.rojoma.simplearm.v2

import scala.util.Random
import scala.collection.mutable.HashSet

import org.scalatest.FunSuite
import org.scalatest.MustMatchers
import org.scalatest.prop.PropertyChecks
import org.scalacheck.{Gen, Arbitrary}
import org.scalacheck.rng.Seed

import Break._
import SomeCloseableResource._

class ResoureScopeTests extends FunSuite with MustMatchers with PropertyChecks {
  def makeResource() = new TestResource[String]

  test("A resource is acquired and released") {
    implicit val res = makeResource()
    using(new ResourceScope) { rs =>
      rs.open("hello")
      res.mark("inner")
    }
    (res.result) must equal (List("opening hello before", "opening hello after", "inner", "closing hello normally"))
  }

  test("Resources are acquired and released in the right order") {
    implicit val res = makeResource()
    using(new ResourceScope) { rs =>
      rs.open("hello")
      rs.open("goodbye")
      res.mark("inner")
    }
    (res.result) must equal (List("opening hello before", "opening hello after", "opening goodbye before", "opening goodbye after", "inner", "closing goodbye normally", "closing hello normally"))
  }

  test("Non-local return counts as a normal close") {
    implicit val res = makeResource()
    def foo() {
      using(new ResourceScope) { rs =>
        rs.open("hello")
        rs.open("goodbye")
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
      using(new ResourceScope) { rs =>
        rs.open("hello")
        rs.open("goodbye")
        res.mark("inner")
        break()
      }
    }
    (res.result) must equal (List("opening hello before", "opening hello after", "opening goodbye before", "opening goodbye after", "inner", "closing goodbye due to com.rojoma.simplearm.v2.Break", "closing hello due to com.rojoma.simplearm.v2.Break"))
  }

  test("resources are released abrnomally if a resource-constructor throws") {
    implicit val res = makeResource()
    breaking {
      using(new ResourceScope) { rs =>
        rs.open("hello")
        rs.open(break(): String)
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
      using(new ResourceScope) { rs =>
        rs.open("hello")
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
      using(new ResourceScope) { rs =>
        rs.open("hello")
        rs.open("goodbye")
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
      using(new ResourceScope) { rs =>
        rs.open("hello")
        rs.open("goodbye")
        rs.open("..what else is there?")
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
      using(new ResourceScope) { rs =>
        rs.open("hello")
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
      using(new ResourceScope) { rs =>
        rs.open("hello")
        rs.open("goodbye")
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
      using(new ResourceScope) { rs =>
        rs.open("hello")
        rs.open("goodbye")
        rs.open("..what else is there?")
        res.mark("inner")
      }
    }

    (res.result) must equal (List("opening hello before", "opening hello after", "opening goodbye before", "closing goodbye due to com.rojoma.simplearm.v2.Break", "closing hello due to com.rojoma.simplearm.v2.Break"))
  }

  test("Importing a better resource works") {
    val marker = new Marker
    using(new ResourceScope) { rs =>
      rs.open(new SomeCloseable(marker))
    }
    marker.marked must be (true)
  }

  test("Unmanaging a resource prevents it from being closed") {
    implicit val res = new TestResource[String]

    using(new ResourceScope) { rs =>
      rs.open("hello")
      val unmanageMe = rs.open("goodbye")
      rs.open("something else")
      rs.unmanage(unmanageMe)
    }
    (res.result) must equal (List("opening hello before", "opening hello after", "opening goodbye before", "opening goodbye after", "opening something else before", "opening something else after", "closing something else normally", "closing hello normally"))
  }

  test("Explicitly closing a resource removes it from the open-list") {
    implicit val res = new TestResource[String]

    using(new ResourceScope) { rs =>
      rs.open("hello")
      val closeMe = rs.open("goodbye")
      rs.open("something else")
      rs.close(closeMe)
    }
    (res.result) must equal (List("opening hello before", "opening hello after", "opening goodbye before", "opening goodbye after", "opening something else before", "opening something else after", "closing goodbye normally", "closing something else normally", "closing hello normally"))
  }

  test("Transferring a resource transfers dependent resources too") {
    implicit val res = new TestResource[String]

    using(new ResourceScope) { rs =>
      val a = rs.open("a")
      val b = rs.open("b")
      val c = rs.open("c", transitiveClose=List(a))
      val d = rs.open("d", transitiveClose=List(a))
      val e = rs.open("e", transitiveClose=List(c, d))
      rs.transfer(e).to(new ResourceScope)
    }

    (res.result) must equal (List("opening a before", "opening a after", "opening b before", "opening b after", "opening c before", "opening c after", "opening d before", "opening d after", "opening e before", "opening e after", "closing b normally"))
  }

  test("Resources get closed on scope-close even if one close fails") {
    implicit val res = new TestResource[String]

    breaking {
      using(new ResourceScope) { rs =>
        rs.open("a")
        rs.open(new AutoCloseable { def close() = break() })
        rs.open("b")
      }
    }
    (res.result) must equal (List("opening a before", "opening a after", "opening b before", "opening b after", "closing b normally", "closing a due to com.rojoma.simplearm.v2.Break"))
  }

  test("Resources get closed transitively even if one close fails") {
    implicit val res = new TestResource[String]

    breaking {
      val rs = new ResourceScope
      val a = rs.open("a")
      val brk = rs.open(new AutoCloseable { def close() = break() }, transitiveClose = List(a))
      rs.close(rs.open("b", transitiveClose = List(brk)))
    }
    (res.result) must equal (List("opening a before", "opening a after", "opening b before", "opening b after", "closing b normally", "closing a due to com.rojoma.simplearm.v2.Break"))
  }

  test("Resources get closed on scope-close even if one close does a non-local return") {
    implicit val res = new TestResource[String]

    def foo() {
      val rFoo = { () => return }
      using(new ResourceScope) { rs =>
        rs.open("a")
        rs.open(new AutoCloseable { def close() = rFoo() })
        rs.open("b")
      }
    }
    foo()
    (res.result) must equal (List("opening a before", "opening a after", "opening b before", "opening b after", "closing b normally", "closing a normally"))
  }

  test("Resources get closed transitively even if one close does a non-local return") {
    implicit val res = new TestResource[String]

    def foo() {
      val rFoo = { () => return }
      val rs = new ResourceScope
      val a = rs.open("a")
      val brk = rs.open(new AutoCloseable { def close() = rFoo() }, transitiveClose = List(a))
      rs.close(rs.open("b", transitiveClose = List(brk)))
    }
    foo()
    (res.result) must equal (List("opening a before", "opening a after", "opening b before", "opening b after", "closing b normally", "closing a normally"))
  }

  class CloseableResource(val id: Int, val dependencies: Seq[CloseableResource]) extends AutoCloseable {
    var isClosed = false
    def close() {
      isClosed must be (false)
      dependencies.foreach { x => x.isClosed must be (false) }
      isClosed = true
    }
    def foreach[U](f: CloseableResource => U) {
      val seen = new HashSet[CloseableResource]
      def loop(cr: CloseableResource) {
        if(!seen(cr)) {
          seen.add(cr)
          f(cr)
          cr.dependencies.foreach(loop)
        }
      }
      loop(this)
    }

    override def toString = {
      val sb = new StringBuilder
      foreach { cr =>
        sb.append(cr.id).append(" -> ").append(cr.dependencies.map(_.id).mkString(",")).append('\n')
      }
      sb.toString
    }
  }

  def add(rs: ResourceScope, cr: CloseableResource, rng: Random) {
    val seen = new HashSet[CloseableResource]
    def loop(cr: CloseableResource) {
      if(!seen(cr)) {
        seen.add(cr)
        for(dep <- rng.shuffle(cr.dependencies)) loop(dep)
        rs.open(cr, transitiveClose = cr.dependencies.toList)
      }
    }
    loop(cr)
  }

  private def crGen(nodeCount: Int, seed: Long): Gen[CloseableResource] = {
    val nodes = new HashSet[CloseableResource]
    var rng = Seed(seed)
    def nextLong() = { var (l, s) = rng.long; rng = s; l }
    def nonterminalGen(p: Gen.Parameters): Gen[CloseableResource] = {
      for(i <- 0 until nodeCount; nextDeps <- Gen.someOf(nodes).apply(p, Seed(nextLong())))
	nodes += new CloseableResource(i, nextDeps)
      for(nextDeps <- Gen.someOf(nodes)) yield
	new CloseableResource(nodeCount, nextDeps)
    }
    Gen.parameterized(nonterminalGen)
  }

  private implicit val arbCR: Arbitrary[CloseableResource] = Arbitrary {
      for {
        s <- Arbitrary.arbitrary[Long]
        v <- Gen.sized(crGen(_, s))
      } yield v
    }

  test("Resources are released in some valid order after a transfer") {
    forAll { (seed: Long, cr: CloseableResource) =>
      val rng = new scala.util.Random(seed)
      val rs1 = new ResourceScope("source")
      add(rs1, cr, rng)
      val rs2 = new ResourceScope("dest")
      rs1.transfer(cr).to(rs2)
      rs2.close(cr)
    }
  }

  test("Transferred resources get closed when the target resource scope is closed") {
    forAll { (seed: Long, cr: CloseableResource) =>
      val rng = new scala.util.Random(seed)
      val rs1 = new ResourceScope("source")
      add(rs1, cr, rng)
      val rs2 = new ResourceScope("dest")
      rs1.transfer(cr).to(rs2)
      rs2.close(cr)
      cr.foreach { cr => cr.isClosed must be (true) }
    }
  }

  test("Resources are no longer managed by the original scope after a transfer") {
    forAll { (seed: Long, cr: CloseableResource) =>
      val rng = new scala.util.Random(seed)
      val rs1 = new ResourceScope("source")
      add(rs1, cr, rng)
      val rs2 = new ResourceScope("dest")
      rs1.transfer(cr).to(rs2)
      cr.foreach { cr => rs1.isManaged(cr) must be (false) }
      cr.foreach { cr => rs2.isManaged(cr) must be (true) }
    }
  }

  test("Associated resources are closed when their parent unmanaged value is closed explicitly") {
    implicit val res = makeResource()
    using(new ResourceScope("test")) { rs =>
      val result = rs.unmanagedWithAssociatedScope("inner") { rsi =>
          rsi.open("inner")
          new Object
        }
      (res.result) must equal (List("opening inner before", "opening inner after"))
      rs.close(result)
      (res.result) must equal (List("opening inner before", "opening inner after", "closing inner normally"))
    }
  }

  test("Associated resources are closed when their parent unmanaged value is closed implicitly") {
    implicit val res = makeResource()
    using(new ResourceScope("test")) { rs =>
      val result = rs.unmanagedWithAssociatedScope("inner") { rsi =>
          rsi.open("inner")
          new Object
        }
      (res.result) must equal (List("opening inner before", "opening inner after"))
    }
    (res.result) must equal (List("opening inner before", "opening inner after", "closing inner normally"))
  }

  test("Associated resources are closed promptly when unmanagedWithAssociatedResourceScope exits abnormally") {
    implicit val res = makeResource()
    using(new ResourceScope("test")) { rs =>
      breaking {
        rs.unmanagedWithAssociatedScope("inner") { rsi =>
          rsi.open("inner")
          break()
        }
      }
      (res.result) must equal (List("opening inner before", "opening inner after", "closing inner due to com.rojoma.simplearm.v2.Break"))
    }
  }

  test("Associated resources are closed promptly when unmanagedWithAssociatedResourceScope exits early") {
    implicit val res = makeResource()
    using(new ResourceScope("test")) { rs =>
      def foo() {
        rs.unmanagedWithAssociatedScope("inner") { rsi =>
          rsi.open("inner")
          return
        }
      }
      foo()
      (res.result) must equal (List("opening inner before", "opening inner after", "closing inner normally"))
    }
  }
}
