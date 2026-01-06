package com.oms.customer

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SimpleSpec extends AnyFlatSpec with Matchers {
  
  "A simple test" should "pass" in {
    val x = 1 + 1
    x shouldBe 2
  }
  
  it should "also work with strings" in {
    val hello = "Hello"
    hello shouldBe "Hello"
  }
}
