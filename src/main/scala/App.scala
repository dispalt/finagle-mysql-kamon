import com.twitter.finagle.Mysql
import com.twitter.finagle.mysql.Client
import com.twitter.util.{Await, Future, FuturePool, JavaTimer}
import io.netty.util.concurrent.DefaultThreadFactory
import kamon.Kamon
import kamon.context.Context
import kamon.tag.Lookups.plain

import java.util.concurrent.{Executors, ThreadFactory}
import scala.concurrent.duration.Duration
import scala.concurrent.{Await => SAwait}

object Application {
  private var i = 0

  def printCtx(extraContext: String = "") = {
    val suffix = if (extraContext != "") s" extra=[$extraContext]" else ""
    println(
      s"$i [${Thread.currentThread().getName}] [${Kamon.currentContext()}]$suffix"
    )
    i += 1
  }

  private def singleMysql(client: Client)(id: Int): Future[Unit] = {
    val keyName = s"the key id=${id}"
    val keyValue = s"the string $id"
    Kamon.runWithContext(Context.of(keyName, keyValue)) {
      for {
        _ <- Future(printCtx("before"))
        _ <- client.select("select * from ids limit 10")(identity)
        _ <- Future(printCtx("after"))
        _ <- Future {
          val result = Kamon.currentContext().getTag(plain(keyName))

          assert(
            result == keyValue,
            s"Failed on iteration $id, result=[$result]"
          )
        }
      } yield ()
    }
  }

  private def single(pool: FuturePool)(id: Int): Future[Unit] = {
    val keyName = s"the key id=$id"
    val keyValue = s"the string $id"
    Kamon.runWithContext(Context.of(keyName, keyValue)) {
      for {
        _ <- Future(printCtx("before"))
        //      _ <- client.select("select * from ids limit 10")(identity)
        _ <- pool.apply(printCtx("within"))
        _ <- Future(printCtx("after"))
        _ <- Future(
          assert(
            Kamon.currentContext().getTag(plain(keyName)) == keyValue,
            s"Failed on iteration $id"
          )
        )
      } yield ()
    }
  }

  def mkNettyThreadFactory(): ThreadFactory = {
    val prefix = "finagle/netty4"
    val threadGroup =
      new ThreadGroup(Thread.currentThread().getThreadGroup, prefix)
    new DefaultThreadFactory(
      /* poolName */ prefix,
      /* daemon */ true,
      /* priority */ Thread.NORM_PRIORITY,
      /* threadGroup */ threadGroup
    )
  }

  /** <code>
    *   create table ids
    * (
    *    id  bigint
    *        primary key,
    *    ref text not null
    * )
    *    charset = utf8mb4;
    *  </code>
    * @param args
    */
  def main(args: Array[String]): Unit = {
    val tf =
      FuturePool.apply(Executors.newCachedThreadPool(mkNettyThreadFactory()))

    Kamon.init()

    lazy val client = Mysql.client
      .withCredentials(sys.env("USER"), sys.env("PASS"))
      .withDatabase(sys.env("DB"))
      .newRichClient(sys.env("DEST"))

    implicit val timer = new JavaTimer(true)

//    val results = Future.collect((0 to 10).map(single(tf)))
    val results = Future.collect((0 to 10).map(singleMysql(client)))

    Await.result(results)

    printCtx()

    Await.result(client.close())
    SAwait.result(Kamon.stop(), Duration.Inf)
  }

}