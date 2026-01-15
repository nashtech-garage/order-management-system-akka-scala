package com.oms.user.actor

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import com.oms.user.model._
import com.oms.user.repository.UserRepository
import com.oms.common.security.JwtUser
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
            message should include("Failed to create user")
        }
      }
    }

    "receiving GetUser command" should {
      "return UserFound when user exists" in {
        val mockRepo = mock[UserRepository]
        val now = LocalDateTime.now()
        val user = User(Some(1L), "founduser", "found@example.com", "hash", "USER", now)
        
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
          User(Some(1L), "u1", "u1@e.com", "h", "USER", now),
          User(Some(2L), "u2", "u2@e.com", "h", "USER", now)
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
        when(mockRepo.update(anyLong, any[Option[String]], any[Option[String]]))
          .thenReturn(Future.successful(1))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        val request = UpdateUserRequest(Some("up@e.com"), Some("ADMIN"))
        actor ! UserActor.UpdateUser(1L, request, probe.ref)
        
        probe.expectMessageType[UserActor.UserUpdated] match {
          case UserActor.UserUpdated(message) =>
            message should include("updated successfully")
        }
      }

      "return UserError when user not found" in {
        val mockRepo = mock[UserRepository]
        when(mockRepo.update(anyLong, any[Option[String]], any[Option[String]]))
          .thenReturn(Future.successful(0))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        val request = UpdateUserRequest(Some("up@e.com"), None)
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

      "return UserError when user not found" in {
        val mockRepo = mock[UserRepository]
        when(mockRepo.delete(999L)).thenReturn(Future.successful(0))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        actor ! UserActor.DeleteUser(999L, probe.ref)
        
        probe.expectMessageType[UserActor.UserError] match {
          case UserActor.UserError(message) =>
            message should include("not found")
        }
      }

      "return UserError when delete fails" in {
        val mockRepo = mock[UserRepository]
        when(mockRepo.delete(1L)).thenReturn(Future.failed(new RuntimeException("DB error")))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        actor ! UserActor.DeleteUser(1L, probe.ref)
        
        probe.expectMessageType[UserActor.UserError] match {
          case UserActor.UserError(message) =>
            message should include("Failed to delete user")
        }
      }
    }

    "receiving Login command" should {
      "return LoginSuccess when credentials are valid" in {
        val mockRepo = mock[UserRepository]
        val now = LocalDateTime.now()
        val passwordHash = BCrypt.hashpw("password", BCrypt.gensalt())
        val user = User(Some(1L), "loginuser", "login@example.com", passwordHash, "USER", now)
        
        when(mockRepo.findByUsername("loginuser")).thenReturn(Future.successful(Some(user)))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        val request = LoginRequest("loginuser", "password")
        actor ! UserActor.Login(request, probe.ref)
        
        probe.expectMessageType[UserActor.LoginSuccess] match {
          case UserActor.LoginSuccess(userResp, token) =>
            userResp.username shouldBe "loginuser"
            token should not be empty
        }
      }

      "return UserError when password is incorrect" in {
        val mockRepo = mock[UserRepository]
        val now = LocalDateTime.now()
        val passwordHash = BCrypt.hashpw("password", BCrypt.gensalt())
        val user = User(Some(1L), "loginuser", "login@example.com", passwordHash, "USER", now)
        
        when(mockRepo.findByUsername("loginuser")).thenReturn(Future.successful(Some(user)))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        val request = LoginRequest("loginuser", "wrongpassword")
        actor ! UserActor.Login(request, probe.ref)
        
        probe.expectMessageType[UserActor.UserError] match {
          case UserActor.UserError(message) =>
            message should include("Invalid username or password")
        }
      }

      "return UserError when user not found" in {
        val mockRepo = mock[UserRepository]
        when(mockRepo.findByUsername("nonexistent")).thenReturn(Future.successful(None))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        val request = LoginRequest("nonexistent", "password")
        actor ! UserActor.Login(request, probe.ref)
        
        probe.expectMessageType[UserActor.UserError] match {
          case UserActor.UserError(message) =>
            message should include("Invalid username or password")
        }
      }

      "return UserError when repository fails" in {
        val mockRepo = mock[UserRepository]
        when(mockRepo.findByUsername("erroruser")).thenReturn(Future.failed(new RuntimeException("DB error")))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        val request = LoginRequest("erroruser", "password")
        actor ! UserActor.Login(request, probe.ref)
        
        probe.expectMessageType[UserActor.UserError] match {
          case UserActor.UserError(message) =>
            message should include("Login failed")
        }
      }
    }

    "receiving Logout command" should {
      "return LogoutSuccess" in {
        val mockRepo = mock[UserRepository]
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        actor ! UserActor.Logout("some.token", probe.ref)
        
        probe.expectMessageType[UserActor.LogoutSuccess] match {
          case UserActor.LogoutSuccess(message) =>
            message shouldBe "Logout successful"
        }
      }
    }

    "receiving GetCurrentUser command" should {
      "return UserFound when user exists" in {
        val mockRepo = mock[UserRepository]
        val now = LocalDateTime.now()
        val user = User(Some(1L), "currentuser", "current@example.com", "hash", "USER", now)
        
        when(mockRepo.findById(1L)).thenReturn(Future.successful(Some(user)))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        actor ! UserActor.GetCurrentUser(1L, probe.ref)
        
        probe.expectMessageType[UserActor.UserFound] match {
          case UserActor.UserFound(response) =>
            response.username shouldBe "currentuser"
        }
      }

      "return UserError when user not found" in {
        val mockRepo = mock[UserRepository]
        when(mockRepo.findById(999L)).thenReturn(Future.successful(None))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        actor ! UserActor.GetCurrentUser(999L, probe.ref)
        
        probe.expectMessageType[UserActor.UserError] match {
          case UserActor.UserError(message) =>
            message should include("User profile not found")
        }
      }

      "return UserError when repository fails" in {
        val mockRepo = mock[UserRepository]
        when(mockRepo.findById(1L)).thenReturn(Future.failed(new RuntimeException("DB error")))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        actor ! UserActor.GetCurrentUser(1L, probe.ref)
        
        probe.expectMessageType[UserActor.UserError] match {
          case UserActor.UserError(message) =>
            message should include("Failed to get user profile")
        }
      }
    }

    "receiving UpdateCurrentUser command" should {
      "return UserUpdated when update succeeds" in {
        val mockRepo = mock[UserRepository]
        when(mockRepo.updateProfile(anyLong, any[Option[String]], any[Option[String]]))
          .thenReturn(Future.successful(1))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        val request = UpdateProfileRequest(Some("new@email.com"), None)
        actor ! UserActor.UpdateCurrentUser(1L, request, probe.ref)
        
        probe.expectMessageType[UserActor.UserUpdated] match {
          case UserActor.UserUpdated(message) =>
            message should include("Profile updated successfully")
        }
      }

      "return UserError when no fields provided" in {
        val mockRepo = mock[UserRepository]
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        val request = UpdateProfileRequest(None, None)
        actor ! UserActor.UpdateCurrentUser(1L, request, probe.ref)
        
        probe.expectMessageType[UserActor.UserError] match {
          case UserActor.UserError(message) =>
            message should include("At least one field must be provided")
        }
      }

      "return UserError when user not found" in {
        val mockRepo = mock[UserRepository]
        when(mockRepo.updateProfile(anyLong, any[Option[String]], any[Option[String]]))
          .thenReturn(Future.successful(0))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        val request = UpdateProfileRequest(Some("new@email.com"), None)
        actor ! UserActor.UpdateCurrentUser(999L, request, probe.ref)
        
        probe.expectMessageType[UserActor.UserError] match {
          case UserActor.UserError(message) =>
            message should include("User profile not found")
        }
      }

      "return UserError when update fails" in {
        val mockRepo = mock[UserRepository]
        when(mockRepo.updateProfile(anyLong, any[Option[String]], any[Option[String]]))
          .thenReturn(Future.failed(new RuntimeException("DB error")))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        val request = UpdateProfileRequest(Some("new@email.com"), None)
        actor ! UserActor.UpdateCurrentUser(1L, request, probe.ref)
        
        probe.expectMessageType[UserActor.UserError] match {
          case UserActor.UserError(message) =>
            message should include("Failed to update profile")
        }
      }
    }

    "receiving ChangePassword command" should {
      "return UserUpdated when password changed successfully" in {
        val mockRepo = mock[UserRepository]
        val now = LocalDateTime.now()
        val currentPasswordHash = BCrypt.hashpw("currentPassword", BCrypt.gensalt())
        val user = User(Some(1L), "user", "user@example.com", currentPasswordHash, "USER", now)
        
        when(mockRepo.findById(1L)).thenReturn(Future.successful(Some(user)))
        when(mockRepo.updatePassword(anyLong, any[String])).thenReturn(Future.successful(1))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        val request = ChangePasswordRequest("currentPassword", "newPassword")
        actor ! UserActor.ChangePassword(1L, request, probe.ref)
        
        probe.expectMessageType[UserActor.UserUpdated] match {
          case UserActor.UserUpdated(message) =>
            message shouldBe "Password changed successfully"
        }
      }

      "return UserError when current password is incorrect" in {
        val mockRepo = mock[UserRepository]
        val now = LocalDateTime.now()
        val currentPasswordHash = BCrypt.hashpw("currentPassword", BCrypt.gensalt())
        val user = User(Some(1L), "user", "user@example.com", currentPasswordHash, "USER", now)
        
        when(mockRepo.findById(1L)).thenReturn(Future.successful(Some(user)))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        val request = ChangePasswordRequest("wrongPassword", "newPassword")
        actor ! UserActor.ChangePassword(1L, request, probe.ref)
        
        probe.expectMessageType[UserActor.UserError] match {
          case UserActor.UserError(message) =>
            message should include("Current password is incorrect")
        }
      }

      "return UserError when user not found" in {
        val mockRepo = mock[UserRepository]
        when(mockRepo.findById(999L)).thenReturn(Future.successful(None))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        val request = ChangePasswordRequest("currentPassword", "newPassword")
        actor ! UserActor.ChangePassword(999L, request, probe.ref)
        
        probe.expectMessageType[UserActor.UserError] match {
          case UserActor.UserError(message) =>
            message should include("Current password is incorrect")
        }
      }

      "return UserError when password update fails" in {
        val mockRepo = mock[UserRepository]
        val now = LocalDateTime.now()
        val currentPasswordHash = BCrypt.hashpw("currentPassword", BCrypt.gensalt())
        val user = User(Some(1L), "user", "user@example.com", currentPasswordHash, "USER", now)
        
        when(mockRepo.findById(1L)).thenReturn(Future.successful(Some(user)))
        when(mockRepo.updatePassword(anyLong, any[String])).thenReturn(Future.successful(0))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        val request = ChangePasswordRequest("currentPassword", "newPassword")
        actor ! UserActor.ChangePassword(1L, request, probe.ref)
        
        probe.expectMessageType[UserActor.UserError] match {
          case UserActor.UserError(message) =>
            message should include("Failed to change password")
        }
      }

      "return UserError when repository fails" in {
        val mockRepo = mock[UserRepository]
        when(mockRepo.findById(1L)).thenReturn(Future.failed(new RuntimeException("DB error")))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        val request = ChangePasswordRequest("currentPassword", "newPassword")
        actor ! UserActor.ChangePassword(1L, request, probe.ref)
        
        probe.expectMessageType[UserActor.UserError] match {
          case UserActor.UserError(message) =>
            message should include("Failed to change password")
        }
      }
    }

    "receiving GetAllUsers command" should {
      "return UserError when repository fails" in {
        val mockRepo = mock[UserRepository]
        when(mockRepo.findAll(anyInt, anyInt)).thenReturn(Future.failed(new RuntimeException("DB error")))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        actor ! UserActor.GetAllUsers(0, 10, probe.ref)
        
        probe.expectMessageType[UserActor.UserError] match {
          case UserActor.UserError(message) =>
            message should include("Failed to get users")
        }
      }
    }

    "receiving UpdateUser command" should {
      "return UserError when repository fails" in {
        val mockRepo = mock[UserRepository]
        when(mockRepo.update(anyLong, any[Option[String]], any[Option[String]]))
          .thenReturn(Future.failed(new RuntimeException("DB error")))
        
        val actor = spawn(UserActor(mockRepo))
        val probe = createTestProbe[UserActor.Response]()
        
        val request = UpdateUserRequest(Some("new@email.com"), Some("ADMIN"))
        actor ! UserActor.UpdateUser(1L, request, probe.ref)
        
        probe.expectMessageType[UserActor.UserError] match {
          case UserActor.UserError(message) =>
            message should include("Failed to update user")
        }
      }
    }
  }
}
