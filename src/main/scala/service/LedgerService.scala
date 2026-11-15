package service

import domain.*
import repository.AccountRepository
import java.util.concurrent.ConcurrentHashMap

class LedgerService(repo: AccountRepository):
  private val locks = new ConcurrentHashMap[AccountId, Object]()

  private def validatePositiveAmount(amount: BigDecimal): Either[LedgerError, Unit] =
    if amount > 0 then Right(())
    else Left(LedgerError.InvalidAmount(amount))

  private def validateActive(account: Account): Either[LedgerError, Unit] =
    if account.status == AccountStatus.Active then Right(())
    else Left(LedgerError.AccountNotActive(account.id, account.status))

  private def validateCurrency(expected: Currency, actual: Currency): Either[LedgerError, Unit] =
    if expected == actual then Right(())
    else Left(LedgerError.CurrencyMismatch(expected, actual))

  private def validateSufficientFunds(available: BigDecimal, required: BigDecimal): Either[LedgerError, Unit] =
    if available >= required then Right(())
    else Left(LedgerError.InsufficientFunds(available, required))

  private def lockFor(id: AccountId): Object =
    locks.computeIfAbsent(id, _ => new Object)

  def createAccount(id: AccountId, owner: String, currency: Currency): Either[LedgerError, Account] =
    val acc = Account(id, owner, Money(BigDecimal(0), currency), AccountStatus.Active)
    if repo.create(acc) then Right(acc)
    else Left(LedgerError.AccountAlreadyExists(id))

  def deposit(accountId: AccountId, money: Money): Either[LedgerError, Account] =
    lockFor(accountId).synchronized {
      for
        _ <- validatePositiveAmount(money.amount)
        account <- repo.findById(accountId).toRight(LedgerError.AccountNotFound(accountId))
        _ <- validateActive(account)
        _ <- validateCurrency(account.balance.currency, money.currency)
      yield
        val updatedBalance = Money(account.balance.amount + money.amount, account.balance.currency)
        val updatedAccount = account.copy(balance = updatedBalance)
        repo.save(updatedAccount)
    }

  def transfer(fromId: AccountId, toId: AccountId, money: Money): Either[LedgerError, (Account, Account)] =
    if fromId == toId then Left(LedgerError.SameAccount(fromId))
    else
      val (firstId, secondId) =
        if fromId.value < toId.value then (fromId, toId) else (toId, fromId)
      lockFor(firstId).synchronized {
        lockFor(secondId).synchronized {
          for
            _ <- validatePositiveAmount(money.amount)
            fromAcc <- repo.findById(fromId).toRight(LedgerError.AccountNotFound(fromId))
            toAcc <- repo.findById(toId).toRight(LedgerError.AccountNotFound(toId))
            _ <- validateActive(fromAcc)
            _ <- validateActive(toAcc)
            _ <- validateCurrency(fromAcc.balance.currency, money.currency)
            _ <- validateCurrency(toAcc.balance.currency, money.currency)
            _ <- validateSufficientFunds(fromAcc.balance.amount, money.amount)
          yield
            val updatedFrom = fromAcc.copy(
              balance = Money(fromAcc.balance.amount - money.amount, fromAcc.balance.currency)
            )
            val updatedTo = toAcc.copy(
              balance = Money(toAcc.balance.amount + money.amount, toAcc.balance.currency)
            )
            repo.save(updatedFrom)
            repo.save(updatedTo)
            (updatedFrom, updatedTo)
        }
      }