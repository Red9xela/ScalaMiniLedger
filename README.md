# Scala Mini-Ledger

Учебный проект на Scala 3, представляющий собой небольшое in-memory приложение для управления банковскими счетами и выполнения денежных переводов.

Проект демонстрирует применение функциональных возможностей Scala 3, моделирование предметной области, работу с бизнес-ошибками и основы многопоточной обработки операций.

## Возможности

- **Account Management** — создание банковских счетов с указанием владельца и валюты.
- **Deposits** — пополнение счетов с проверкой суммы, существования счёта, его статуса и валюты.
- **Money Transfers** — перевод денежных средств между счетами с проверкой баланса, статуса и совместимости валют.
- **Account Statements** — форматирование информации о счёте с использованием Type Classes.
- **Error Handling** — обработка бизнес-ошибок через `Either`, без использования исключений для штатных ошибок.

Поддерживаемые валюты: `RUB`, `USD`, `EUR`.

Статусы счетов: `Active`, `Frozen`, `Closed`. Операции пополнения и перевода доступны только для активных счетов.

## Tech Stack

- **Language:** Scala 3.10.0
- **Build Tool:** sbt
- **Testing:** ScalaTest 3.2.20
- **JVM Collections:** ConcurrentHashMap

## Scala 3 Concepts

В проекте используются следующие возможности языка:

| Concept | Usage |
|---|---|
| Opaque Types | Типобезопасная модель идентификаторов `AccountId` |
| ADT | Моделирование валют, статусов счетов и бизнес-ошибок через `enum` |
| Case Classes | Доменные модели `Account` и `Money` |
| Pattern Matching | Обработка результатов операций |
| Either | Представление успешных результатов и бизнес-ошибок |
| For-Comprehension | Последовательная композиция операций с `Either` |
| Type Classes | Абстракция форматирования через `StatementFormatter[A]` |
| Given / Using | Предоставление и использование экземпляров Type Classes |
| Extension Methods | Добавление метода `formatStatement` |
| Concurrency | Использование `ConcurrentHashMap` и JVM-синхронизации |

## Architecture

Проект разделён на три основных слоя:

```text
src/
├── main/
│   └── scala/
│       ├── domain/
│       │   ├── AccountId.scala
│       │   ├── Domain.scala
│       │   └── StatementFormatter.scala
│       │
│       ├── repository/
│       │   ├── AccountRepository.scala
│       │   └── InMemoryAccountRepository.scala
│       │
│       ├── service/
│       │   └── LedgerService.scala
│       │
│       └── main/
│           └── Main.scala
│
└── test/
    └── scala/
        └── service/
            └── LedgerServiceSpec.scala
```

**Domain**

Содержит основные бизнес-модели, типы идентификаторов, валюты, статусы счетов и ошибки предметной области.

**Repository**

Определяет интерфейс `AccountRepository` и его in-memory реализацию `InMemoryAccountRepository` на базе `ConcurrentHashMap`.

**Service**

Содержит бизнес-логику приложения. `LedgerService` отвечает за создание счетов, пополнение и перевод средств, а также проверку бизнес-правил.

## Getting Started

### Prerequisites

- JDK
- sbt

### Installation

Клонирование репозитория:

```bash
git clone https://github.com/Red9xela/ScalaMiniLedger.git
cd ScalaMiniLedger
```

### Run the application

Запуск демонстрационного сценария:

```bash
sbt run
```

### Run tests

Запуск тестов:

```bash
sbt test
```

## Usage Example

Ниже представлен пример использования `LedgerService`:

```scala
import domain.*
import repository.InMemoryAccountRepository
import service.LedgerService

val repo = new InMemoryAccountRepository()
val service = new LedgerService(repo)

// Create accounts
val aliceId = AccountId("acc-1")
val bobId   = AccountId("acc-2")

service.createAccount(aliceId, "Alice", Currency.RUB)
service.createAccount(bobId, "Bob", Currency.RUB)

// Deposit and transfer
val result = for
  _ <- service.deposit(
    aliceId,
    Money(BigDecimal(1000), Currency.RUB)
  )
  accounts <- service.transfer(
    aliceId,
    bobId,
    Money(BigDecimal(350), Currency.RUB)
  )
yield accounts

// Handle result
result match
  case Right((alice, bob)) =>
    println(alice.formatStatement)
    println(bob.formatStatement)

  case Left(error) =>
    println(s"Operation failed: $error")
```

### Example Output

```text
[Account acc-1] Owner: Alice | Balance: 650 RUB | Status: Active
[Account acc-2] Owner: Bob | Balance: 350 RUB | Status: Active
```

## Testing

Тестирование реализовано с использованием ScalaTest.

`LedgerServiceSpec` покрывает следующие сценарии:

- Успешное пополнение активного счёта.
- Успешный перевод средств между счетами с одинаковой валютой.
- Обработка ошибки недостаточного баланса (`InsufficientFunds`).
- Обработка несовпадения валют (`CurrencyMismatch`).
- Конкурентные переводы между двумя счетами в противоположных направлениях.
- Форматирование выписки по счёту с использованием `StatementFormatter`.

## Project Scope

Проект предназначен для изучения Scala 3 и принципов построения небольших сервисов с бизнес-логикой.

Текущая реализация использует in-memory хранилище. Данные не сохраняются после завершения приложения.

Это учебная реализация, а не готовая банковская система. Используемые механизмы синхронизации требуют дальнейшей проверки и усиления перед применением в production-системах.
