package com.gu.invoicing.common

/** A touch of DSL to enable having code and runtime assertions side-by-side f tap { v =>
  * "description $v" assert (predicate(v)) }
  */
object Assert {

  implicit class StringAssert(specification: String) {
    def assert(predicate: Boolean): Unit = Predef.assert(predicate, specification)
  }

}
