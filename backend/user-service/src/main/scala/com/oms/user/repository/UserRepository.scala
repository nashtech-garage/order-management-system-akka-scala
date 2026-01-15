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
  
  def create(user: User): Future[User] = {
    val insertQuery = (users returning users.map(_.id) into ((u, id) => u.copy(id = Some(id)))) += user
    db.run(insertQuery)
  }
  
  def findById(id: Long): Future[Option[User]] = {
    db.run(users.filter(_.id === id).result.headOption)
  }
  
  def findByUsername(username: String): Future[Option[User]] = {
    db.run(users.filter(_.username === username).result.headOption)
  }
  
  def findByEmail(email: String): Future[Option[User]] = {
    db.run(users.filter(_.email === email).result.headOption)
  }
  
  def findAll(offset: Int = 0, limit: Int = 20): Future[Seq[User]] = {
    db.run(users.drop(offset).take(limit).result)
  }
  
  def update(id: Long, email: Option[String], role: Option[String]): Future[Int] = {
    val query = users.filter(_.id === id)
    val updates = (email, role) match {
      case (Some(e), Some(r)) => query.map(u => (u.email, u.role)).update((e, r))
      case (Some(e), None) => query.map(_.email).update(e)
      case (None, Some(r)) => query.map(_.role).update(r)
      case _ => DBIO.successful(0)
    }
    db.run(updates)
  }
  
  def updateProfile(id: Long, email: Option[String], username: Option[String]): Future[Int] = {
    val query = users.filter(_.id === id)
    val updates = (email, username) match {
      case (Some(e), Some(u)) => query.map(user => (user.email, user.username)).update((e, u))
      case (Some(e), None) => query.map(_.email).update(e)
      case (None, Some(u)) => query.map(_.username).update(u)
      case _ => DBIO.successful(0)
    }
    db.run(updates)
  }
  
  def updatePassword(id: Long, newPasswordHash: String): Future[Int] = {
    db.run(users.filter(_.id === id).map(_.passwordHash).update(newPasswordHash))
  }
  
  def delete(id: Long): Future[Int] = {
    db.run(users.filter(_.id === id).delete)
  }
  
  def count(): Future[Int] = {
    db.run(users.length.result)
  }
}
