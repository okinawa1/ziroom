import Constants.gson
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
    val resp =
        "https://www.apple.com.cn/shop/fulfillment-messages?pl=true&parts.0=$phone/A&location=$location".httpGet()
    val applePhoneResp = gson.fromJson(resp.body()?.string(), ApplePhoneResp::class.java)
    while (true) {
        val availableStore =
            applePhoneResp.body.content.pickupMessage.stores.map { store -> store.partsAvailability.phone }
                .filterNot { p -> p.pickupDisplay == "unavailable" }
        println(availableStore)
        if (availableStore.isNotEmpty()) {
            WechatSender().sendMsg(availableStore.joinToString(",") { s -> s.storePickupQuote })
        }
        sleep(500)
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