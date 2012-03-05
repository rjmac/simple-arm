# simple-arm

`simple-arm` is based on the `Resource` typeclass, which opens and
closes resources.  There are two ways to use it, for-comprehensions
and the `using` function:

```scala
import com.rojoma.simplearm.util._

val linesCopied = for {
  in <- managed(new java.io.Reader(inName))
  out <- managed(new java.io.Writer(outName))
} yield {
  copyLines(in, out)
}

val linesCopied = using(new java.io.Reader(inName), new java.io.Writer(outName)) { (in, out) =>
  copyLines(in, out)
}
```

They are almost completely equivalent; the `for`-comprehension
requires using `managed` but the `using` function separates the
resource from its name.  In a `for`-comprehension, earlier resources
are available at the time later ones are initialized, of course.

## Getting it

There is a maven-ish repository at http://rjmac.github.com/maven/ --
setting up SBT is as simple as

```scala
resolvers += "rojoma.com" at "http://rjmac.github.com/maven/releases/"

libraryDependencies += "com.rojoma" %% "simple-arm" % "1.1.9"
```

While for Maven, the pom snippets are:

```xml
<repositories>
  <repository>
    <id>rojoma.com</id>
    <url>http://rjmac.github.com/maven/releases/</url>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>com.rojoma</groupId>
    <artifactId>simple-arm_${scala.version}</artifactId>
    <version>1.1.9</version>
  </dependency>
</dependencies>
```

## Details

Resource-management is defined as follows:

1. Create the resouce by evaluating the by-name parameter passed to `managed` or `using`.
2. Open the resource by calling the `open` method on the typeclass instance with it.
   By default, this is a no-op.  If the open returns normally, the
   resource is considered to be under management and will be closed.
3. Do whatever is required with this resource.
4. If the "whatever" returns normally, invoke `close` on the typeclass
   instance with it.  Otherwise, invoke `closeAbormally` with both the
   resource object and the exception.  By default, this simply defers
   to `close`.  If `closeAbnormally` throws an `Exception` (i.e., not
   an `Error` or other `Throwable`), `handleSecondaryException` is
   called with both exceptions; by default this simply rethrows the
   secondary exception.

Resources are acquired in left-to-right order and released in the
opposite order.

The `for`-comprehension version also has an `unmanaged` function
available.  Because of the way the library works internally (the
object returned by `managed` is not a proper monad at all), this does
not work:

```scala
for {
  a <- managed(resourceA(...))
  val aPrime = f(a)
  b <- managed(resourceB(...))
} ...
```

Instead, create `aPrime` this way:

```scala
for {
  a <- managed(resourceA(...))
  aPrime <- unmanaged(f(a))
  b <- managed(resourceB(...))
} ...
```
