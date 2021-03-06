package com.btesila.communication

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import com.btesila.communication.AskingQuestions.Barista.Ok
import com.btesila.communication.AskingQuestions.OrderRegister.Receipt

import scala.concurrent.duration._
import scala.util.Success


object AskingQuestions extends App {

  class Barista extends Actor with ActorLogging {

    import Barista._
    import OrderRegister._

    import context.dispatcher

    val child = context.actorOf(OrderRegister.props(), "hakky-order-register")

    override def receive: Receive = {
      case CoffeeRequest =>
        log.debug("Preparing a coffee right away")
        sender() ! Ok
        child ! Order(Coffee)
      case JuiceRequest =>
        log.debug("Preparing a juice right away")
        implicit val timeout = Timeout(1 seconds)
        val receipt = child ? Order(Juice)
        val otherside = sender()
        receipt.mapTo[Receipt] pipeTo otherside

      case Receipt(price) =>
        log.debug("Gotta handle the receipt worth {} to the customer", price)
    }
  }

  object Barista {
    def props(): Props = Props(classOf[Barista])

    case object CoffeeRequest

    case object JuiceRequest

    case object Ok

  }

  class OrderRegister extends Actor with ActorLogging {

    import OrderRegister._

    var revenue = 0

    override def receive: Receive = {
      case Order(article) =>
        val price = prices(article)
        revenue += price
        log.info("Increasing my revenue by {}", price)
        sender() ! Receipt(price)
    }
  }

  object OrderRegister {
    def props(): Props = Props(classOf[OrderRegister])

    sealed trait Article

    case object Coffee extends Article

    case object Juice extends Article

    case class Receipt(price: Int)

    case class Order(article: Article)

    val prices: Map[Article, Int] = Map(Coffee -> 10, Juice -> 5)
  }

  val system = ActorSystem("hakky-bar")
  val barista = system.actorOf(Barista.props(), "hakky-barista")

  import system.dispatcher

  implicit val timeout = Timeout(1 seconds)

  val response = barista ? Barista.CoffeeRequest
  response onComplete {
    case Success(Ok) => println("Yay, got a response")
    case _ => println("No response...")
  }

  val anotherResponse = barista ? Barista.JuiceRequest
  anotherResponse onComplete {
    case Success(Receipt(_)) => println("Yay, got a receipt...")
    case _ => println("No receipt...")
  }
}
