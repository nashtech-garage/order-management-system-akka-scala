package com.oms.payment

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SimpleSpec extends AnyFlatSpec with Matchers {
  "A simple test" should "pass" in {
    1 + 1 shouldBe 2
  }

  it should "also work with BigDecimal" in {
    BigDecimal("100.50") + BigDecimal("50.25") shouldBe BigDecimal("150.75")
  }
}
