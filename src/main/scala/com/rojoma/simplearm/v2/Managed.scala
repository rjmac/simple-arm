package com.rojoma.simplearm.v2

abstract class Managed[+A] { self =>
  def run[B](f: A => B): B

  /** Alias for "run" to allow Managed to be used in a syntactically
    * lightweight way in for-comprehensions.  Note this does not
    * return `Unit`!  The result of a for-comprehension that does not
    * have a `yield` is the the result of running the computation.
    * With a "yield", the computation is delayed and the result is
    * another `Managed`. */
  final def foreach[B](f: A => B): B = run(f)

  /** Produces a `Managed` representing a resource extracted from this
    * one.  The new resource is NOT MANAGED; only the original
    * resource will be closed.
    */
  final def map[B](mapper: A => B): Managed[B] = new Managed[B] {
    def run[C](f: B => C): C = self.run(f compose mapper)
  }

  /** Produces a `Managed` representing a resource extracted from this
    * one.  The new resource is managed; both the new resource and the
    * original one will be closed, in that order.
    *
    * {{{
    * m.mapManaged(f)
    * }}}
    * is equivalent to
    * {{{
    * m.flatMap { r => managed(f(r)) }
    * }}}
    */
  final def mapManaged[B](mapper: A => B)(implicit ev: Resource[B]): Managed[B] = new Managed[B] {
    def run[C](f: B => C): C = self.run { a => using(mapper(a))(f) }
  }

  final def flatMap[B](flatMapper: A => Managed[B]): Managed[B] = new Managed[B] {
    def run[C](f: B => C) = self.run(flatMapper(_).run(f))
  }
}
