# simple-arm

`simple-arm` is based on the `Managed` typeclass, which opens and
closes resources.  There are three ways to use it: for-comprehensions,
the `using` function, and `ResourceScope`s:

```scala
import com.rojoma.simplearm.util._

val linesCopied = for {
  in <- managed(new java.io.FileReader(inName))
  out <- managed(new java.io.FileWriter(outName))
} {
  copyLines(in, out)
}

val linesCopied = using(new java.io.FileReader(inName), new java.io.FileWriter(outName)) { (in, out) =>
  copyLines(in, out)
}

// return an iterator which is managed by the given ResourceScope.
// Closing the iterator through the scope will also ensure the
// underlying Source is closed.
def fileAsIterator(in: File, rs: ResourceScope): Iterator[String] = {
  val source = rs.open(Source.fromFile(in))
  rs.openUnmanaged(source.getLines(), transitiveClose = List(source))
}
```

The first two are almost completely equivalent; the
`for`-comprehension requires using `managed` but the `using` function
separates the resource from its name.  In a `for`-comprehension,
earlier resources are available at the time later ones are
initialized, of course.

`Managed` is a monad; invoking `flatMap` or `map` on it will produce a
new `Managed`.  To actually cause the effects to occur, `run` or
`foreach` must be used.  This is a change from simple-arm 1, where
`Managed` supported the for-comprehension syntactic sugar without
actually being a monad.  `foreach` and `run` are synonyms; in
particular, `foreach` will return the result of the function it is
passed:

```scala
val x = for(r <- managed(...)) yield 5 // x is Managed[Int]
val y = for(r <- managed(...)) 5 // y is Int
```

## Getting it

SBT:

```scala
libraryDependencies += "com.rojoma" %% "simple-arm-v2" % "2.2.0"
```

While for Maven, the pom snippets are:

```xml
<dependencies>
  <dependency>
    <groupId>com.rojoma</groupId>
    <artifactId>simple-arm-v2_${scala.version}</artifactId>
    <version>2.2.0</version>
  </dependency>
</dependencies>
```

## Details

Resource-management is defined as follows:

1. Create the resouce by evaluating the by-name parameter passed to `managed` or `using`.
2. Open the resource by calling the `openPreTry` method on the
   typeclass instance with it.  By default, this is a no-op.  If the
   open returns normally, the resource is considered to be under
   management and will be closed.
3. Open the resource by calling the `openPostTry` method on the
   typeclass instance.  By default, this is a no-op.
3. Do whatever is required with this resource.
4. If the "whatever" returns normally ("normally" includes via
   `ControlThrowable`), invoke `close` on the typeclass instance with
   it.  Otherwise, invoke `closeAbormally` with both the resource
   object and the exception.  By default, this simply defers to
   `close`.  If `closeAbnormally` throws a non-`ControlThrowable`
   exception, it is added to the original exception's suppressed
   list.

Resources in `using` are acquired in left-to-right order and released
in the opposite order.  `ResourceScope` will, unless resources are
explicitly closed early, also close resources in the opposite order
from which they were added.  Note that transferring resources between
`ResourceScopes` may not preserve the exact order.  Instead, it uses
_some_ order consistent with the DAG produced by resources'
`transitiveClose` parameters.
