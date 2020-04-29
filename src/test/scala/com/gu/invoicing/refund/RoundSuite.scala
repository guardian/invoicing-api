package com.gu.invoicing.refund
import Impl._

class RoundSuite extends munit.FunSuite {
  test("Zuora uses Half Up rounding to two decimal places with rounding increment of 0.1") {
    assertEquals(roundHalfUp(BigDecimal(454.5454545)), BigDecimal(454.55))
    assertEquals(roundHalfUp(BigDecimal(454.5444545)), BigDecimal(454.54))
  }
}
