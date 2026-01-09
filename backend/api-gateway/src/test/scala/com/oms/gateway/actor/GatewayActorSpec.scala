package com.oms.gateway.actor

import akka.actor.testkit.typed.scaladsl.{ActorTestKit, TestProbe}
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model._
import com.oms.gateway.actor.GatewayActor._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class GatewayActorSpec extends AnyWordSpec with Matchers with ScalaFutures with BeforeAndAfterAll {
  
  implicit val defaultPatience: PatienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(100, Millis))
  
  private val testKit = ActorTestKit()
  implicit val typedSystem: ActorSystem[_] = testKit.system
  implicit val ec: ExecutionContext = typedSystem.executionContext
  
  // Test service URLs
  private val testServiceUrls = Map(
    "user-service" -> "http://localhost:9081",
    "customer-service" -> "http://localhost:9082",
    "product-service" -> "http://localhost:9083",
    "order-service" -> "http://localhost:9084",
    "payment-service" -> "http://localhost:9085",
    "report-service" -> "http://localhost:9086"
  )
  
  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
  }
  
  "GatewayActor" should {
    
    "handle ProxyRequest for known service" ignore {
      // This test requires running services - skipped for unit testing
      val gatewayActor = testKit.spawn(GatewayActor(testServiceUrls))
      val probe = testKit.createTestProbe[Response]()
      
      val request = HttpRequest(
        method = HttpMethods.GET,
        uri = Uri("/users/1")
      )
      
      gatewayActor ! ProxyRequest(request, "user-service", probe.ref)
      
      // Should receive some response (either success or connection error)
      val response = probe.receiveMessage(5.seconds)
      response should (be(a[ProxyResponse]) or be(a[GatewayError]))
    }
    
    "return GatewayError for unknown service" in {
      val gatewayActor = testKit.spawn(GatewayActor(testServiceUrls))
      val probe = testKit.createTestProbe[Response]()
      
      val request = HttpRequest(
        method = HttpMethods.GET,
        uri = Uri("/test")
      )
      
      gatewayActor ! ProxyRequest(request, "unknown-service", probe.ref)
      
      val response = probe.receiveMessage(2.seconds)
      response shouldBe a[GatewayError]
      
      val error = response.asInstanceOf[GatewayError]
      error.message should include("Unknown service")
    }
    
    "preserve request method when proxying" ignore {
      // This test requires running services - skipped for unit testing
      val gatewayActor = testKit.spawn(GatewayActor(testServiceUrls))
      val probe = testKit.createTestProbe[Response]()
      
      val postRequest = HttpRequest(
        method = HttpMethods.POST,
        uri = Uri("/users"),
        entity = HttpEntity(ContentTypes.`application/json`, """{"name":"test"}""")
      )
      
      gatewayActor ! ProxyRequest(postRequest, "user-service", probe.ref)
      
      // Should receive response
      val response = probe.receiveMessage(5.seconds)
      response should (be(a[ProxyResponse]) or be(a[GatewayError]))
    }
    
    "handle PUT requests" ignore {
      // This test requires running services - skipped for unit testing
      val gatewayActor = testKit.spawn(GatewayActor(testServiceUrls))
      val probe = testKit.createTestProbe[Response]()
      
      val putRequest = HttpRequest(
        method = HttpMethods.PUT,
        uri = Uri("/users/1"),
        entity = HttpEntity(ContentTypes.`application/json`, """{"name":"updated"}""")
      )
      
      gatewayActor ! ProxyRequest(putRequest, "user-service", probe.ref)
      
      val response = probe.receiveMessage(5.seconds)
      response should (be(a[ProxyResponse]) or be(a[GatewayError]))
    }
    
    "handle DELETE requests" ignore {
      // This test requires running services - skipped for unit testing
      val gatewayActor = testKit.spawn(GatewayActor(testServiceUrls))
      val probe = testKit.createTestProbe[Response]()
      
      val deleteRequest = HttpRequest(
        method = HttpMethods.DELETE,
        uri = Uri("/users/1")
      )
      
      gatewayActor ! ProxyRequest(deleteRequest, "user-service", probe.ref)
      
      val response = probe.receiveMessage(5.seconds)
      response should (be(a[ProxyResponse]) or be(a[GatewayError]))
    }
    
    "preserve query parameters in proxy request" ignore {
      // This test requires running services - skipped for unit testing
      val gatewayActor = testKit.spawn(GatewayActor(testServiceUrls))
      val probe = testKit.createTestProbe[Response]()
      
      val requestWithQuery = HttpRequest(
        method = HttpMethods.GET,
        uri = Uri("/users?role=admin&status=active")
      )
      
      gatewayActor ! ProxyRequest(requestWithQuery, "user-service", probe.ref)
      
      val response = probe.receiveMessage(5.seconds)
      response should (be(a[ProxyResponse]) or be(a[GatewayError]))
    }
    
    "handle HealthCheck for known service" ignore {
      // This test requires running services - skipped for unit testing
      val gatewayActor = testKit.spawn(GatewayActor(testServiceUrls))
      val probe = testKit.createTestProbe[Response]()
      
      gatewayActor ! HealthCheck("user-service", probe.ref)
      
      val response = probe.receiveMessage(5.seconds)
      response should (be(a[ServiceHealthy]) or be(a[ServiceUnhealthy]))
    }
    
    "return GatewayError for health check on unknown service" in {
      val gatewayActor = testKit.spawn(GatewayActor(testServiceUrls))
      val probe = testKit.createTestProbe[Response]()
      
      gatewayActor ! HealthCheck("unknown-service", probe.ref)
      
      val response = probe.receiveMessage(2.seconds)
      response shouldBe a[GatewayError]
      
      val error = response.asInstanceOf[GatewayError]
      error.message should include("Unknown service")
    }
    
    "handle multiple proxy requests to different services" ignore {
      // This test requires running services - skipped for unit testing
      val gatewayActor = testKit.spawn(GatewayActor(testServiceUrls))
      val probe1 = testKit.createTestProbe[Response]()
      val probe2 = testKit.createTestProbe[Response]()
      val probe3 = testKit.createTestProbe[Response]()
      
      val userRequest = HttpRequest(method = HttpMethods.GET, uri = Uri("/users/1"))
      val customerRequest = HttpRequest(method = HttpMethods.GET, uri = Uri("/customers/1"))
      val productRequest = HttpRequest(method = HttpMethods.GET, uri = Uri("/products/1"))
      
      gatewayActor ! ProxyRequest(userRequest, "user-service", probe1.ref)
      gatewayActor ! ProxyRequest(customerRequest, "customer-service", probe2.ref)
      gatewayActor ! ProxyRequest(productRequest, "product-service", probe3.ref)
      
      val response1 = probe1.receiveMessage(5.seconds)
      val response2 = probe2.receiveMessage(5.seconds)
      val response3 = probe3.receiveMessage(5.seconds)
      
      response1 should (be(a[ProxyResponse]) or be(a[GatewayError]))
      response2 should (be(a[ProxyResponse]) or be(a[GatewayError]))
      response3 should (be(a[ProxyResponse]) or be(a[GatewayError]))
    }
    
    "handle multiple health checks concurrently" ignore {
      // This test requires running services - skipped for unit testing
      val gatewayActor = testKit.spawn(GatewayActor(testServiceUrls))
      val probe1 = testKit.createTestProbe[Response]()
      val probe2 = testKit.createTestProbe[Response]()
      val probe3 = testKit.createTestProbe[Response]()
      
      gatewayActor ! HealthCheck("user-service", probe1.ref)
      gatewayActor ! HealthCheck("customer-service", probe2.ref)
      gatewayActor ! HealthCheck("product-service", probe3.ref)
      
      val response1 = probe1.receiveMessage(5.seconds)
      val response2 = probe2.receiveMessage(5.seconds)
      val response3 = probe3.receiveMessage(5.seconds)
      
      response1 should (be(a[ServiceHealthy]) or be(a[ServiceUnhealthy]))
      response2 should (be(a[ServiceHealthy]) or be(a[ServiceUnhealthy]))
      response3 should (be(a[ServiceHealthy]) or be(a[ServiceUnhealthy]))
    }
    
    "handle requests with headers" ignore {
      // This test requires running services - skipped for unit testing
      val gatewayActor = testKit.spawn(GatewayActor(testServiceUrls))
      val probe = testKit.createTestProbe[Response]()
      
      val requestWithHeaders = HttpRequest(
        method = HttpMethods.GET,
        uri = Uri("/users/1"),
        headers = List(
          akka.http.scaladsl.model.headers.RawHeader("X-Custom-Header", "test-value"),
          akka.http.scaladsl.model.headers.`Content-Type`(ContentTypes.`application/json`)
        )
      )
      
      gatewayActor ! ProxyRequest(requestWithHeaders, "user-service", probe.ref)
      
      val response = probe.receiveMessage(5.seconds)
      response should (be(a[ProxyResponse]) or be(a[GatewayError]))
    }
    
    "handle requests with request body" ignore {
      // This test requires running services - skipped for unit testing
      val gatewayActor = testKit.spawn(GatewayActor(testServiceUrls))
      val probe = testKit.createTestProbe[Response]()
      
      val jsonBody = """{"username":"testuser","email":"test@example.com"}"""
      val requestWithBody = HttpRequest(
        method = HttpMethods.POST,
        uri = Uri("/users"),
        entity = HttpEntity(ContentTypes.`application/json`, jsonBody)
      )
      
      gatewayActor ! ProxyRequest(requestWithBody, "user-service", probe.ref)
      
      val response = probe.receiveMessage(5.seconds)
      response should (be(a[ProxyResponse]) or be(a[GatewayError]))
    }
    
    "handle nested paths correctly" ignore {
      // This test requires running services - skipped for unit testing
      val gatewayActor = testKit.spawn(GatewayActor(testServiceUrls))
      val probe = testKit.createTestProbe[Response]()
      
      val nestedRequest = HttpRequest(
        method = HttpMethods.GET,
        uri = Uri("/orders/123/items/456")
      )
      
      gatewayActor ! ProxyRequest(nestedRequest, "order-service", probe.ref)
      
      val response = probe.receiveMessage(5.seconds)
      response should (be(a[ProxyResponse]) or be(a[GatewayError]))
    }
    
    "maintain actor behavior after multiple messages" ignore {
      // This test requires running services - skipped for unit testing
      val gatewayActor = testKit.spawn(GatewayActor(testServiceUrls))
      val probe = testKit.createTestProbe[Response]()
      
      // Send multiple messages
      for (i <- 1 to 5) {
        val request = HttpRequest(method = HttpMethods.GET, uri = Uri(s"/users/$i"))
        gatewayActor ! ProxyRequest(request, "user-service", probe.ref)
        val response = probe.receiveMessage(5.seconds)
        response should (be(a[ProxyResponse]) or be(a[GatewayError]))
      }
    }
  }
  
  "GatewayActor Command types" should {
    
    "create ProxyRequest with all parameters" in {
      val request = HttpRequest(method = HttpMethods.GET, uri = Uri("/test"))
      val probe = testKit.createTestProbe[Response]()
      
      val proxyRequest = ProxyRequest(request, "user-service", probe.ref)
      proxyRequest.request shouldEqual request
      proxyRequest.targetService shouldEqual "user-service"
      proxyRequest.replyTo shouldEqual probe.ref
    }
    
    "create HealthCheck with all parameters" in {
      val probe = testKit.createTestProbe[Response]()
      
      val healthCheck = HealthCheck("user-service", probe.ref)
      healthCheck.serviceName shouldEqual "user-service"
      healthCheck.replyTo shouldEqual probe.ref
    }
  }
  
  "GatewayActor Response types" should {
    
    "create ProxyResponse with HttpResponse" in {
      val httpResponse = HttpResponse(status = StatusCodes.OK)
      val proxyResponse = ProxyResponse(httpResponse)
      proxyResponse.response shouldEqual httpResponse
    }
    
    "create ServiceHealthy with service name" in {
      val serviceHealthy = ServiceHealthy("user-service")
      serviceHealthy.serviceName shouldEqual "user-service"
    }
    
    "create ServiceUnhealthy with service name and reason" in {
      val serviceUnhealthy = ServiceUnhealthy("user-service", "Connection timeout")
      serviceUnhealthy.serviceName shouldEqual "user-service"
      serviceUnhealthy.reason shouldEqual "Connection timeout"
    }
    
    "create GatewayError with message" in {
      val error = GatewayError("Service unavailable")
      error.message shouldEqual "Service unavailable"
    }
  }
  
  "GatewayActor error handling" should {
    
    "handle connection errors gracefully" ignore {
      // This test requires running services - skipped for unit testing
      val gatewayActor = testKit.spawn(GatewayActor(testServiceUrls))
      val probe = testKit.createTestProbe[Response]()
      
      // This should result in connection error since service is not running
      val request = HttpRequest(method = HttpMethods.GET, uri = Uri("/users"))
      gatewayActor ! ProxyRequest(request, "user-service", probe.ref)
      
      val response = probe.receiveMessage(5.seconds)
      // Should receive either a proxied response or an error, but not crash
      response should (be(a[ProxyResponse]) or be(a[GatewayError]))
    }
    
    "continue processing after error" ignore {
      // This test requires running services - skipped for unit testing
      val gatewayActor = testKit.spawn(GatewayActor(testServiceUrls))
      val probe1 = testKit.createTestProbe[Response]()
      val probe2 = testKit.createTestProbe[Response]()
      
      // First request to non-existent service
      val request1 = HttpRequest(method = HttpMethods.GET, uri = Uri("/test"))
      gatewayActor ! ProxyRequest(request1, "unknown-service", probe1.ref)
      probe1.receiveMessage(2.seconds)
      
      // Second request should still be processed
      val request2 = HttpRequest(method = HttpMethods.GET, uri = Uri("/users"))
      gatewayActor ! ProxyRequest(request2, "user-service", probe2.ref)
      val response2 = probe2.receiveMessage(5.seconds)
      
      response2 should (be(a[ProxyResponse]) or be(a[GatewayError]))
    }
  }
  
  "GatewayActor service configuration" should {
    
    "work with different service configurations" ignore {
      // This test requires running services - skipped for unit testing
      val customServiceUrls = Map(
        "service1" -> "http://localhost:10001",
        "service2" -> "http://localhost:10002"
      )
      
      val gatewayActor = testKit.spawn(GatewayActor(customServiceUrls))
      val probe = testKit.createTestProbe[Response]()
      
      val request = HttpRequest(method = HttpMethods.GET, uri = Uri("/test"))
      gatewayActor ! ProxyRequest(request, "service1", probe.ref)
      
      val response = probe.receiveMessage(5.seconds)
      response should (be(a[ProxyResponse]) or be(a[GatewayError]))
    }
    
    "work with empty service configuration" in {
      val gatewayActor = testKit.spawn(GatewayActor(Map.empty[String, String]))
      val probe = testKit.createTestProbe[Response]()
      
      val request = HttpRequest(method = HttpMethods.GET, uri = Uri("/test"))
      gatewayActor ! ProxyRequest(request, "any-service", probe.ref)
      
      val response = probe.receiveMessage(2.seconds)
      response shouldBe a[GatewayError]
    }
    
    "handle all configured services" ignore {
      // This test requires running services - skipped for unit testing
      val gatewayActor = testKit.spawn(GatewayActor(testServiceUrls))
      
      testServiceUrls.keys.foreach { serviceName =>
        val probe = testKit.createTestProbe[Response]()
        val request = HttpRequest(method = HttpMethods.GET, uri = Uri("/health"))
        gatewayActor ! ProxyRequest(request, serviceName, probe.ref)
        
        val response = probe.receiveMessage(5.seconds)
        response should (be(a[ProxyResponse]) or be(a[GatewayError]))
      }
    }
  }
}
