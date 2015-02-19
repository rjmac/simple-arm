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

  /** Produces a `Managed` representing a value extracted from this one.
    * The new value DOES NOT HAVE AN ASSOCIATED `Resource` so it will
    * not be separately closed!  Use `mapManaged` if the extracted
    * resource should be independently managed.
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

  /** Applies a side-effect to the managed resource when it is run.
    * The intent is to be used for things that require a `start` or
    * other such activation call before they are fully usable.
    * {{{
    * for {
    *   x <- managed(new A).and(_.start())
    *   y <- managed(new B(x)) // by the time "new B" is called, x will have been started
    * } doSomethingWith(x, y)
    * }}}
    */
  final def and[B](op: A => B): Managed[A] = map { a => op(a); a }

  final def flatMap[B](flatMapper: A => Managed[B]): Managed[B] = new Managed[B] {
    def run[C](f: B => C) = self.run(flatMapper(_).run(f))
  }
}
