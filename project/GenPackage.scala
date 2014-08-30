import sbt._

object GenPackage extends (File => Seq[File]) {
  def genUsing(n: Int): String = {
    val typeVars = (0 until n).map(i => ('A' + i).toChar).toIndexedSeq
    val evVars = (1 to n).map("ev" + _).toIndexedSeq
    val paramVars = (1 to n).map("r" + _).toIndexedSeq
    val forcedVars = (1 to n).map("fr" + _).toIndexedSeq
    val resultVars = (1 to n).map("res" + _).toIndexedSeq
    val sb = new StringBuilder

    val formals = paramVars.zip(typeVars).map { case (p,t) => p + ": =>" + t }
    val evs = evVars.zip(typeVars).map { case (e,t) => e + ": Resource[" + t + "]" }

    sb.append("def using[").append(typeVars.mkString(", ")).append(", RR](").append(formals.mkString(", ")).append(")(f: (").append(typeVars.mkString(", ")).append(") => RR)(implicit ").append(evs.mkString(", ")).append("): RR = {\n")

    def loop(i: Int, indent: Int) {
      def idt(f: StringBuilder => StringBuilder) = f(sb.append(" " * indent)).append("\n")
      if(i == n) {
        idt(_.append("f(").append(forcedVars.mkString(", ")).append(")"))
      } else {
        idt(_.append("val ").append(forcedVars(i)).append(" = ").append(paramVars(i)))
        idt(_.append(evVars(i)).append(".openBeforeTry(").append(forcedVars(i)).append(")"))
        idt(_.append("val ").append(resultVars(i)).append(" = try {"))
        idt(_.append("  ").append(evVars(i)).append(".openAfterTry(").append(forcedVars(i)).append(")"))
        loop(i + 1, indent + 2)
        idt(_.append("} catch {"))
        idt(_.append("  case e: Throwable => handleEx(").append(evVars(i)).append(", ").append(forcedVars(i)).append(", e)"))
        idt(_.append("}"))
        idt(_.append(evVars(i)).append(".close(").append(forcedVars(i)).append(")"))
        idt(_.append(resultVars(i)))
      }
    }

    loop(0, 2)

    sb.append("}\n\n")

    sb.toString
  }

  def apply(base: File): Seq[File] = {
    val output = base / "com" / "rojoma" / "simplearm" / "v2"
    output.mkdirs()
    val outfile = output / "package.scala"
    val f = new java.io.FileWriter(outfile)
    try {
      f.write("""package com.rojoma.simplearm

import scala.util.control.ControlThrowable

package object v2 {
def managed[A: Resource](resource: =>A): Managed[A] = new Managed[A] {
  def run[B](f: A => B): B = using(resource)(f)
}

def unmanaged[A](a: A): Managed[A] = new Managed[A] {
  def run[B](f: A => B): B = f(a)
}

private def handleEx[A](resource: Resource[A], value: A, ex: Throwable): Nothing = ex match {
  case control: ControlThrowable =>
    // A non-local return is normal; just close the thing and propagate the
    // control change.  If the close throws, that's great, we'll go there
    // instead.
    resource.close(value)
    throw control
  case e: Throwable =>
    try {
      resource.closeAbnormally(value, e)
    } catch {
      case control: ControlThrowable =>
        // this is kinda icky. What we have here is a non-local return that
        // was used to exit from closeAbnormally.  We'll interpret this as
        // "stop exception propagation and switch to the new control path"
        throw control
      case secondaryException: Throwable =>
        e.addSuppressed(secondaryException)
    }
    throw e
}
//
""")
      for(i <- 1 to 22) f.write(genUsing(i))
      f.write("}\n")
    } finally {
      f.close()
    }
    Seq(outfile)
  }
}
