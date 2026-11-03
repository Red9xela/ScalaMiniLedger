package repository
import domain.{Account, AccountId}

trait AccountRepository:
  def findById(id: AccountId): Option[Account]

  def save(account: Account): Account