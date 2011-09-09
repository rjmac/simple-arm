package com.rojoma
package simplearm

// Most of this file is copied verbatim from jsuereth's scala-arm

import java.io.Closeable

trait Resource[-A] {
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
  implicit val reflectiveCloseableResource = new Resource[ReflectiveCloseable] {
    def close(r: ReflectiveCloseable) = r.close()
    override def toString = "Resource[{ def close() : Unit }]"
  }

  /** Structural type for disposable resources */
  type ReflectiveDisposable = { def dispose() }

  /**
   * This is the type class implementation for reflectively assuming a class with a dispose method is
   * a resource.
   */
  implicit val reflectiveDisposableResource = new Resource[ReflectiveDisposable] {
    def close(r: ReflectiveDisposable) = r.dispose()
    override def toString = "Resource[{ def dispose() : Unit }]"
  }
}

sealed trait MediumPriorityResourceImplicits extends LowPriorityResourceImplicits {
  implicit val closeableResource = new Resource[Closeable] {
    def close(r: Closeable) = r.close()
    override def toString = "Resource[java.io.Closeable]"
  }
  
  //Add All JDBC related handlers.
  implicit val connectionResource = new Resource[java.sql.Connection] {
    def close(r: java.sql.Connection) = r.close()
    override def toString = "Resource[java.sql.Connection]"
  }

  // This will work for Statements, PreparedStatements and CallableStatements.
  implicit val statementResource = new Resource[java.sql.Statement] {
    def close(r: java.sql.Statement) = r.close()
    override def toString = "Resource[java.sql.Statement]"
  }

  // Also handles RowSet
  implicit val resultSetResource = new Resource[java.sql.ResultSet] {
    def close(r: java.sql.ResultSet) = r.close()
    override def toString = "Resource[java.sql.ResultSet]"
  }

  implicit val pooledConnectionResource = new Resource[javax.sql.PooledConnection] {
    def close(r: javax.sql.PooledConnection) = r.close()
    override def toString = "Resource[javax.sql.PooledConnection]"
  }
}

/**
 * Companion object to the Resource type trait. This contains all the default implicits in appropriate priority order.
 */
object Resource extends MediumPriorityResourceImplicits
