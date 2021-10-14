import com.google.gson.annotations.SerializedName
import io.github.rybalkinsd.kohttp.ext.httpGet
import java.lang.Thread.sleep
import java.net.URLEncoder

/**
 * apple 有货提醒
 */
fun main() {
    val location = URLEncoder.encode("上海 上海")
    val phone = "MLTE3CH" //256g iphone 13 pro 远空蓝
    var errCount = 0
    var totalError = 0;
    var proxyClient = proxyClient()

    while (true) {
        val applePhoneResp: ApplePhoneResp = try {
            //请求苹果
            "https://www.apple.com.cn/shop/fulfillment-messages?pl=true&parts.0=$phone/A&location=$location".httpGet(
                proxyClient
            ).body()?.string()?.fromJson(ApplePhoneResp::class.java)!!
        } catch (e: Exception) {
            println(e.printStackTrace())
            errCount++
            if (errCount > 5) {
                proxyClient = proxyClient()
                //重新计数
                errCount = 0
                totalError++
                if (totalError > 5) {
                    println("程序错误过多，请检查程序")
                    WechatSender().sendMsg("程序错误过多，请检查程序")
                    break
                }
            }
            continue
        }
        println(applePhoneResp)
        val availableStore = applePhoneResp.body.content.pickupMessage.stores
            .map { store -> store.partsAvailability.phone }
            .filterNot { p -> p.storePickupQuote.contains(Regex(".*苏州.*|.*无锡.*|.*杭州.*|.*西湖.*|.*天一.*")) }
            .filter { p -> p.pickupDisplay == "available" }
        if (availableStore.isNotEmpty()) {
            println(availableStore)
            WechatSender().sendMsg(availableStore.joinToString(",") { s -> s.storePickupQuote })
        }
        sleep(3000)
    }
}

data class ApplePhoneResp(
    val body: Body,
)

data class Body(
    val content: Content
)

data class Content(
    val pickupMessage: PickupMessage
)

data class PickupMessage(

    val stores: List<Store>,
)

data class Store(
    val partsAvailability: PartsAvailability,

    )

data class PartsAvailability(
    @SerializedName("MLTE3CH/A")
    val phone: MLTE3CHAX
)

data class MLTE3CHAX(
    val ctoOptions: String,
    val partNumber: String,
    val pickupDisplay: String,
    val pickupSearchQuote: String,
    val pickupType: String,
    val purchaseOption: String,
    val storePickEligible: Boolean,
    val storePickupLabel: String,
    val storePickupLinkText: String,
    val storePickupProductTitle: String,
    val storePickupQuote: String,
    val storePickupQuote2_0: String,
    val storeSearchEnabled: Boolean,
    val storeSelectionEnabled: Boolean
)