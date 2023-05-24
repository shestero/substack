import zio.*
import zio.Console.printLine
import zio.stream.{ZSink, ZStream}

import java.net.URL
import java.nio.charset.StandardCharsets.UTF_8
import java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
import java.time.format.DateTimeFormatter
import java.time.ZonedDateTime
import scala.util.Try
import scala.xml.NodeSeq

object Main extends ZIOAppDefault {
  val sep = "\t" // Output column separator (TSV)

  val numOfThreads = 16

  implicit val dateTimeOrdering: Ordering[ZonedDateTime] = _ compareTo _
  private val DATE_OUTFORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm[:ss]")
  private val RFC1123_KLUDGY = DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm[:ss] z")
  def getRFC1123TimeKludge(date: String): Try[ZonedDateTime] =
    Try { ZonedDateTime.parse(date, RFC_1123_DATE_TIME) } orElse Try { ZonedDateTime.parse(date, RFC1123_KLUDGY) }

  def charAllowedInNick(c: Char): Boolean = c.isLetterOrDigit || "-_.".contains(c)
  val stringToWords: String => Seq[String] = _.map{ c => if(charAllowedInNick(c)) c else ' ' }.split(" ")
  val wordsToNicks: Seq[String] => Seq[String] = _.collect { case s"$nick.substack.com" => nick }
  val nickToUrl: String => String = s"https://" + _ + ".substack.com/feed"

  val XMLAdjuster: String => String = // can possibly replace in CDATA
    Seq("itunes", "content", "atom", "podcast").map { t =>
      (_: String).replace(s"<$t:", s"<${t}_").replace(s"</$t:", s"</${t}_")
    }.reduce(_ compose _)

  val download: String => String = { new URL(_: String).openStream().readAllBytes() } andThen { new String(_, UTF_8) }

  val channelTags1 = Seq("itunes_name", "itunes_email")
  def channelVals1(channel: NodeSeq): Map[String, String] =
    channelTags1.flatMap(col => Try{ col -> ( channel \\ col ).text }.toOption).toMap

  val channelTags2 = Seq("title", "description")
  def channelVals2(channel: NodeSeq): Map[String, String] =
    channelTags2.flatMap(col => Try{ col -> ( channel \ col ).text }.toOption).toMap

  val itemTags: Seq[(String, (String, Seq[String] => String))] = // column name (in output), XML tag and aggregation
    Seq(
      "items"        -> ("guid", { _.size.toString }),
      "max_pubDate"  -> ("pubDate", { _.flatMap(getRFC1123TimeKludge andThen {_.toOption}).max.format(DATE_OUTFORMAT) })
    )
  def itemVals(items: NodeSeq): Map[String, String] =
    itemTags.flatMap{ case (col, (tag, agg)) => Try{ col -> agg(( items \ tag ).map(_.text)) }.toOption }.toMap

  val bodyColumns: Seq[String] = channelTags1 ++ itemTags.map(_._1) ++ channelTags2
  def rowVals(channel: NodeSeq): Map[String, String] = channelVals1(channel) ++ channelVals2(channel) ++ itemVals(channel \ "item")
  val rowBody: NodeSeq => Seq[String] = rowVals andThen { values => bodyColumns.map(col => values.getOrElse(col, "?")) }

  val nickToRowBody =
    nickToUrl andThen download andThen XMLAdjuster andThen xml.XML.loadString andThen { _ \ "channel" } andThen rowBody
  def nickToRow(nick: String): Seq[String] = nick +: nickToUrl(nick) +: nickToRowBody(nick)

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] = {
    val header = ZStream("count" +: "nick" +: "url" +: bodyColumns)
    val body =
      ZStream
        .fromIterable[(String, Int)](
          io.Source
            .stdin
            .getLines
            .flatMap(stringToWords andThen wordsToNicks)
            .filterNot(Seq("api", "apple", "cdn").contains) // reserved words - bad nicks
            .toSeq.groupMapReduce(identity)(_ => 1)(_ + _) // count nicks
            .toSeq.sortBy(-_._2) // sort by frequency (desc)
            .to(Iterable)
        )
        .mapZIOPar(numOfThreads) { (nick, count) => // or mapZIOParUnordered a bit faster
          ZIO.fromTry(Try {
            count.toString +: nickToRow(nick)
          }).fold(
            { error => java.lang.System.err.println(s"$nick\t: $error"); Iterable.empty },
            Iterable(_)
          )
        }
        .flattenIterables

    (header concat body).map(_.mkString(sep)).run(ZSink.foreach(printLine(_)))
  }
}
