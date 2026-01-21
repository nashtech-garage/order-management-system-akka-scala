package com.oms.user.actor

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import com.oms.user.model._
import com.oms.user.repository.UserRepository
import org.scalatest.wordspec.AnyWordSpecLike
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.mindrot.jbcrypt.BCrypt

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

class UserActorSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with MockitoSugar with ArgumentMatchersSugar {

  implicit val ec: ExecutionContext = system.executionContext

  "UserActor" when {
    
    "receiving CreateUser command" should {
      "return UserCreated on success" in {
        val mockRepo = mock[UserRepository]
        val now = LocalDateTime.now()
        val user = User(
          id = Some(1L),
          username = "newuser",
          email = "new@example.com",
          passwordHash = "hash",
          role = "USER",
          createdAt = now
        )
        
        when(mockRepo.create(any[User])).thenReturn(Future.successful(user))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        val request = CreateUserRequest("newuser", "new@example.com", "password")
        actor ! UserActor.CreateUser(request, probe.ref)
        
        probe.expectMessageType[UserActor.UserCreated] match {
          case UserActor.UserCreated(response) =>
            response.username shouldBe "newuser"
            response.email shouldBe "new@example.com"
            response.id shouldBe 1L
        }
      }

      "return UserError on failure" in {
        val mockRepo = mock[UserRepository]
        when(mockRepo.create(any[User]))
          .thenReturn(Future.failed(new RuntimeException("DB error")))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        val request = CreateUserRequest("erroruser", "error@example.com", "password")
        actor ! UserActor.CreateUser(request, probe.ref)
        
        probe.expectMessageType[UserActor.UserError] match {
          case UserActor.UserError(message) =>
            message should include("Unable to create user. Please try again later")
        }
      }
    }

    "receiving GetUser command" should {
      "return UserFound when user exists" in {
        val mockRepo = mock[UserRepository]
        val now = LocalDateTime.now()
        val user = User(Some(1L), "founduser", "found@example.com", "hash", "user", "active", None, None, 0, now, now)
        
        when(mockRepo.findById(1L)).thenReturn(Future.successful(Some(user)))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        actor ! UserActor.GetUser(1L, probe.ref)
        
        probe.expectMessageType[UserActor.UserFound] match {
          case UserActor.UserFound(response) =>
            response.id shouldBe 1L
            response.username shouldBe "founduser"
        }
      }

      "return UserError when user not found" in {
        val mockRepo = mock[UserRepository]
        when(mockRepo.findById(999L)).thenReturn(Future.successful(None))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        actor ! UserActor.GetUser(999L, probe.ref)
        
        probe.expectMessageType[UserActor.UserError] match {
          case UserActor.UserError(message) =>
            message should include("not found")
        }
      }
    }

    "receiving GetAllUsers command" should {
      "return UsersFound with list of users" in {
        val mockRepo = mock[UserRepository]
        val now = LocalDateTime.now()
        val users = Seq(
          User(Some(1L), "u1", "u1@e.com", "h", "user", "active", None, None, 0, now, now),
          User(Some(2L), "u2", "u2@e.com", "h", "user", "active", None, None, 0, now, now)
        )
        
        when(mockRepo.findAll(0, 10)).thenReturn(Future.successful(users))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        actor ! UserActor.GetAllUsers(0, 10, probe.ref)
        
        probe.expectMessageType[UserActor.UsersFound] match {
          case UserActor.UsersFound(userList) =>
            userList should have size 2
            userList.head.username shouldBe "u1"
        }
      }
    }

    "receiving UpdateUser command" should {
      "return UserUpdated when update succeeds" in {
        val mockRepo = mock[UserRepository]
        when(mockRepo.update(anyLong, any[Option[String]], any[Option[String]], any[Option[String]], any[Option[String]]))
          .thenReturn(Future.successful(1))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        val request = UpdateUserRequest(Some("up@e.com"), Some("admin"), None, None)
        actor ! UserActor.UpdateUser(1L, request, probe.ref)
        
        probe.expectMessageType[UserActor.UserUpdated] match {
          case UserActor.UserUpdated(message) =>
            message should include("updated successfully")
        }
      }

      "return UserError when user not found" in {
        val mockRepo = mock[UserRepository]
        when(mockRepo.update(anyLong, any[Option[String]], any[Option[String]], any[Option[String]], any[Option[String]]))
          .thenReturn(Future.successful(0))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        val request = UpdateUserRequest(Some("up@e.com"), None, None, None)
        actor ! UserActor.UpdateUser(999L, request, probe.ref)
        
        probe.expectMessageType[UserActor.UserError] match {
          case UserActor.UserError(message) =>
            message should include("not found")
        }
      }
    }

    "receiving DeleteUser command" should {
      "return UserDeleted when delete succeeds" in {
        val mockRepo = mock[UserRepository]
        when(mockRepo.delete(1L)).thenReturn(Future.successful(1))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        actor ! UserActor.DeleteUser(1L, probe.ref)
        
        probe.expectMessageType[UserActor.UserDeleted] match {
          case UserActor.UserDeleted(message) =>
            message should include("deleted successfully")
        }
      }
    }

    "receiving GetAllUsers command" should {
      "return UsersFound with users" in {
        val mockRepo = mock[UserRepository]
        val now = LocalDateTime.now()
        val users = Seq(
          User(Some(1L), "user1", "u1@e.com", "hash", "user", "active", None, None, 0, now, now),
          User(Some(2L), "user2", "u2@e.com", "hash", "admin", "active", None, None, 0, now, now)
        )
        
        when(mockRepo.findAll(0, 20)).thenReturn(Future.successful(users))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        actor ! UserActor.GetAllUsers(0, 20, probe.ref)
        
        probe.expectMessageType[UserActor.UsersFound] match {
          case UserActor.UsersFound(userResponses) =>
            userResponses should have size 2
        }
      }
    }

    "receiving SearchUsers command" should {
      "return UserListFound with filtered users" in {
        val mockRepo = mock[UserRepository]
        val now = LocalDateTime.now()
        val users = Seq(
          User(Some(1L), "admin", "admin@e.com", "hash", "admin", "active", None, None, 0, now, now)
        )
        
        when(mockRepo.search(any[Option[String]], any[Option[String]], any[Option[String]], anyInt, anyInt))
          .thenReturn(Future.successful(users))
        when(mockRepo.countFiltered(any[Option[String]], any[Option[String]], any[Option[String]]))
          .thenReturn(Future.successful(1))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        val request = UserSearchRequest(Some("admin"), Some("admin"), Some("active"), 0, 20)
        actor ! UserActor.SearchUsers(request, probe.ref)
        
        probe.expectMessageType[UserActor.UserListFound] match {
          case UserActor.UserListFound(response) =>
            response.users should have size 1
            response.users.head.role shouldBe "admin"
        }
      }
    }

    "receiving GetUserStats command" should {
      "return UserStats with statistics" in {
        val mockRepo = mock[UserRepository]
        val stats = UserStatsResponse(10, 8, 2)
        
        when(mockRepo.getStats()).thenReturn(Future.successful(stats))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        actor ! UserActor.GetUserStats(probe.ref)
        
        probe.expectMessageType[UserActor.UserStatsFound] match {
          case UserActor.UserStatsFound(response) =>
            response.totalUsers shouldBe 10
            response.activeUsers shouldBe 8
            response.lockedUsers shouldBe 2
        }
      }
    }

    "receiving UpdateAccountStatus command" should {
      "return UserUpdated when status is valid" in {
        val mockRepo = mock[UserRepository]
        when(mockRepo.updateStatus(1L, "active")).thenReturn(Future.successful(1))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        val request = AccountStatusRequest("active", None)
        actor ! UserActor.UpdateAccountStatus(1L, request, probe.ref)
        
        probe.expectMessageType[UserActor.UserUpdated]
      }

      "return UserError when status is invalid" in {
        val mockRepo = mock[UserRepository]
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        val request = AccountStatusRequest("invalid", None)
        actor ! UserActor.UpdateAccountStatus(1L, request, probe.ref)
        
        probe.expectMessageType[UserActor.UserError] match {
          case UserActor.UserError(msg) =>
            msg should include("Invalid status")
        }
      }
    }

    "receiving BulkUserAction command" should {
      "activate multiple users" in {
        val mockRepo = mock[UserRepository]
        when(mockRepo.bulkUpdateStatus(any[Seq[Long]], eqTo("active")))
          .thenReturn(Future.successful(3))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        val request = BulkUserActionRequest(Seq(1L, 2L, 3L), "activate", None)
        actor ! UserActor.BulkUserAction(request, probe.ref)
        
        probe.expectMessageType[UserActor.BulkActionCompleted] match {
          case UserActor.BulkActionCompleted(msg, affected) =>
            affected shouldBe 3
            msg should include("Activated")
        }
      }

      "lock multiple users" in {
        val mockRepo = mock[UserRepository]
        when(mockRepo.bulkUpdateStatus(any[Seq[Long]], eqTo("locked")))
          .thenReturn(Future.successful(2))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        val request = BulkUserActionRequest(Seq(1L, 2L), "lock", None)
        actor ! UserActor.BulkUserAction(request, probe.ref)
        
        probe.expectMessageType[UserActor.BulkActionCompleted] match {
          case UserActor.BulkActionCompleted(msg, affected) =>
            affected shouldBe 2
            msg should include("Locked")
        }
      }

      "delete multiple users" in {
        val mockRepo = mock[UserRepository]
        when(mockRepo.bulkDelete(any[Seq[Long]]))
          .thenReturn(Future.successful(2))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        val request = BulkUserActionRequest(Seq(1L, 2L), "delete", None)
        actor ! UserActor.BulkUserAction(request, probe.ref)
        
        probe.expectMessageType[UserActor.BulkActionCompleted] match {
          case UserActor.BulkActionCompleted(msg, affected) =>
            affected shouldBe 2
            msg should include("Deleted")
        }
      }

      "return UserError when userIds is empty" in {
        val mockRepo = mock[UserRepository]
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        val request = BulkUserActionRequest(Seq.empty, "activate", None)
        actor ! UserActor.BulkUserAction(request, probe.ref)
        
        probe.expectMessageType[UserActor.UserError] match {
          case UserActor.UserError(msg) =>
            msg should include("No user IDs provided")
        }
      }

      "return UserError for unknown action" in {
        val mockRepo = mock[UserRepository]
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        val request = BulkUserActionRequest(Seq(1L), "unknown", None)
        actor ! UserActor.BulkUserAction(request, probe.ref)
        
        probe.expectMessageType[UserActor.UserError] match {
          case UserActor.UserError(msg) =>
            msg should include("Unknown action")
        }
      }
    }

    "receiving Login command" should {
      "return LoginSuccess for valid credentials" in {
        val mockRepo = mock[UserRepository]
        val now = LocalDateTime.now()
        val passwordHash = BCrypt.hashpw("password123", BCrypt.gensalt())
        val user = User(
          Some(1L), "testuser", "test@example.com", passwordHash,
          "user", "active", None, None, 0, now, now
        )
        
        when(mockRepo.findByUsername("testuser")).thenReturn(Future.successful(Some(user)))
        when(mockRepo.recordLogin(1L)).thenReturn(Future.successful(1))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        val request = LoginRequest("testuser", "password123")
        actor ! UserActor.Login(request, probe.ref)
        
        probe.expectMessageType[UserActor.LoginSuccess]
      }

      "return UserError for locked account" in {
        val mockRepo = mock[UserRepository]
        val now = LocalDateTime.now()
        val passwordHash = BCrypt.hashpw("password123", BCrypt.gensalt())
        val user = User(
          Some(1L), "locked", "locked@example.com", passwordHash,
          "user", "locked", None, None, 5, now, now
        )
        
        when(mockRepo.findByUsername("locked")).thenReturn(Future.successful(Some(user)))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        val request = LoginRequest("locked", "password123")
        actor ! UserActor.Login(request, probe.ref)
        
        probe.expectMessageType[UserActor.UserError] match {
          case UserActor.UserError(msg) =>
            msg should include("locked")
        }
      }

      "return UserError for invalid password" in {
        val mockRepo = mock[UserRepository]
        val now = LocalDateTime.now()
        val passwordHash = BCrypt.hashpw("correct", BCrypt.gensalt())
        val user = User(
          Some(1L), "testuser", "test@example.com", passwordHash,
          "user", "active", None, None, 0, now, now
        )
        
        when(mockRepo.findByUsername("testuser")).thenReturn(Future.successful(Some(user)))
        when(mockRepo.updateLoginAttempts(1L, 1)).thenReturn(Future.successful(1))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        val request = LoginRequest("testuser", "wrong")
        actor ! UserActor.Login(request, probe.ref)
        
        probe.expectMessageType[UserActor.UserError] match {
          case UserActor.UserError(msg) =>
            msg should include("Invalid")
        }
      }

      "return UserError for non-existent user" in {
        val mockRepo = mock[UserRepository]
        when(mockRepo.findByUsername("nonexistent")).thenReturn(Future.successful(None))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        val request = LoginRequest("nonexistent", "password")
        actor ! UserActor.Login(request, probe.ref)
        
        probe.expectMessageType[UserActor.UserError]
      }
    }

    "receiving GetCurrentUser command" should {
      "return UserFound for valid JWT user" in {
        val mockRepo = mock[UserRepository]
        val now = LocalDateTime.now()
        val user = User(Some(1L), "current", "current@e.com", "hash", "user", "active", None, None, 0, now, now)
        
        when(mockRepo.findById(1L)).thenReturn(Future.successful(Some(user)))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        actor ! UserActor.GetCurrentUser(1L, probe.ref)
        
        probe.expectMessageType[UserActor.UserFound] match {
          case UserActor.UserFound(response) =>
            response.id shouldBe 1L
            response.username shouldBe "current"
        }
      }
    }

    "receiving UpdateCurrentUser command" should {
      "return UserUpdated when profile update succeeds" in {
        val mockRepo = mock[UserRepository]
        when(mockRepo.updateProfile(anyLong, any[Option[String]], any[Option[String]], any[Option[String]]))
          .thenReturn(Future.successful(1))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        val request = UpdateProfileRequest(Some("newemail@e.com"), None, None)
        actor ! UserActor.UpdateCurrentUser(1L, request, probe.ref)
        
        probe.expectMessageType[UserActor.UserUpdated]
      }
    }

    "receiving ChangePassword command" should {
      "return UserUpdated when password change succeeds" in {
        val mockRepo = mock[UserRepository]
        val now = LocalDateTime.now()
        val currentHash = BCrypt.hashpw("oldpass", BCrypt.gensalt())
        val user = User(Some(1L), "user", "user@e.com", currentHash, "user", "active", None, None, 0, now, now)
        
        when(mockRepo.findById(1L)).thenReturn(Future.successful(Some(user)))
        when(mockRepo.updatePassword(anyLong, any[String]))
          .thenReturn(Future.successful(1))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        val request = ChangePasswordRequest("oldpass", "newpass")
        actor ! UserActor.ChangePassword(1L, request, probe.ref)
        
        probe.expectMessageType[UserActor.UserUpdated]
      }

      "return UserError for incorrect current password" in {
        val mockRepo = mock[UserRepository]
        val now = LocalDateTime.now()
        val currentHash = BCrypt.hashpw("realpass", BCrypt.gensalt())
        val user = User(Some(1L), "user", "user@e.com", currentHash, "user", "active", None, None, 0, now, now)
        
        when(mockRepo.findById(1L)).thenReturn(Future.successful(Some(user)))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        val request = ChangePasswordRequest("wrongpass", "newpass")
        actor ! UserActor.ChangePassword(1L, request, probe.ref)
        
        probe.expectMessageType[UserActor.UserError]
      }
    }

    "receiving DeleteUser command" should {
      "return UserDeleted when deletion succeeds" in {
        val mockRepo = mock[UserRepository]
        when(mockRepo.delete(2L)).thenReturn(Future.successful(1))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        actor ! UserActor.DeleteUser(2L, probe.ref)
        
        probe.expectMessageType[UserActor.UserDeleted]
      }

      "return UserError when user not found" in {
        val mockRepo = mock[UserRepository]
        when(mockRepo.delete(999L)).thenReturn(Future.successful(0))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        actor ! UserActor.DeleteUser(999L, probe.ref)
        
        probe.expectMessageType[UserActor.UserError]
      }
    }

    "receiving GetCurrentUser command" should {
      "return UserError when user not found" in {
        val mockRepo = mock[UserRepository]
        when(mockRepo.findById(999L)).thenReturn(Future.successful(None))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        actor ! UserActor.GetCurrentUser(999L, probe.ref)
        
        probe.expectMessageType[UserActor.UserError]
      }
    }

    "receiving UpdateCurrentUser command" should {
      "return UserError when no fields provided" in {
        val mockRepo = mock[UserRepository]
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        val request = UpdateProfileRequest(None, None, None)
        actor ! UserActor.UpdateCurrentUser(1L, request, probe.ref)
        
        probe.expectMessageType[UserActor.UserError]
      }

      "return UserError when user not found" in {
        val mockRepo = mock[UserRepository]
        when(mockRepo.updateProfile(anyLong, any[Option[String]], any[Option[String]], any[Option[String]]))
          .thenReturn(Future.successful(0))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        val request = UpdateProfileRequest(Some("newemail@e.com"), None, None)
        actor ! UserActor.UpdateCurrentUser(999L, request, probe.ref)
        
        probe.expectMessageType[UserActor.UserError]
      }
    }

    "receiving ChangePassword command" should {
      "return UserError when user not found" in {
        val mockRepo = mock[UserRepository]
        when(mockRepo.findById(999L)).thenReturn(Future.successful(None))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        val request = ChangePasswordRequest("oldpass", "newpass")
        actor ! UserActor.ChangePassword(999L, request, probe.ref)
        
        probe.expectMessageType[UserActor.UserError]
      }

      "return UserError when password update fails" in {
        val mockRepo = mock[UserRepository]
        val now = LocalDateTime.now()
        val currentHash = BCrypt.hashpw("oldpass", BCrypt.gensalt())
        val user = User(Some(1L), "user", "user@e.com", currentHash, "user", "active", None, None, 0, now, now)
        
        when(mockRepo.findById(1L)).thenReturn(Future.successful(Some(user)))
        when(mockRepo.updatePassword(anyLong, any[String]))
          .thenReturn(Future.successful(0))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        val request = ChangePasswordRequest("oldpass", "newpass")
        actor ! UserActor.ChangePassword(1L, request, probe.ref)
        
        probe.expectMessageType[UserActor.UserError]
      }

      "return UserError when database fails during password change" in {
        val mockRepo = mock[UserRepository]
        when(mockRepo.findById(anyLong))
          .thenReturn(Future.failed(new RuntimeException("Database error")))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        val request = ChangePasswordRequest("oldpass", "newpass")
        actor ! UserActor.ChangePassword(1L, request, probe.ref)
        
        probe.expectMessageType[UserActor.UserError]
      }
    }

    "receiving CreateUser command" should {
      "return UserError when database fails" in {
        val mockRepo = mock[UserRepository]
        when(mockRepo.create(any[User]))
          .thenReturn(Future.failed(new RuntimeException("Database error")))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        val request = CreateUserRequest("newuser", "new@e.com", "password", None, None)
        actor ! UserActor.CreateUser(request, probe.ref)
        
        probe.expectMessageType[UserActor.UserError]
      }
    }

    "receiving GetCurrentUser command" should {
      "return UserError when database fails" in {
        val mockRepo = mock[UserRepository]
        when(mockRepo.findById(anyLong))
          .thenReturn(Future.failed(new RuntimeException("Database error")))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        actor ! UserActor.GetCurrentUser(1L, probe.ref)
        
        probe.expectMessageType[UserActor.UserError]
      }
    }

    "receiving UpdateCurrentUser command" should {
      "return UserError when database fails" in {
        val mockRepo = mock[UserRepository]
        when(mockRepo.updateProfile(anyLong, any[Option[String]], any[Option[String]], any[Option[String]]))
          .thenReturn(Future.failed(new RuntimeException("Database error")))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        val request = UpdateProfileRequest(Some("newemail@e.com"), None, None)
        actor ! UserActor.UpdateCurrentUser(1L, request, probe.ref)
        
        probe.expectMessageType[UserActor.UserError]
      }
    }
  }
}