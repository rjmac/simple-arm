package com.rojoma.simplearm.v2

import scala.collection.mutable.ArrayBuilder
import scala.util.control.ControlThrowable
import java.util.IdentityHashMap
import scala.language.existentials
import scala.annotation.tailrec

object ResourceScope {
  private val ctr = new java.util.concurrent.atomic.AtomicLong

  private class Node[A](val value: A, val res: Resource[A], val transitiveClose: List[Any], var prev: Node[_], var next: Node[_]) {
    def close() = res.close(value)
    def closeAbnormally(t: Throwable) = res.closeAbnormally(value, t)
  }

  implicit object ResourceScopeResource extends Resource[ResourceScope] {
    def close(rs: ResourceScope) = rs.close()
    override def closeAbnormally(rs: ResourceScope, t: Throwable) = rs.closeAbnormally(t)
  }

  class Transferator[A] private[ResourceScope] (self: ResourceScope, value: A) {
    def from(rs: ResourceScope) = rs.transfer(value).to(self)
    def to(rs: ResourceScope) {
      if(self.id < rs.id) {
        self.synchronized {
          rs.synchronized {
            rs.transferFrom(self, value)
          }
        }
      } else {
        rs.synchronized {
          self.synchronized {
            rs.transferFrom(self, value)
          }
        }
      }
    }
  }
}

/** A (managable) object for managing resources with lifetimes that
  * do not nest nicely.
  *
  * {{{
  * using(new ResourceScope) { rs =>
  *   val in = rs.open(new FileInputStream("a"))
  *   val out = rs.open(new FileOutputStream("b"))
  *   rs.close(in) // explicit close of a resource
  *   throw new Exception // resource scope ensures "out" is closed
  * }
  * }}}
  *
  * Resource scopes may themselves be owned by other resource scopes.
  * It is possible to build an object which contains complex
  * sub-resources in this fashion.
  *
  * {{{
  * def allocateComplexThing() =
  *   using(new ResourceScope("temp")) { tmpScope =>
  *     new ComplexThing {
  *       // If ComplexThing's ctor throws, tmpScope will ensure cleanup
  *       val myScope = tmpScope.open(new ResourceScope("complex thing"))
  *       val sub1 = myScope.open(subResourceOne())
  *       val sub2 = myScope.open(subResourceTwo())
  *       // The very last thing the constructor does is unmanage
  *       // the scope and so take ownership.
  *       tmpScope.unmanage(myScope)
  *       def close() { myScope.close() }
  *     }
  *   }
  * }}}
  *
  * All methods on this class are thread-safe up to disposal of the
  * `ResourceScope` itself.
  */
final class ResourceScope(val name: String = "anonymous") {
  import ResourceScope._

  private val id = ctr.getAndIncrement()
  private[this] val managed = new IdentityHashMap[Any, Node[_]]
  private[this] var head: Node[_] = null

  override def toString = s"ResourceScope($name)"

  // The main part of transferring a value from one scope to another;
  // at this point both locks are held.
  private def transferFrom(that: ResourceScope, value: Any) {
    val allNodes = that.findTransitiveCloseNodes(value)
    if(allNodes eq null) throw new IllegalArgumentException("transfer of resource not managed by " + that.name)
    if(that eq this) { return }

    for(node <- allNodes) {
      if(managed.put(node.value, node) ne null) throw new IllegalStateException("Value was managed by both " + this.name + " and " + that.name + "?")
      that.unmanageNode(node)

      if(head eq null) {
        node.next = null
        node.prev = null
        head = node
      } else {
        node.next = head
        head.prev = node
        node.prev = null
        head = node
      }
    }
  }

  // called during close, or during a transfer by the receiving scope;
  // returns nodes in order from deepest dependency to shallowest,
  // ending with the node for the value itself.
  private def findTransitiveCloseNodes(value: Any): Array[Node[_]] = {
    val node = managed.get(value)
    if(node eq null) null
    else if(node.transitiveClose.isEmpty) Array(node)
    else complexFindTransitiveCloseNodes(node)
  }

