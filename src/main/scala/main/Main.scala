package main

import domain.*
import repository.InMemoryAccountRepository
import service.LedgerService

@main def runLedgerDemo(): Unit =
  val repo    = new InMemoryAccountRepository()
  val service = new LedgerService(repo)

  val aliceId = AccountId("acc-1")
  val bobId   = AccountId("acc-2")

  service.createAccount(aliceId, "Alice", Currency.RUB)
  service.createAccount(bobId, "Bob", Currency.RUB)

  val result = for
    _                   <- service.deposit(aliceId, Money(BigDecimal(1000), Currency.RUB))
    (aliceAcc, bobAcc)  <- service.transfer(aliceId, bobId, Money(BigDecimal(350), Currency.RUB))
  yield (aliceAcc, bobAcc)

  result match
    case Right((alice, bob)) =>
      println("Перевод выполнен успешно:")
      println(alice.formatStatement)
      println(bob.formatStatement)
    case Left(error) =>
      println(s"Ошибка операции: $error")