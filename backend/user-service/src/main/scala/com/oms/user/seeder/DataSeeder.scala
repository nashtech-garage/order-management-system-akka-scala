package com.oms.user.seeder

import com.oms.user.model.User
import com.oms.user.repository.UserRepository
import org.mindrot.jbcrypt.BCrypt
import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class DataSeeder(userRepository: UserRepository)(implicit ec: ExecutionContext) {
  
  def seedData(): Future[Unit] = {
    userRepository.count().flatMap { count =>
      if (count == 0) {
        println(">>> No users found. Seeding initial data...")
        seedUsers()
      } else {
        println(s">>> Database already has $count user(s). Skipping seeding.")
        Future.successful(())
      }
    }
  }
  
  private def seedUsers(): Future[Unit] = {
    val users = Seq(
      User(
        username = "admin",
        email = "admin@oms.com",
        passwordHash = hashPassword("admin123"),
        role = "admin",
        status = "active",
        phoneNumber = Some("+1234567890"),
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
      ),
      User(
        username = "john_doe",
        email = "john.doe@example.com",
        passwordHash = hashPassword("password123"),
        role = "user",
        status = "active",
        phoneNumber = Some("+1234567891"),
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
      ),
      User(
        username = "jane_smith",
        email = "jane.smith@example.com",
        passwordHash = hashPassword("password123"),
        role = "user",
        status = "active",
        phoneNumber = Some("+1234567892"),
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
      ),
      User(
        username = "manager",
        email = "manager@oms.com",
        passwordHash = hashPassword("manager123"),
        role = "admin",
        status = "active",
        phoneNumber = Some("+1234567893"),
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
      ),
      User(
        username = "test_user",
        email = "test@example.com",
        passwordHash = hashPassword("test123"),
        role = "user",
        status = "active",
        phoneNumber = Some("+1234567894"),
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
      ),
      User(
        username = "locked_user",
        email = "locked@example.com",
        passwordHash = hashPassword("password123"),
        role = "user",
        status = "locked",
        loginAttempts = 5,
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
      )
    )
    
    val futures = users.map { user =>
      userRepository.create(user).transform {
        case Success(created) =>
          println(s">>> Seeded user: ${created.username} (${created.email}) with role: ${created.role}")
          Success(())
        case Failure(ex) =>
          println(s">>> Failed to seed user ${user.username}: ${ex.getMessage}")
          Success(()) // Continue even if one fails
      }
    }
    
    Future.sequence(futures).map { _ =>
      println(">>> Data seeding completed!")
    }
  }
  
  private def hashPassword(password: String): String = {
    BCrypt.hashpw(password, BCrypt.gensalt())
  }
}
