package repository
import domain.{Account, AccountId}
import java.util.concurrent.ConcurrentHashMap

class InMemoryAccountRepository extends AccountRepository {
  private val storage = new ConcurrentHashMap[AccountId, Account]()

  def findById(id: domain.AccountId): Option[domain.Account] = ???

  def save(account: domain.Account): domain.Account = ???
}

