import java.io.InputStream
import java.util.zip.{Inflater, InflaterInputStream}

object Utils {


  def getURLBytes(
                    url: String,
                    timeout: Int = 120*1000, // com.padverb.C.networkTimeout,
                    proxy: Option[java.net.Proxy] = None,
                    maxRedir: Int = 5
                  ): Array[Byte] =
  {
    if (maxRedir<=0)
      throw new Exception(s"Too many redirections. The last url: $url")

    val u = new java.net.URL(url)
    // convert to java.net.HttpURLConnection to fail when file:/ is used
    val connection = proxy.fold(u.openConnection())(u.openConnection(_)).asInstanceOf[java.net.HttpURLConnection]
    connection.setConnectTimeout(timeout)
    connection.setReadTimeout(timeout)
    // connection.getResponseCode() match case 301 | 302 => ...
    Option(connection.getHeaderField("Location")) match {
      case None =>
        (connection.getContentEncoding() match {
          case "gzip" => new java.util.zip.GZIPInputStream(connection.getInputStream)
          case "deflate" => new InflaterInputStream(connection.getInputStream, new Inflater(true))
          case _ => connection.getInputStream
        }).readAllBytes()

      case Some(url) =>
        getURLBytes(url, timeout, proxy, maxRedir-1)
    }
  }

}
