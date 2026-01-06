package com.oms.common

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SimpleSpec extends AnyFlatSpec with Matchers {

  "A simple test" should "verify basic arithmetic" in {
    val sum = 1 + 1
    sum shouldBe 2
  }

  "BigDecimal" should "handle decimal operations correctly" in {
    val price1 = BigDecimal("10.50")
    val price2 = BigDecimal("5.25")
    val total = price1 + price2
    total shouldBe BigDecimal("15.75")
  }
}
