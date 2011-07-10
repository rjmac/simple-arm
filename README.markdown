simple-arm
==========

`simple-arm` is based on the `Resource` typeclass, which opens and
closes resources.  There are two ways to use it, for-comprehensions
and the `using` function:

```scala
val linesCopied = for {
  in <- managed(new java.io.Reader(inName))
  out <- managed(new java.io.Reader(outName))
} yield {
  copyLines(in, out)
}

val lines = using(new java.io.Reader(inName), new java.io.Writer(outName)) { (in, out) =>
  copyLines(in,out)
}
```

They are completely equivalent; the for-comprehension requires using
`managed` but the `using` function separates the resource from its
name.

Resource-management is defined as follows:

1. Create the resouce by evaluating the by-name parameter passed to `managed` or `using`.
2. Open the resource by calling the `open` method on the typeclass instance with it.
   By default, this is a no-op.  If the open returns normally, the
   resource is considered to be under management and will be closed.
3. Do whatever is required with this resource.
4. If the "whatever" returns normally, invoke `close` on the typeclass
   instance with it.  Otherwise, invoke `closeAbormally` with both the
   resource object and the exception.  By default, this simply defers
   to `close`.

Resources are acquired in left-to-right order and released in the
opposite order.
