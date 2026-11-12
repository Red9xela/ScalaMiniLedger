package service

import domain.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import repository.InMemoryAccountRepository

import java.util.concurrent.{Callable, Executors}
import scala.jdk.CollectionConverters.*

class LedgerServiceSpec extends AnyFlatSpec with Matchers:

  private def createFixture() =
    val repo    = new InMemoryAccountRepository()
    val service = new LedgerService(repo)
    (repo, service)

  "LedgerService" should "deposit money to an active account" in {
    val (_, service) = createFixture()
    val id           = AccountId("acc-1")
    service.createAccount(id, "Alice", Currency.RUB)

    val res = service.deposit(id, Money(BigDecimal(500), Currency.RUB))

    res shouldBe a[Right[?, ?]]
    res.map(_.balance.amount) shouldBe Right(BigDecimal(500))
  }

  it should "transfer money between two active accounts with the same currency" in {
    val (repo, service) = createFixture()
    val fromId          = AccountId("acc-1")
    val toId            = AccountId("acc-2")

    service.createAccount(fromId, "Alice", Currency.RUB)
    service.createAccount(toId, "Bob", Currency.RUB)
    service.deposit(fromId, Money(BigDecimal(1000), Currency.RUB))

    val transferRes = service.transfer(fromId, toId, Money(BigDecimal(400), Currency.RUB))

    transferRes shouldBe a[Right[?, ?]]
    repo.findById(fromId).map(_.balance.amount) shouldBe Some(BigDecimal(600))
    repo.findById(toId).map(_.balance.amount) shouldBe Some(BigDecimal(400))
  }

  it should "return InsufficientFunds when balance is too low" in {
    val (_, service) = createFixture()
    val fromId       = AccountId("acc-1")
    val toId         = AccountId("acc-2")

    service.createAccount(fromId, "Alice", Currency.RUB)
    service.createAccount(toId, "Bob", Currency.RUB)
    service.deposit(fromId, Money(BigDecimal(100), Currency.RUB))

    val res = service.transfer(fromId, toId, Money(BigDecimal(250), Currency.RUB))

    res shouldBe Left(LedgerError.InsufficientFunds(BigDecimal(100), BigDecimal(250)))
  }

  it should "return CurrencyMismatch when transferring between different currencies" in {
    val (_, service) = createFixture()
    val fromId       = AccountId("acc-1")
    val toId         = AccountId("acc-2")

    service.createAccount(fromId, "Alice", Currency.RUB)
    service.createAccount(toId, "Bob", Currency.USD)
    service.deposit(fromId, Money(BigDecimal(500), Currency.RUB))

    val res = service.transfer(fromId, toId, Money(BigDecimal(100), Currency.RUB))

    res shouldBe Left(LedgerError.CurrencyMismatch(Currency.USD, Currency.RUB))
  }

  it should "handle concurrent opposite transfers safely without deadlocks or lost updates" in {
    val (repo, service) = createFixture()
    val id1             = AccountId("acc-1")
    val id2             = AccountId("acc-2")

    service.createAccount(id1, "Alice", Currency.RUB)
    service.createAccount(id2, "Bob", Currency.RUB)
    service.deposit(id1, Money(BigDecimal(1000), Currency.RUB))
    service.deposit(id2, Money(BigDecimal(1000), Currency.RUB))

    val pool = Executors.newFixedThreadPool(8)
    val tasks = (1 to 100).flatMap { _ =>
      Seq(
        new Callable[Unit]:
          def call(): Unit = service.transfer(id1, id2, Money(BigDecimal(5), Currency.RUB))
        ,
        new Callable[Unit]:
          def call(): Unit = service.transfer(id2, id1, Money(BigDecimal(5), Currency.RUB))
      )
    }

    pool.invokeAll(tasks.asJava)
    pool.shutdown()

    val balance1 = repo.findById(id1).get.balance.amount
    val balance2 = repo.findById(id2).get.balance.amount

    balance1 shouldBe BigDecimal(1000)
    balance2 shouldBe BigDecimal(1000)
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