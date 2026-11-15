package service

import domain.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import repository.InMemoryAccountRepository

import java.util.concurrent.{Callable, Executors}
import scala.jdk.CollectionConverters.*

class LedgerServiceSpec extends AnyFlatSpec with Matchers:
  private def openAccount(service: LedgerService, id: String, currency: Currency = Currency.RUB): AccountId =
    val accountId = AccountId(id)
    service.createAccount(accountId, "Owner", currency)
    accountId

  private def setStatus(repo: InMemoryAccountRepository, id: AccountId, status: AccountStatus): Unit =
    repo.findById(id).foreach(acc => repo.save(acc.copy(status = status)))

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

  it should "return AccountAlreadyExists and keep the balance when an account is created twice" in {
    val (repo, service) = createFixture()
    val id = openAccount(service, "acc-1")
    service.deposit(id, Money(BigDecimal(500), Currency.RUB))

    val res = service.createAccount(id, "Another", Currency.USD)

    res shouldBe Left(LedgerError.AccountAlreadyExists(id))
    repo.findById(id).map(_.balance) shouldBe Some(Money(BigDecimal(500), Currency.RUB))
  }

  it should "return InvalidAmount when depositing zero or a negative amount" in {
    val (_, service) = createFixture()
    val id = openAccount(service, "acc-1")

    service.deposit(id, Money(BigDecimal(0), Currency.RUB)) shouldBe
      Left(LedgerError.InvalidAmount(BigDecimal(0)))
    service.deposit(id, Money(BigDecimal(-10), Currency.RUB)) shouldBe
      Left(LedgerError.InvalidAmount(BigDecimal(-10)))
  }

  it should "return AccountNotFound when depositing to a missing account" in {
    val (_, service) = createFixture()
    val missing = AccountId("missing")

    service.deposit(missing, Money(BigDecimal(10), Currency.RUB)) shouldBe
      Left(LedgerError.AccountNotFound(missing))
  }

  it should "return CurrencyMismatch when depositing in a different currency" in {
    val (_, service) = createFixture()
    val id = openAccount(service, "acc-1", Currency.RUB)

    service.deposit(id, Money(BigDecimal(10), Currency.USD)) shouldBe
      Left(LedgerError.CurrencyMismatch(Currency.RUB, Currency.USD))
  }

  it should "return AccountNotActive when depositing to a frozen account" in {
    val (repo, service) = createFixture()
    val id = openAccount(service, "acc-1")
    setStatus(repo, id, AccountStatus.Frozen)

    service.deposit(id, Money(BigDecimal(10), Currency.RUB)) shouldBe
      Left(LedgerError.AccountNotActive(id, AccountStatus.Frozen))
  }

  it should "return AccountNotActive when depositing to a closed account" in {
    val (repo, service) = createFixture()
    val id = openAccount(service, "acc-1")
    setStatus(repo, id, AccountStatus.Closed)

    service.deposit(id, Money(BigDecimal(10), Currency.RUB)) shouldBe
      Left(LedgerError.AccountNotActive(id, AccountStatus.Closed))
  }

  it should "return SameAccount and leave the balance unchanged on a self-transfer" in {
    val (repo, service) = createFixture()
    val id = openAccount(service, "acc-1")
    service.deposit(id, Money(BigDecimal(1000), Currency.RUB))

    val res = service.transfer(id, id, Money(BigDecimal(100), Currency.RUB))

    res shouldBe Left(LedgerError.SameAccount(id))
    repo.findById(id).map(_.balance.amount) shouldBe Some(BigDecimal(1000))
  }

  it should "return InvalidAmount when transferring zero or a negative amount" in {
    val (_, service) = createFixture()
    val from = openAccount(service, "acc-1")
    val to = openAccount(service, "acc-2")

    service.transfer(from, to, Money(BigDecimal(0), Currency.RUB)) shouldBe
      Left(LedgerError.InvalidAmount(BigDecimal(0)))
    service.transfer(from, to, Money(BigDecimal(-5), Currency.RUB)) shouldBe
      Left(LedgerError.InvalidAmount(BigDecimal(-5)))
  }

  it should "return AccountNotFound when the sender or the receiver does not exist" in {
    val (_, service) = createFixture()
    val existing = openAccount(service, "acc-1")
    val missing = AccountId("missing")
    val money = Money(BigDecimal(10), Currency.RUB)

    service.transfer(missing, existing, money) shouldBe Left(LedgerError.AccountNotFound(missing))
    service.transfer(existing, missing, money) shouldBe Left(LedgerError.AccountNotFound(missing))
  }

  it should "return AccountNotActive when the sender or the receiver is not active" in {
    val (repo, service) = createFixture()
    val from = openAccount(service, "acc-1")
    val to = openAccount(service, "acc-2")
    service.deposit(from, Money(BigDecimal(1000), Currency.RUB))
    val money = Money(BigDecimal(10), Currency.RUB)

    setStatus(repo, to, AccountStatus.Frozen)
    service.transfer(from, to, money) shouldBe
      Left(LedgerError.AccountNotActive(to, AccountStatus.Frozen))

    setStatus(repo, to, AccountStatus.Active)
    setStatus(repo, from, AccountStatus.Closed)
    service.transfer(from, to, money) shouldBe
      Left(LedgerError.AccountNotActive(from, AccountStatus.Closed))
  }

  it should "return CurrencyMismatch when the transfer currency differs from the sender account currency" in {
    val (_, service) = createFixture()
    val from = openAccount(service, "acc-1", Currency.RUB)
    val to = openAccount(service, "acc-2", Currency.USD)
    service.deposit(from, Money(BigDecimal(500), Currency.RUB))

    service.transfer(from, to, Money(BigDecimal(10), Currency.USD)) shouldBe
      Left(LedgerError.CurrencyMismatch(Currency.RUB, Currency.USD))
  }

  it should "not change any balance when a transfer fails" in {
    val (repo, service) = createFixture()
    val from = openAccount(service, "acc-1")
    val to = openAccount(service, "acc-2")
    service.deposit(from, Money(BigDecimal(100), Currency.RUB))

    service.transfer(from, to, Money(BigDecimal(250), Currency.RUB)) shouldBe a[Left[?, ?]]

    repo.findById(from).map(_.balance.amount) shouldBe Some(BigDecimal(100))
    repo.findById(to).map(_.balance.amount) shouldBe Some(BigDecimal(0))
  }

  it should "stay consistent under concurrent deposits and transfers when ids are different String instances" in {
    val (repo, service) = createFixture()

    def fresh(value: String): AccountId = AccountId(new String(value))

    service.createAccount(fresh("acc-1"), "Alice", Currency.RUB)
    service.createAccount(fresh("acc-2"), "Bob", Currency.RUB)
    service.deposit(fresh("acc-1"), Money(BigDecimal(1000), Currency.RUB))
    service.deposit(fresh("acc-2"), Money(BigDecimal(1000), Currency.RUB))

    val deposits = Seq.fill[Callable[Any]](200)(() =>
      service.deposit(fresh("acc-1"), Money(BigDecimal(1), Currency.RUB))
    )
    val aToB = Seq.fill[Callable[Any]](100)(() =>
      service.transfer(fresh("acc-1"), fresh("acc-2"), Money(BigDecimal(5), Currency.RUB))
    )
    val bToA = Seq.fill[Callable[Any]](100)(() =>
      service.transfer(fresh("acc-2"), fresh("acc-1"), Money(BigDecimal(3), Currency.RUB))
    )

    val pool = Executors.newFixedThreadPool(8)
    try
      val futures = pool.invokeAll((deposits ++ aToB ++ bToA).asJava)
      futures.asScala.foreach(_.get()) // surfaces exceptions from tasks
    finally pool.shutdown()

    repo.findById(AccountId("acc-1")).map(_.balance.amount) shouldBe Some(BigDecimal(1000))
    repo.findById(AccountId("acc-2")).map(_.balance.amount) shouldBe Some(BigDecimal(1200))
  }