package com.oms.user.repository

import com.oms.user.model.User
import slick.jdbc.PostgresProfile.api._
import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

class UserRepository(db: Database)(implicit ec: ExecutionContext) {
  
  private class UsersTable(tag: Tag) extends Table[User](tag, "users") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def username = column[String]("username", O.Unique)
    def email = column[String]("email", O.Unique)
    def passwordHash = column[String]("password_hash")
    def role = column[String]("role")
    def createdAt = column[LocalDateTime]("created_at")
    
    def * = (id.?, username, email, passwordHash, role, createdAt).mapTo[User]
  }
  
  private val users = TableQuery[UsersTable]
  
  def createSchema(): Future[Unit] = {
    db.run(users.schema.createIfNotExists)
  }
  
  def findAll(offset: Int = 0, limit: Int = 100): Future[Seq[User]] = {
    db.run(users.drop(offset).take(limit).result)
  }
}
