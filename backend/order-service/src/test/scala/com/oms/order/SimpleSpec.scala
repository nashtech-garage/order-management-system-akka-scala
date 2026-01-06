package com.oms.order

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SimpleSpec extends AnyFlatSpec with Matchers {
  
  "A simple test" should "pass" in {
    val x = 1 + 1
    x shouldBe 2
  }
  
  it should "also work with BigDecimal" in {
    val price = BigDecimal("10.50") * 2
    price shouldBe BigDecimal("21.00")
  }
}
