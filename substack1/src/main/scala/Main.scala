import java.nio.charset.StandardCharsets.UTF_8
import scala.util.Try
import scala.xml.Elem
import scala.collection.parallel.CollectionConverters._
import scala.collection.parallel.ForkJoinTaskSupport

object Main extends App {
  val sep = "\t" // Output column separator (TSV)

  val numOfThreads = 12

  def charAllowed(c: Char): Boolean = c.isLetterOrDigit || "-_.".contains(c)
  val stringToWords: String => Seq[String] = _.map(Option(_).filter(charAllowed) getOrElse ' ').split(" ")
  val wordsToNicks: Seq[String] => Seq[String] = _.collect { case s"$nick.substack.com" => nick }

  val XMLAdjuster: String => String = // can possibly replace in CDATA
    Seq("itunes", "content", "atom", "podcast").map { t =>
      (_:String).replace(s"<$t:", s"<${t}_").replace(s"</$t:", s"</${t}_")
    }.reduce(_ compose _)

  val nickToUrl: String => String = s"https://" + _ + ".substack.com/feed"
  val download: String => String = { new java.net.URL(_: String).openStream().readAllBytes() } andThen { new String(_, UTF_8) }
  // { Utils.getURLBytes(_:String) } andThen { new String(_, UTF_8) }
  val tags = Seq("itunes_name", "itunes_email")
  def getColumns(root: Elem): Map[String, String] = tags.flatMap(col => ( root \\ col ).map(col -> _.text) ).toMap

  val nickToColumns = nickToUrl andThen download andThen XMLAdjuster andThen xml.XML.loadString andThen getColumns

  val nicks = io.Source
    .stdin
    .getLines
    .flatMap(stringToWords andThen wordsToNicks)
    .filter(_!="api")
    .distinct
    .to(LazyList)
    .par // BEGIN parallel block
  nicks.tasksupport = new ForkJoinTaskSupport(new java.util.concurrent.ForkJoinPool(numOfThreads))
  nicks.flatMap{ nick =>
      Try{ nick -> nickToColumns(nick) }
        .toEither
        .fold({ e => System.err.println(s"$nick\t: $e"); None }, Some(_) )
    }
    .seq // END parallel block
    .map{ case (col, cols) => col +: nickToUrl(col) +: tags.map(cols.getOrElse(_, "")) }
    .map(_.mkString(sep))
    .foreach(println)
}
