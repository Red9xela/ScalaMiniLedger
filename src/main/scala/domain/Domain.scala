package domain

enum Currency:
  case RUB, USD, EUR

enum AccountStatus:
  case Active, Frozen, Closed

final case class Money(amount: BigDecimal,
                       currency: Currency)

final case class Account(
                          id: AccountId,
                          owner: String,
                          balance: Money,
                          status: AccountStatus
                        )
  

enum LedgerError:
  case AccountNotFound(accountId: AccountId)
  case AccountNotActive(accountId: AccountId, status: AccountStatus)
  case InsufficientFunds(available: BigDecimal, required: BigDecimal)
  case CurrencyMismatch(expected: Currency, actual: Currency)
  case InvalidAmount(amount: BigDecimal)


