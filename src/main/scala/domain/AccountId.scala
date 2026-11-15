package domain

opaque type AccountId = String

object AccountId:
  def apply(value: String): AccountId = value

  extension (id: AccountId)
    def value: String = id