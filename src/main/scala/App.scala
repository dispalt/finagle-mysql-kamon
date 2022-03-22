import com.twitter.finagle.Mysql
import com.twitter.util.{Await, Future}
import kamon.Kamon
import kamon.context.Context


object Application {
  private var i = 0

  def printCtx() = {
    println(s"$i [${Thread.currentThread().getName}] [${Kamon.currentContext()}]")
    i += 1
  }

  /**
   create table ids
(
    id  bigint
        primary key,
    ref text not null
)
    charset = utf8mb4
   * @param args
   */
  def main(args: Array[String]): Unit = {
    Kamon.init()

    lazy val client = Mysql.client
      .withCredentials(sys.env("USER"), sys.env("PASS"))
      .withDatabase(sys.env("DB"))
      .newRichClient(sys.env("DEST"))


    def prg = for {
      _ <-  Future(Kamon.storeContext(Context.of("the key", "the string")))
      _ <- Future(printCtx())
      _ <- client.select("select * from ids limit 10")(identity)
      _ <- Future(printCtx())

    } yield ()


    Await.result(prg)

    printCtx()
  }



}