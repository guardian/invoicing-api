import java.time.{DayOfWeek, LocalDate}
import java.time.temporal.TemporalAdjusters

import com.gu.invoicing.preview.Impl.chargeNameToDay

DayOfWeek.THURSDAY.name()
LocalDate.parse("2020-10-28").`with`(TemporalAdjusters.previous(chargeNameToDay("Wednesday")))

DayOfWeek.valueOf("Monday".toUpperCase)


class Animal
class Cat extends Animal
class Dog extends Animal

class CageUP[A <: Animal](animal: A)
class CageLB[A >: Animal](animal: A)
val cageup = new CageUP(new Dog)
val cagelb = new CageLB(new Dog)

implicitly[Animal <:< Dog]
