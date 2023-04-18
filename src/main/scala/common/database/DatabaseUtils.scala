package database

import slick.jdbc.JdbcBackend
import slick.jdbc.JdbcBackend.Database

object DatabaseUtils {
  def getConnection: JdbcBackend.Database = Database.forConfig("slick-mysql")
}
