package com.oms.order.model

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class OrderStatusSpec extends AnyFlatSpec with Matchers {

  "OrderStatus" should "have correct status constants" in {
    OrderStatus.Draft shouldBe "draft"
    OrderStatus.Created shouldBe "created"
    OrderStatus.Paid shouldBe "paid"
    OrderStatus.Shipping shouldBe "shipping"
    OrderStatus.Completed shouldBe "completed"
    OrderStatus.Cancelled shouldBe "cancelled"
  }

  it should "list all valid statuses" in {
    OrderStatus.AllStatuses should contain theSameElementsAs 
      Seq("draft", "created", "paid", "shipping", "completed", "cancelled")
    OrderStatus.AllStatuses should have size 6
  }

  it should "validate draft to created transition" in {
    OrderStatus.isValidTransition("draft", "created") shouldBe true
  }

  it should "validate created to paid transition" in {
    OrderStatus.isValidTransition("created", "paid") shouldBe true
  }

  it should "validate paid to shipping transition" in {
    OrderStatus.isValidTransition("paid", "shipping") shouldBe true
  }

  it should "validate shipping to completed transition" in {
    OrderStatus.isValidTransition("shipping", "completed") shouldBe true
  }

  it should "allow cancellation from draft" in {
    OrderStatus.isValidTransition("draft", "cancelled") shouldBe true
  }

  it should "allow cancellation from created" in {
    OrderStatus.isValidTransition("created", "cancelled") shouldBe true
  }

  it should "allow cancellation from paid" in {
    OrderStatus.isValidTransition("paid", "cancelled") shouldBe true
  }

  it should "allow cancellation from shipping" in {
    OrderStatus.isValidTransition("shipping", "cancelled") shouldBe true
  }

  it should "reject skipping from draft to paid" in {
    OrderStatus.isValidTransition("draft", "paid") shouldBe false
  }

  it should "reject skipping from draft to shipping" in {
    OrderStatus.isValidTransition("draft", "shipping") shouldBe false
  }

  it should "reject skipping from draft to completed" in {
    OrderStatus.isValidTransition("draft", "completed") shouldBe false
  }

  it should "reject skipping from created to shipping" in {
    OrderStatus.isValidTransition("created", "shipping") shouldBe false
  }

  it should "reject skipping from created to completed" in {
    OrderStatus.isValidTransition("created", "completed") shouldBe false
  }

  it should "reject skipping from paid to completed" in {
    OrderStatus.isValidTransition("paid", "completed") shouldBe false
  }

  it should "reject backward transition from created to draft" in {
    OrderStatus.isValidTransition("created", "draft") shouldBe false
  }

  it should "reject backward transition from paid to created" in {
    OrderStatus.isValidTransition("paid", "created") shouldBe false
  }

  it should "reject backward transition from paid to draft" in {
    OrderStatus.isValidTransition("paid", "draft") shouldBe false
  }

  it should "reject backward transition from shipping to paid" in {
    OrderStatus.isValidTransition("shipping", "paid") shouldBe false
  }

  it should "reject backward transition from shipping to created" in {
    OrderStatus.isValidTransition("shipping", "created") shouldBe false
  }

  it should "reject backward transition from completed to shipping" in {
    OrderStatus.isValidTransition("completed", "shipping") shouldBe false
  }

  it should "reject backward transition from completed to paid" in {
    OrderStatus.isValidTransition("completed", "paid") shouldBe false
  }

  it should "reject cancellation from completed status" in {
    OrderStatus.isValidTransition("completed", "cancelled") shouldBe false
  }

  it should "reject same status transition for draft" in {
    OrderStatus.isValidTransition("draft", "draft") shouldBe false
  }

  it should "reject same status transition for created" in {
    OrderStatus.isValidTransition("created", "created") shouldBe false
  }

  it should "reject same status transition for paid" in {
    OrderStatus.isValidTransition("paid", "paid") shouldBe false
  }

  it should "reject same status transition for shipping" in {
    OrderStatus.isValidTransition("shipping", "shipping") shouldBe false
  }

  it should "reject same status transition for completed" in {
    OrderStatus.isValidTransition("completed", "completed") shouldBe false
  }

  it should "reject same status transition for cancelled" in {
    OrderStatus.isValidTransition("cancelled", "cancelled") shouldBe false
  }

  it should "reject transition from cancelled to any status" in {
    OrderStatus.isValidTransition("cancelled", "draft") shouldBe false
    OrderStatus.isValidTransition("cancelled", "created") shouldBe false
    OrderStatus.isValidTransition("cancelled", "paid") shouldBe false
    OrderStatus.isValidTransition("cancelled", "shipping") shouldBe false
    OrderStatus.isValidTransition("cancelled", "completed") shouldBe false
  }
}
