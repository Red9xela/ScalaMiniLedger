# Scala Mini-Ledger

Учебный проект на Scala 3, представляющий собой небольшое in-memory приложение для управления банковскими счетами и выполнения денежных переводов.

Проект демонстрирует применение функциональных возможностей Scala 3, моделирование предметной области, работу с бизнес-ошибками и основы многопоточной обработки операций.

## Возможности

- **Account Management** — создание банковских счетов с указанием владельца и валюты. Повторное создание счёта с тем же идентификатором возвращает ошибку.
- **Deposits** — пополнение счетов с проверкой суммы, существования счёта, его статуса и валюты.
- **Money Transfers** — перевод денежных средств между счетами с проверкой баланса, статуса и совместимости валют. Перевод на тот же счёт отклоняется ошибкой.
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
| Concurrency | `ConcurrentHashMap`, атомарный `putIfAbsent` и блокировки по счёту (по равенству идентификатора) |

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
        ├── domain/
        │   └── StatementFormatterSpec.scala
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

val aliceId = AccountId("acc-1")
val bobId   = AccountId("acc-2")

service.createAccount(aliceId, "Alice", Currency.RUB)
service.createAccount(bobId, "Bob", Currency.RUB)

// Deposit and transfer
val result = for
  _ <- service.createAccount(aliceId, "Alice", Currency.RUB)
  _ <- service.createAccount(bobId, "Bob", Currency.RUB)
  _ <- service.deposit(aliceId, Money(BigDecimal(1000), Currency.RUB))
  accounts <- service.transfer(aliceId, bobId, Money(BigDecimal(350), Currency.RUB))
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

- Создание счёта и ошибка `AccountAlreadyExists` при повторном создании (баланс не сбрасывается).
- Пополнение: успешное, `InvalidAmount`, `AccountNotFound`, `CurrencyMismatch`, `AccountNotActive` (Frozen и Closed).
- Перевод: успешный, `InsufficientFunds`, `CurrencyMismatch`, `InvalidAmount`, `AccountNotFound`, `AccountNotActive`, `SameAccount`.
- Неизменность балансов при неудачном переводе.
- Согласованность балансов при параллельных пополнениях и переводах с идентификаторами, созданными как разные экземпляры `String`.

`StatementFormatterSpec` проверяет форматирование выписки для активного и замороженного счёта.

## Project Scope

Известные ограничения:

- Перевод выполняется двумя последовательными операциями `save`, поэтому не атомарен: параллельное чтение может увидеть промежуточное состояние.
- Карта блокировок растёт вместе с числом счетов и не очищается.
- Суммы не округляются и не ограничиваются по числу знаков после запятой.
- Идентификатор счёта и имя владельца не проверяются на пустоту.
- Нет журнала операций: хранится только текущий баланс.