  private def complexFindTransitiveCloseNodes(node: Node[_]): Array[Node[_]] = {
    val seen = new java.util.HashSet[Node[_]]
    val result = new ArrayBuilder.ofRef[Node[_]]
    def loop(value: Any) {
      val node = managed.get(value)
      if((node ne null) && !seen.contains(node)) step(node)
    }
    def step(node: Node[_]) {
      seen.add(node)
      node.transitiveClose.foreach(loop)
      result += node
    }
    step(node)
    result.result()
  }

  // called during a transfer by the receiving scope after the value
  // has been successfully added to its own managed map
  private def unmanageNode(node: Node[_]) {
    val n = managed.remove(node.value)
    if(n eq null) throw new IllegalArgumentException("unmanage of resource not managed by " + name)
    if(n ne node) throw new IllegalStateException(name + " manages node.value but not via node?")
    if(n.next ne null) n.next.prev = n.prev
    if(n.prev ne null) n.prev.next = n.next
    else head = n.next
  }

  /** Manages a resource.  Once a resource is managed, to close it explicitly
    * use the `close(Any)` method on the `ResourceScope`, not any close on the
    * managed object.
    *
    * Optionally, this function can take a list of values to automatically close
    * when the current resource is closed.  Those values must be resources managed
    * by this scope.  If this value is transferred to another scope, the other
    * resources will be transferred along with it.
    *
    * @throws IllegalArgumentException if the value is already managed by this scope,
    *      or if any value listed in `transitiveClose` is not.
    */
  def open[T](value: => T, transitiveClose: Seq[Any] = Nil)(implicit res: Resource[T]): T = {
    val tc = transitiveClose.toList
    require(tc.forall(managed.containsKey), "dependency not managed by " + name)
    val x = value
    res.openBeforeTry(x)
    try {
      res.openAfterTry(x)
      synchronized {
        if(managed.containsKey(x)) throw new IllegalArgumentException("value already managed by " + name)
        val n = new Node(x, res, tc, null, head)

        managed.put(x, n)
        if(head ne null) head.prev = n
        head = n
      }
      x
    } catch {
      case control: ControlThrowable =>
        res.close(x)
        throw control
      case t: Throwable =>
        try {
          res.closeAbnormally(x, t)
        } catch {
          case control: ControlThrowable =>
            throw control
          case t2: Throwable =>
            t.addSuppressed(t2)
        }
        throw t
    }
  }

  /** Registers an unmanaged value with this scope.  This isn't very
    * useful in itself, but it can take a list of values to
    * transitively close, just like `open`.  This is useful for things
    * like iterator wrappers which are not themselves closeable but
    * which refer to closable things, and which may be transferred to
    * other scopes.
    *
    * @throws IllegalArgumentException if the value is already "managed" by this scope,
    *      or if any value listed in `transitiveClose` is not.
    */
  def openUnmanaged[T](value: => T, transitiveClose: Seq[Any] = Nil) =
    open(value, transitiveClose)(Resource.Noop.asInstanceOf[Resource[T]])

  /** Un-manages a resource.  If this succeeds, the value will no
    * longer be managed at all.
    *
    * @throws IllegalArgumentException if the value is not managed by this scope
    */
  def unmanage(value: Any) = synchronized {
    unmanageNode(managed.get(value))
  }

  /** Transfers a resource's ownership (and that of all its dependencies) between two scopes.
    * {{{
    * rs1.transfer(something).to(rs2)
    * rs2.transfer(something).from(rs1) // equivalent
    * }}}
    * If the resource has dependencies, the order in which they are transferred to the
    * other scope is defined only up to what can be inferred by the graph formed by
    * those dependencies.  That is:
    * {{{
    * val d1 = rs1.open(f())
    * val d2 = rs1.open(f())
    * val r = rs1.open(g(), transitiveClose = List(d1, d2))
    * // rs1.close() would be guaranteed to close [r, d2, d1]
    * rs1.transfer(r).to(rs2)
    * rs2.close() // may close either [r, d2, d1], or [r, d1, d2]
    * }}}
    *
    * @note This function is easy to use incorrectly; it depends on the
    *       declared dependency graph being correct.
    * @throws IllegalArgumentException if the value is not managed by the
    *       source scope
    */
  def transfer[A](value: A) = new Transferator(this, value)

