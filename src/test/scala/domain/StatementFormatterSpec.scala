package domain

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class StatementFormatterSpec extends AnyFlatSpec with Matchers:

  private def account(status: AccountStatus) =
    Account(AccountId("acc-1"), "Alice", Money(BigDecimal(650), Currency.RUB), status)

  "StatementFormatter" should "format an active account" in {
    account(AccountStatus.Active).formatStatement shouldBe
      "[Account acc-1] Owner: Alice | Balance: 650 RUB | Status: Active"
  }

  it should "format a frozen account" in {
    account(AccountStatus.Frozen).formatStatement shouldBe
      "[Account acc-1] Owner: Alice | Balance: 650 RUB | Status: Frozen"
  }

  "StatementFormatter" should "format account statement using given instance" in {
    val account = Account(
      id = AccountId("acc-1"),
      owner = "Alice",
      balance = Money(BigDecimal(650), Currency.RUB),
      status = AccountStatus.Active
    )

    account.formatStatement shouldBe "[Account acc-1] Owner: Alice | Balance: 650 RUB | Status: Active"
  }