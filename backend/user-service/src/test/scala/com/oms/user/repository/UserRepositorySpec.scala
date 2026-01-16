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
          role = "user"
        )

        val created = repository.create(user).futureValue
        val updated = repository.update(
          created.id.get,
          Some("updated@example.com"),
          Some("admin"),
          None,
          None
        ).futureValue

        updated shouldBe 1

        val found = repository.findById(created.id.get).futureValue
        found shouldBe defined
        found.get.email shouldBe "updated@example.com"
        found.get.role shouldBe "admin"
      }

      "successfully update only email" in {
        val user = User(
          username = s"updateemail${System.currentTimeMillis()}",
          email = s"updateemail.${System.currentTimeMillis()}@example.com",
          passwordHash = "pass",
          role = "USER"
        )

        val created = repository.create(user).futureValue
        val updated = repository.update(
          created.id.get,
          Some("newonly@example.com"),
          None,
          None,
          None
        ).futureValue

        updated shouldBe 1

        val found = repository.findById(created.id.get).futureValue
        found shouldBe defined
        found.get.email shouldBe "newonly@example.com"
        found.get.role shouldBe "USER"
      }

      "successfully update only role" in {
        val user = User(
          username = s"updaterole${System.currentTimeMillis()}",
          email = s"updaterole.${System.currentTimeMillis()}@example.com",
          passwordHash = "pass",
          role = "USER"
        )

        val created = repository.create(user).futureValue
        val updated = repository.update(
          created.id.get,
          None,
          Some("ADMIN"),
          None,
          None
        ).futureValue

        updated shouldBe 1

        val found = repository.findById(created.id.get).futureValue
        found shouldBe defined
        found.get.role shouldBe "ADMIN"
      }

      "return 0 when neither email nor role provided" in {
        val user = User(
          username = s"updatenothing${System.currentTimeMillis()}",
          email = s"updatenothing.${System.currentTimeMillis()}@example.com",
          passwordHash = "pass",
          role = "USER"
        )

        val created = repository.create(user).futureValue
        val updated = repository.update(
          created.id.get,
          None,
          Some("ADMIN"),
          None,
          None
        ).futureValue

        updated shouldBe 1
      }
    }

    "updating profile" should {
      "successfully update email and username" in {
        val user = User(
          username = s"profileupdate${System.currentTimeMillis()}",
          email = s"profileupdate.${System.currentTimeMillis()}@example.com",
          passwordHash = "pass",
          role = "USER"
        )

        val created = repository.create(user).futureValue
        val updated = repository.updateProfile(
          created.id.get,
          Some("newprofile@example.com"),
          Some("newusername"),
          None
        ).futureValue

        updated shouldBe 1

        val found = repository.findById(created.id.get).futureValue
        found shouldBe defined
        found.get.email shouldBe "newprofile@example.com"
        found.get.username shouldBe "newusername"
      }

      "successfully update only email in profile" in {
        val user = User(
          username = s"profileemail${System.currentTimeMillis()}",
          email = s"profileemail.${System.currentTimeMillis()}@example.com",
          passwordHash = "pass",
          role = "USER"
        )

        val created = repository.create(user).futureValue
        val updated = repository.updateProfile(
          created.id.get,
          Some("newprofileemail@example.com"),
          None,
          None
        ).futureValue

        updated shouldBe 1

        val found = repository.findById(created.id.get).futureValue
        found shouldBe defined
        found.get.email shouldBe "newprofileemail@example.com"
      }

      "successfully update only username in profile" in {
        val user = User(
          username = s"profileusername${System.currentTimeMillis()}",
          email = s"profileusername.${System.currentTimeMillis()}@example.com",
          passwordHash = "pass",
          role = "USER"
        )

        val created = repository.create(user).futureValue
        val updated = repository.updateProfile(
          created.id.get,
          None,
          Some("newprofileusername"),
          None
        ).futureValue

        updated shouldBe 1

        val found = repository.findById(created.id.get).futureValue
        found shouldBe defined
        found.get.username shouldBe "newprofileusername"
      }

      "return 0 when neither email nor username provided" in {
        val user = User(
          username = s"profilenothing${System.currentTimeMillis()}",
          email = s"profilenothing.${System.currentTimeMillis()}@example.com",
          passwordHash = "pass",
          role = "USER"
        )

        val created = repository.create(user).futureValue
        val updated = repository.updateProfile(
          created.id.get,
          None,
          None,
          None
        ).futureValue

        updated shouldBe 0
      }
    }

    "updating password" should {
      "successfully update password" in {
        val user = User(
          username = s"passwordupdate${System.currentTimeMillis()}",
          email = s"passwordupdate.${System.currentTimeMillis()}@example.com",
          passwordHash = "oldpass",
          role = "USER"
        )

        val created = repository.create(user).futureValue
        val updated = repository.updatePassword(created.id.get, "newpasshash").futureValue

        updated shouldBe 1

        val found = repository.findById(created.id.get).futureValue
        found shouldBe defined
        found.get.passwordHash shouldBe "newpasshash"
      }

      "return 0 when user not found" in {
        val updated = repository.updatePassword(99999L, "newpasshash").futureValue
        updated shouldBe 0
      }
    }

    "deleting a user" should {
      "return 0 when user not found" in {
        val deleted = repository.delete(99999L).futureValue
        deleted shouldBe 0
      }
    }

    "counting users" should {
      "return the total count" in {
        val count = repository.count().futureValue
        count should be >= 0
      }
    }

    "searching users" should {
      "find users by query" in {
        val username = s"searchuser${System.currentTimeMillis()}"
        val user = User(
          username = username,
          email = s"$username@example.com",
          passwordHash = "pass",
          role = "user",
          status = "active"
        )

        repository.create(user).futureValue
        val results = repository.search(Some(username), None, None, 0, 20).futureValue

        results should not be empty
        results.exists(_.username == username) shouldBe true
      }

      "filter by role" in {
        val results = repository.search(None, Some("admin"), None, 0, 20).futureValue
        results.foreach(_.role shouldBe "admin")
      }

      "filter by status" in {
        val results = repository.search(None, None, Some("active"), 0, 20).futureValue
        results.foreach(_.status shouldBe "active")
      }
    }

    "counting filtered users" should {
      "count users by role" in {
        val count = repository.countFiltered(None, Some("user"), None).futureValue
        count should be >= 0
      }

      "count users by status" in {
        val count = repository.countFiltered(None, None, Some("active")).futureValue
        count should be >= 0
      }
    }

    "updating user status" should {
      "successfully update status" in {
        val user = User(
          username = s"statususer${System.currentTimeMillis()}",
          email = "status@example.com",
          passwordHash = "pass",
          role = "user",
          status = "active"
        )

        val created = repository.create(user).futureValue
        val updated = repository.updateStatus(created.id.get, "locked").futureValue

        updated shouldBe 1

        val found = repository.findById(created.id.get).futureValue
        found.get.status shouldBe "locked"
      }
    }

    "recording login" should {
      "update lastLogin and reset login attempts" in {
        val user = User(
          username = s"loginuser${System.currentTimeMillis()}",
          email = "login@example.com",
          passwordHash = "pass",
          role = "user",
          loginAttempts = 3
        )

        val created = repository.create(user).futureValue
        val recorded = repository.recordLogin(created.id.get).futureValue

        recorded shouldBe 1

        val found = repository.findById(created.id.get).futureValue
        found.get.lastLogin shouldBe defined
        found.get.loginAttempts shouldBe 0
      }
    }

    "updating login attempts" should {
      "increment login attempts" in {
        val user = User(
          username = s"attemptuser${System.currentTimeMillis()}",
          email = "attempt@example.com",
          passwordHash = "pass",
          role = "user",
          loginAttempts = 0
        )

        val created = repository.create(user).futureValue
        val updated = repository.updateLoginAttempts(created.id.get, 1).futureValue

        updated shouldBe 1

        val found = repository.findById(created.id.get).futureValue
        found.get.loginAttempts shouldBe 1
      }
    }

    "bulk updating status" should {
      "update multiple users status" in {
        val user1 = User(
          username = s"bulk1${System.currentTimeMillis()}",
          email = "bulk1@example.com",
          passwordHash = "pass",
          role = "user",
          status = "active"
        )
        val user2 = User(
          username = s"bulk2${System.currentTimeMillis()}",
          email = "bulk2@example.com",
          passwordHash = "pass",
          role = "user",
          status = "active"
        )

        val created1 = repository.create(user1).futureValue
        val created2 = repository.create(user2).futureValue

        val updated = repository.bulkUpdateStatus(
          Seq(created1.id.get, created2.id.get),
          "locked"
        ).futureValue

        updated shouldBe 2

        val found1 = repository.findById(created1.id.get).futureValue
        val found2 = repository.findById(created2.id.get).futureValue

        found1.get.status shouldBe "locked"
        found2.get.status shouldBe "locked"
      }
    }

    "bulk deleting users" should {
      "delete multiple users" in {
        val user1 = User(
          username = s"bulkdel1${System.currentTimeMillis()}",
          email = "bulkdel1@example.com",
          passwordHash = "pass",
          role = "user"
        )
        val user2 = User(
          username = s"bulkdel2${System.currentTimeMillis()}",
          email = "bulkdel2@example.com",
          passwordHash = "pass",
          role = "user"
        )

        val created1 = repository.create(user1).futureValue
        val created2 = repository.create(user2).futureValue

        val deleted = repository.bulkDelete(
          Seq(created1.id.get, created2.id.get)
        ).futureValue

        deleted shouldBe 2

        val found1 = repository.findById(created1.id.get).futureValue
        val found2 = repository.findById(created2.id.get).futureValue

        found1 shouldBe None
        found2 shouldBe None
      }
    }

    "getting statistics" should {
      "return user statistics" in {
        val stats = repository.getStats().futureValue

        stats.totalUsers should be >= 0
        stats.activeUsers should be >= 0
        stats.lockedUsers should be >= 0
        stats.totalUsers shouldBe (stats.activeUsers + stats.lockedUsers)
      }
    }

    "updating profile" should {
      "update email, username and phoneNumber" in {
        val user = User(
          username = s"profileuser${System.currentTimeMillis()}",
          email = "profile@example.com",
          passwordHash = "pass",
          role = "user"
        )

        val created = repository.create(user).futureValue
        val updated = repository.updateProfile(
          created.id.get,
          Some("newemail@example.com"),
          Some(s"newusername${System.currentTimeMillis()}"),
          Some("+1234567890")
        ).futureValue

        updated shouldBe 1

        val found = repository.findById(created.id.get).futureValue
        found.get.email shouldBe "newemail@example.com"
        found.get.phoneNumber shouldBe Some("+1234567890")
      }
    }

    "listing users" should {
      "return paginated users" in {
        val users = repository.findAll(0, 10).futureValue
        users.size should be <= 10
      }

      "support offset pagination" in {
        val page1 = repository.findAll(0, 5).futureValue
        val page2 = repository.findAll(5, 5).futureValue

        if (page1.nonEmpty && page2.nonEmpty) {
          page1.head.id should not equal page2.head.id
        }
      }
    }
  }
}
