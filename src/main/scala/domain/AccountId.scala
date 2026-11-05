package domain

opaque type AccountId <: AnyRef = String

object AccountId:
  def apply(value: String): AccountId = value

  extension (id: AccountId)
    def value: String = id