package com.oms.user.repository

import com.oms.user.model.User
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.time.{Millis, Seconds, Span}
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext.Implicits.global

class UserRepositorySpec extends AnyWordSpec with Matchers with ScalaFutures with BeforeAndAfterAll {

  implicit val defaultPatience: PatienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(100, Millis))

  // Use H2 in-memory database for testing
  val db: Database = Database.forURL(
    url = "jdbc:h2:mem:usertest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
    driver = "org.h2.Driver"
  )
  val repository = new UserRepository(db)

  override def beforeAll(): Unit = {
    repository.createSchema().futureValue
  }

  override def afterAll(): Unit = {
    db.close()
  }

  "UserRepository" when {

    "creating a user" should {
      "successfully insert and return user with id" in {
        val user = User(
          username = "testuser",
          email = s"test.${System.currentTimeMillis()}@example.com",
          passwordHash = "hash123",
          role = "USER"
        )

        val result = repository.create(user).futureValue

        result.id shouldBe defined
        result.username shouldBe "testuser"
        result.email shouldBe user.email
        result.role shouldBe "USER"
      }

      "fail when username is duplicate" in {
        val username = s"duplicateuser${System.currentTimeMillis()}"
        val user1 = User(username = username, email = "email1@example.com", passwordHash = "pass", role = "USER")
        val user2 = User(username = username, email = "email2@example.com", passwordHash = "pass", role = "USER")

        repository.create(user1).futureValue

        whenReady(repository.create(user2).failed) { ex =>
          ex shouldBe a[Exception]
        }
      }
    }

    "finding a user by id" should {
      "return the user when it exists" in {
        val user = User(
          username = "findbyid",
          email = s"findbyid.${System.currentTimeMillis()}@example.com",
          passwordHash = "pass",
          role = "USER"
        )

        val created = repository.create(user).futureValue
        val found = repository.findById(created.id.get).futureValue

        found shouldBe defined
        found.get.username shouldBe "findbyid"
      }

      "return None when user doesn't exist" in {
        val result = repository.findById(99999L).futureValue
        result shouldBe None
      }
    }

    "finding a user by username" should {
      "return the user when username exists" in {
        val username = s"findname${System.currentTimeMillis()}"
        val user = User(
          username = username,
          email = "findname@example.com",
          passwordHash = "pass",
          role = "USER"
        )

        repository.create(user).futureValue
        val found = repository.findByUsername(username).futureValue

        found shouldBe defined
        found.get.username shouldBe username
      }
    }

    "finding a user by email" should {
      "return the user when email exists" in {
        val email = s"findemail.${System.currentTimeMillis()}@example.com"
        val user = User(
          username = s"userforemail${System.currentTimeMillis()}",
          email = email,
          passwordHash = "pass",
          role = "USER"
        )

        repository.create(user).futureValue
        val found = repository.findByEmail(email).futureValue

        found shouldBe defined
        found.get.email shouldBe email
      }
    }

    "finding all users" should {
      "return paginated results" in {
        val results = repository.findAll(0, 10).futureValue
        results.size should be >= 1
      }
    }

    "updating a user" should {
      "successfully update existing user" in {
        val user = User(
          username = "toupdate",
          email = s"toupdate.${System.currentTimeMillis()}@example.com",
          passwordHash = "pass",
          role = "USER"
        )

        val created = repository.create(user).futureValue
        val updated = repository.update(
          created.id.get,
          Some("updated@example.com"),
          Some("ADMIN")
        ).futureValue

        updated shouldBe 1

        val found = repository.findById(created.id.get).futureValue
        found shouldBe defined
        found.get.email shouldBe "updated@example.com"
        found.get.role shouldBe "ADMIN"
      }
    }

    "deleting a user" should {
      "successfully delete existing user" in {
        val user = User(
          username = "todelete",
          email = s"todelete.${System.currentTimeMillis()}@example.com",
          passwordHash = "pass",
          role = "USER"
        )

        val created = repository.create(user).futureValue
        val deleted = repository.delete(created.id.get).futureValue

        deleted shouldBe 1

        val found = repository.findById(created.id.get).futureValue
        found shouldBe None
      }
    }

    "counting users" should {
      "return the total count" in {
        val count = repository.count().futureValue
        count should be >= 0
      }
    }
  }
}
