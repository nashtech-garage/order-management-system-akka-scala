package com.oms.product

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SimpleSpec extends AnyFlatSpec with Matchers {
  "Basic math" should "work correctly" in {
    1 + 1 shouldBe 2
  }

  "BigDecimal comparison" should "work correctly" in {
    BigDecimal("99.99") should be > BigDecimal("10.00")
  }
}
