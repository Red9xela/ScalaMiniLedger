package domain

trait StatementFormatter[A]:
  def format(value: A): String

object StatementFormatter:
  given StatementFormatter[Account] with
    def format(account: Account): String =
      s"[Account ${account.id.value}] Owner: ${account.owner} | Balance: ${account.balance.amount} ${account.balance.currency} | Status: ${account.status}"

extension [A](value: A)
  def formatStatement(using formatter: StatementFormatter[A]): String =
    formatter.format(value)