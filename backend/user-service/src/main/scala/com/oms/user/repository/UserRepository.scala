package com.oms.user.repository

import com.oms.user.model.{User, AccountStatus, UserStatsResponse}
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
    def status = column[String]("status")
    def phoneNumber = column[Option[String]]("phone_number")
    def lastLogin = column[Option[LocalDateTime]]("last_login")
    def loginAttempts = column[Int]("login_attempts")
    def createdAt = column[LocalDateTime]("created_at")
    def updatedAt = column[LocalDateTime]("updated_at")
    
    def * = (id.?, username, email, passwordHash, role, status, 
             phoneNumber, lastLogin, loginAttempts, createdAt, updatedAt).mapTo[User]
  }
  
  private val users = TableQuery[UsersTable]
  
  def createSchema(): Future[Unit] = {
    db.run(users.schema.createIfNotExists)
  }
  
  def create(user: User): Future[User] = {
    val userWithTimestamp = user.copy(
      createdAt = LocalDateTime.now(),
      updatedAt = LocalDateTime.now()
    )
    val insertQuery = (users returning users.map(_.id) into ((u, id) => u.copy(id = Some(id)))) += userWithTimestamp
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
  
  def update(
    id: Long, 
    email: Option[String], 
    role: Option[String],
    status: Option[String],
    phoneNumber: Option[String]
  ): Future[Int] = {
    val query = users.filter(_.id === id)
    
    val updateActions = Seq(
      email.map(e => query.map(_.email).update(e)),
      role.map(r => query.map(_.role).update(r)),
      status.map(s => query.map(_.status).update(s)),
      phoneNumber.map(p => query.map(_.phoneNumber).update(Some(p)))
    ).flatten
    
    if (updateActions.isEmpty) {
      Future.successful(0)
    } else {
      val combinedAction = DBIO.sequence(updateActions).map(_.sum)
      db.run(combinedAction.flatMap(_ => query.map(_.updatedAt).update(LocalDateTime.now())))
    }
  }
  
  def updateProfile(
    id: Long, 
    email: Option[String], 
    username: Option[String],
    phoneNumber: Option[String]
  ): Future[Int] = {
    val query = users.filter(_.id === id)
    
    val updateActions = Seq(
      email.map(e => query.map(_.email).update(e)),
      username.map(u => query.map(_.username).update(u)),
      phoneNumber.map(p => query.map(_.phoneNumber).update(Some(p)))
    ).flatten
    
    if (updateActions.isEmpty) {
      Future.successful(0)
    } else {
      val combinedAction = DBIO.sequence(updateActions).map(_.sum)
      db.run(combinedAction.flatMap(_ => query.map(_.updatedAt).update(LocalDateTime.now())))
    }
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
  
  // Search and filter methods
  def search(
    query: Option[String],
    role: Option[String],
    status: Option[String],
    offset: Int = 0,
    limit: Int = 20
  ): Future[Seq[User]] = {
    var baseQuery = users.asInstanceOf[Query[UsersTable, User, Seq]]
    
    query.foreach { q =>
      val searchTerm = s"%${q.toLowerCase}%"
      baseQuery = baseQuery.filter(u => 
        u.username.toLowerCase.like(searchTerm) || 
        u.email.toLowerCase.like(searchTerm)
      )
    }
    
    role.foreach { r =>
      baseQuery = baseQuery.filter(_.role === r)
    }
    
    status.foreach { s =>
      baseQuery = baseQuery.filter(_.status === s)
    }
    
    db.run(baseQuery.drop(offset).take(limit).result)
  }
  
  def countFiltered(
    query: Option[String],
    role: Option[String],
    status: Option[String]
  ): Future[Int] = {
    var baseQuery = users.asInstanceOf[Query[UsersTable, User, Seq]]
    
    query.foreach { q =>
      val searchTerm = s"%${q.toLowerCase}%"
      baseQuery = baseQuery.filter(u => 
        u.username.toLowerCase.like(searchTerm) || 
        u.email.toLowerCase.like(searchTerm)
      )
    }
    
    role.foreach { r =>
      baseQuery = baseQuery.filter(_.role === r)
    }
    
    status.foreach { s =>
      baseQuery = baseQuery.filter(_.status === s)
    }
    
    db.run(baseQuery.length.result)
  }
  
  // Account status management
  def updateStatus(id: Long, status: String): Future[Int] = {
    val query = users.filter(_.id === id)
    db.run(
      query.map(u => (u.status, u.updatedAt)).update((status, LocalDateTime.now()))
    )
  }
  
  def updateLoginAttempts(id: Long, attempts: Int): Future[Int] = {
    db.run(users.filter(_.id === id).map(_.loginAttempts).update(attempts))
  }
  
  def recordLogin(id: Long): Future[Int] = {
    val query = users.filter(_.id === id)
    db.run(
      query.map(u => (u.lastLogin, u.loginAttempts)).update((Some(LocalDateTime.now()), 0))
    )
  }
  
  // Bulk operations
  def bulkUpdateStatus(ids: Seq[Long], status: String): Future[Int] = {
    val query = users.filter(_.id.inSet(ids))
    db.run(
      query.map(u => (u.status, u.updatedAt)).update((status, LocalDateTime.now()))
    )
  }
  
  def bulkDelete(ids: Seq[Long]): Future[Int] = {
    db.run(users.filter(_.id.inSet(ids)).delete)
  }
  
  // Statistics
  def getStats(): Future[UserStatsResponse] = {
    val totalQuery = users.length.result
    val activeQuery = users.filter(_.status === AccountStatus.ACTIVE).length.result
    val lockedQuery = users.filter(_.status === AccountStatus.LOCKED).length.result
    
    val combinedQuery = for {
      total <- totalQuery
      active <- activeQuery
      locked <- lockedQuery
    } yield UserStatsResponse(total, active, locked)
    
    db.run(combinedQuery)
  }
  
  def findByIds(ids: Seq[Long]): Future[Seq[User]] = {
    db.run(users.filter(_.id.inSet(ids)).result)
  }
}
