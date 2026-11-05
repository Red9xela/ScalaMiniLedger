package repository
import domain.{Account, AccountId}
import java.util.concurrent.ConcurrentHashMap

class InMemoryAccountRepository extends AccountRepository {
  private val storage = new ConcurrentHashMap[AccountId, Account]()

  def findById(id: AccountId): Option[domain.Account] = 
    Option(storage.get(id))

  def save(account: Account): Account =
    storage.put(account.id, account)
    account
}