  /** Closes a managed value and removes it from the scope.
    *
    * @throws IllegalArgumentException if the value is not managed by this scope
    */
  private[this] def closeImpl(value: Any, cause: Option[Throwable]) {
    val nodes = synchronized { findTransitiveCloseNodes(value) }
    if(nodes eq null) throw new IllegalArgumentException("close of resource not managed by " + name)

    def next(i: Int): Node[_] = {
      val v = nodes(i).value
      synchronized {
        val n = managed.remove(v)
        if(n eq null) return null
        if(n.next ne null) n.next.prev = n.prev
        if(n.prev ne null) n.prev.next = n.next
        else head = n.next
        n
      }
    }

    def continueClosingAbnormally(lastPlusOne: Int, cause: Throwable) {
      var i = lastPlusOne
      while(i != 0) {
        i -= 1
        val node = next(i)
        if(node ne null) {
          try {
            node.closeAbnormally(cause)
          } catch {
            case control: ControlThrowable =>
              continueClosingAbnormally(i, cause)
              throw control
            case t: Throwable =>
              cause.addSuppressed(t)
          }
        }
      }
    }

    def continueClosing(lastPlusOne: Int) {
      var i = lastPlusOne
      while(i != 0) {
        i -= 1
        val node = next(i)
        if(node ne null) {
          try {
            node.close()
          } catch {
            case control: ControlThrowable =>
              continueClosing(i)
              throw control
            case t: Throwable =>
              continueClosingAbnormally(i, t)
              throw t
          }
        }
      }
    }

    cause match {
      case None => continueClosing(nodes.length)
      case Some(t) => continueClosingAbnormally(nodes.length, t)
    }
  }

  def close(value: Any) {
    closeImpl(value, None)
  }

  private[this] def pop(): Node[_] = synchronized {
    if(head eq null) return null
    val h = head
    head = h.next
    if(head ne null) head.prev = null
    managed.remove(h.value)
  }

  /** Closes all resources contained in this scope, in the reverse order
    * they were added. */
  def close() {
    while(true) {
      val n = pop()
      if(n eq null) { return }
      try {
        n.close()
      } catch {
        case control: ControlThrowable =>
          close()
          throw control
        case e: Throwable =>
          closeAbnormally(e)
          throw e
      }
    }
  }

  private def closeAbnormally(cause: Throwable) {
    while(true) {
      val n = pop()
      if(n eq null) { return }
      try {
        n.closeAbnormally(cause)
      } catch {
        case control: ControlThrowable =>
          closeAbnormally(cause) // finish closing
          throw control
        case t: Throwable =>
          cause.addSuppressed(t)
      }
    }
  }

  def isManaged(x: Any) = synchronized { managed.containsKey(x) }

  /** Utility for creating an owned-but-unmanaged object with an associated managed scope.
    * {{{
    * using(new ResourceScope("scope")) { rs =>
    *   val lines: Iterator[Line] = rs.unmanagedWithAssociatedScope("iterator scope") { itScope =>
    *     val stream = itScope.open(new FileInputStream("/tmp/foo"))
    *     linesOfFile(stream) // if this throws, stream will be closed before unmanagedWithAssociatedScope returns
    *   }
    *   rs.close(lines) // closes the associated FileInputStream
    * }
    * }}}
    */
  def unmanagedWithAssociatedScope[A](associatedScopeName: String)(f: ResourceScope => A): A = {
    val tmpScope = open(new ResourceScope(associatedScopeName))
    try {
      openUnmanaged(f(tmpScope), transitiveClose = List(tmpScope))
    } catch {
      case control: ControlThrowable =>
        close(tmpScope)
        throw control
      case cause: Throwable =>
        try {
          closeImpl(tmpScope, Some(cause))
        } catch {
          case t: Throwable =>
            cause.addSuppressed(t)
        }
        throw cause
    }
  }
}
