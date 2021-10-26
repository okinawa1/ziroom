import io.github.rybalkinsd.kohttp.client.client
import io.github.rybalkinsd.kohttp.client.defaultHttpClient
import io.github.rybalkinsd.kohttp.ext.httpGet
import io.github.rybalkinsd.kohttp.interceptors.logging.HttpLoggingInterceptor
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

private fun getIp(): Ip? {
    return "https://ip.jiangxianli.com/api/proxy_ip".httpGet().body()?.string()?.fromJson(Ip::class.java)
}

val default = client {
    connectTimeout = TimeUnit.SECONDS.toMillis(20)
    readTimeout = TimeUnit.SECONDS.toMillis(20)
    writeTimeout = TimeUnit.SECONDS.toMillis(20)
    interceptors {
        interceptors = listOf(HttpLoggingInterceptor())
    }
}

fun proxyClient(): OkHttpClient {
    return getIp()?.let {
        println("获取到动态ip = $it")
        client {
            proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(it.data.ip, it.data.port.toInt()))
            connectTimeout = TimeUnit.SECONDS.toMillis(20)
            readTimeout = TimeUnit.SECONDS.toMillis(20)
            writeTimeout = TimeUnit.SECONDS.toMillis(20)
        }
    } ?: defaultHttpClient
}


data class Ip(
    val code: Int,
    val `data`: IpData,
    val msg: String
)

data class IpData(
    val anonymity: Int,
    val country: String,
    val created_at: String,
    val ip: String,
    val ip_address: String,
    val isp: String,
    val port: String,
    val protocol: String,
    val speed: Int,
    val unique_id: String,
    val updated_at: String,
    val validated_at: String
)