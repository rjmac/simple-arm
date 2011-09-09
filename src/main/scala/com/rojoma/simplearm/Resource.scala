package com.rojoma
package simplearm

// Most of this file is copied verbatim from jsuereth's scala-arm

import java.io.Closeable

trait Resource[A] {
  def open(a: A) {}
  def close(a: A)
  def closeAbnormally(a: A, cause: Throwable) { close(a) }
  def handleSecondaryException(primary: Throwable, secondary: Exception) = { throw secondary }
}

sealed trait LowPriorityResourceImplicits {
  /** Structural type for disposable resources */
  type ReflectiveCloseable = { def close() }

  /**
   * This is the type class implementation for reflectively assuming a class with a close method is
   * a resource.
   */
  implicit def reflectiveCloseableResource[A <: ReflectiveCloseable] = new Resource[A] {
    def close(r: A) = r.close()
    override def toString = "Resource[{ def close() : Unit }]"
  }

  /** Structural type for disposable resources */
  type ReflectiveDisposable = { def dispose() }

  /**
   * This is the type class implementation for reflectively assuming a class with a dispose method is
   * a resource.
   */
  implicit def reflectiveDisposableResource[A <: ReflectiveDisposable] = new Resource[A] {
    def close(r: A) = r.dispose()
    override def toString = "Resource[{ def dispose() : Unit }]"
  }
}

sealed trait MediumPriorityResourceImplicits extends LowPriorityResourceImplicits {
  implicit def closeableResource[A <: Closeable] = new Resource[A] {
    def close(r: A) = r.close()
    override def toString = "Resource[java.io.Closeable]"
  }
  
  //Add All JDBC related handlers.
  implicit def connectionResource[A <: java.sql.Connection] = new Resource[A] {
    def close(r: A) = r.close()
    override def toString = "Resource[java.sql.Connection]"
  }

  // This will work for Statements, PreparedStatements and CallableStatements.
  implicit def statementResource[A <: java.sql.Statement] = new Resource[A] {
    def close(r: A) = r.close()
    override def toString = "Resource[java.sql.Statement]"
  }

  // Also handles RowSet
  implicit def resultSetResource[A <: java.sql.ResultSet] = new Resource[A] {
    def close(r: A) = r.close()
    override def toString = "Resource[java.sql.ResultSet]"
  }

  implicit def pooledConnectionResource[A <: javax.sql.PooledConnection] = new Resource[A] {
    def close(r: A) = r.close()
    override def toString = "Resource[javax.sql.PooledConnection]"
  }
}

/**
 * Companion object to the Resource type trait. This contains all the default implicits in appropriate priority order.
 */
object Resource extends MediumPriorityResourceImplicits
