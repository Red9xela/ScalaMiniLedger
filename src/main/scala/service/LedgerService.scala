package service

import domain.*
import repository.AccountRepository

class LedgerService(repo: AccountRepository):

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

  def createAccount(id: AccountId, owner: String, currency: Currency): Account =
    val newAccount = Account(
      id = id,
      owner = owner,
      balance = Money(BigDecimal(0), currency),
      status = AccountStatus.Active
    )
    repo.save(newAccount)

  def deposit(accountId: AccountId, money: Money): Either[LedgerError, Account] =
    accountId.synchronized {
      for
        _       <- validatePositiveAmount(money.amount)
        account <- repo.findById(accountId).toRight(LedgerError.AccountNotFound(accountId))
        _       <- validateActive(account)
        _       <- validateCurrency(account.balance.currency, money.currency)
      yield
        val updatedBalance = Money(account.balance.amount + money.amount, account.balance.currency)
        val updatedAccount = account.copy(balance = updatedBalance)
        repo.save(updatedAccount)
    }

  def transfer(
                fromId: AccountId,
                toId: AccountId,
                money: Money
              ): Either[LedgerError, (Account, Account)] =
    // Упорядочиваем блокировки по ID для предотвращения Deadlock при встречных переводах
    val (firstLock, secondLock) =
      if fromId.value < toId.value then (fromId, toId)
      else (toId, fromId)

    firstLock.synchronized {
      secondLock.synchronized {
        for
          _       <- validatePositiveAmount(money.amount)
          fromAcc <- repo.findById(fromId).toRight(LedgerError.AccountNotFound(fromId))
          toAcc   <- repo.findById(toId).toRight(LedgerError.AccountNotFound(toId))
          _       <- validateActive(fromAcc)
          _       <- validateActive(toAcc)
          _       <- validateCurrency(fromAcc.balance.currency, money.currency)
          _       <- validateCurrency(toAcc.balance.currency, money.currency)
          _       <- validateSufficientFunds(fromAcc.balance.amount, money.amount)
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