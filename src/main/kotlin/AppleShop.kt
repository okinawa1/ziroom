import com.google.gson.annotations.SerializedName
import io.github.rybalkinsd.kohttp.dsl.context.HttpPostContext
import io.github.rybalkinsd.kohttp.dsl.httpPost
import io.github.rybalkinsd.kohttp.ext.httpGet
import io.github.rybalkinsd.kohttp.ext.url
import org.openqa.selenium.By
import org.openqa.selenium.PageLoadStrategy
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.devtools.v94.network.Network
import java.net.URLEncoder
import java.time.Duration
import java.util.*
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.timerTask


//是否正在购物
@Volatile
var shopping = false

@Volatile
var urlHead = "secure4"

fun main() {
    System.setProperty("webdriver.chrome.driver", "/Users/mars/chromedriver")
    val chromeOptions = ChromeOptions().apply {
        //无头的问题
//        addArguments("--window-size=1920,1080","--start-maximized","--ignore-certificate-errors","--allow-running-insecure-content", "--disable-extensions", "--headless", "--no-sandbox", "--disable-gpu", "--disable-dev-shm-usage", "--proxy-server=direct://")
        setPageLoadStrategy(PageLoadStrategy.NORMAL)
        setImplicitWaitTimeout(Duration.ofSeconds(5))
    }
    var driver: ChromeDriver? = null
    var timer: Timer? = null
    while (true) {
        try {
            driver = ChromeDriver(chromeOptions)
            var stk = ""
            driver.devTools.apply {
                createSession()
                send(Network.enable(Optional.empty(), Optional.empty(), Optional.empty()))
                addListener(Network.requestWillBeSent()) { entry ->
                    //读取cookie
                    if (entry.request.url.contains("/shop/checkoutx")) {
                        stk = entry.request.headers["x-aos-stk"]?.toString() ?: ""
                    }
                }
            }
            timer = Timer()
            //加购
//            readyShopIphone12(driver)
            readyShopIphone13Pro(driver)
            val latch = CountDownLatch(1)
            //定时刷新页面，6秒一次,检查是否有库存
            timer.schedule(timerTask {
                //如果正在下单，则跳过本次任务
                if (shopping) return@timerTask
                try {
                    val currentUrl = driver.currentUrl
                    if (currentUrl.contains("www.apple.com.cn/shop/checkout?_s=Fulfillment-init")) {
                        println("监听线下是否有货。。。。")
                        //当前页面有库存吗
                        urlHead = currentUrl.subSequence(8, 15).toString()
                        refreshAppStore(driver)
                        shopping = true
                        shopping(driver, stk)
                        //下单成功，取消任务
                        this.cancel()
                        shopping = false
                        latch.countDown()
                        WechatSender().sendMsg("苹果商店下单成功，请尽快支付")
                    } else {
                        return@timerTask
                    }
                } catch (e: NoStockRuntimeException) {
                    println("商品无库存，刷新页面。。。")
                    shopping = false
                    driver["https://$urlHead.www.apple.com.cn/shop/checkout?_s=Fulfillment-init"]
                } catch (e: Exception) {
                    shopping = false
                    println("下单失败，${e.printStackTrace()}")
                    try {
                        WechatSender().sendMsg("苹果商店下单失败，请检查程序，${e.message ?: ""}")
                        //重新下单
                        driver["https://$urlHead.www.apple.com.cn/shop/checkout?_s=Fulfillment-init"]
                    } catch (e: Exception) {
                        this.cancel()
                        //如果跳转都失败了，就重开
                        latch.countDown()
                    }
                }
            }, 0L, 6 * 1000L)
            latch.await() //下单成功才会继续执行。
        } catch (e: Exception) {
            println(" 系统异常,${e.printStackTrace()}")
            WechatSender().sendMsg("系统异常,${e.message ?: ""}")
        } finally {
            timer?.cancel()
            driver?.quit()
        }
    }
}

private fun shopping(driver: ChromeDriver, stk: String = "") {
    //找到可取货的店铺并点击
    if (stk.isBlank()) {
        throw RuntimeException("未获取到stk")
    }
    val cookie = driver.getCookie()
    println("stk = ${stk}, cookie = $cookie")
    val searchInput = "上海 上海 黄浦区"
    val city = "上海"
    val provinceCityDistrict = "上海 黄浦区"
    val district = "黄浦区"

    val store = retryReturn(50) {
        queryStoreLocator(
            stk, cookie,
            searchInput,
            city,
            city,
            provinceCityDistrict,
            district
        ).body.checkout.fulfillment.pickupTab.pickup.storeLocator.searchResults.d.retailStores
            .filterNot { retailStore ->
                listOf(
                    "Apple 苏州",
                    "Apple 无锡恒隆广场",
                    "Apple 杭州万象城",
                    "Apple 西湖",
                    "Apple 天一广场"
                ).contains(retailStore.storeName)
            }  //过滤店铺
            .find { retailStore -> retailStore.availability.storeAvailability != "目前不可取货" }
            ?: throw NoStockRuntimeException("没有可下单店铺")
    }
    println("当前有货，正在下单, $store")
    WechatSender().sendMsg("当前有货，正在下单 ${store.storeName}")

    val d = selectStoreLocator(
        stk, cookie,
        store.storeId,
        searchInput,
        city,
        city,
        provinceCityDistrict,
        district
    ).body.checkout.fulfillment.pickupTab.pickup.timeSlot.dateTimeSlots.d
    val timeSlot = d.timeSlotWindows.find { map ->
        map.values.isNotEmpty()
    }?.map { entry ->
        val first = entry.value.first()
        entry.key to first
    }?.first() ?: throw RuntimeException("没有可选时间")

    val date = d.pickUpDates.find { pickUpDate -> pickUpDate.dayOfMonth == timeSlot.first }!!
        .date
    fulfillment(
        stk,
        cookie,
        store.storeId,
        searchInput,
        city,
        provinceCityDistrict,
        district,
        timeSlot.second.checkInStart, //开始事件
        timeSlot.second.checkInEnd, //结束时间
        date, //日期
        timeSlot.second.timeSlotValue, //时间段
        timeSlot.first,
        timeSlot.second.SlotId,
        timeSlot.second.signKey
    )
    pickup(stk, cookie)
    billing(stk, cookie)
    reviewOrder(stk, cookie)
    println("下单成功")
}

/**
 * 查询有货店铺
 */
fun queryStoreLocator(
    stk: String,
    cookie: String,
    searchInput: String,
    city: String,
    state: String,
    provinceCityDistrict: String,
    district: String
): AvailableStockResp {
    val availableStockResp = (httpPost {
        url("https://$urlHead.www.apple.com.cn/shop/checkoutx?_a=search&_m=checkout.fulfillment.pickupTab.pickup.storeLocator")
        appleHeader(stk, cookie)
        body("application/x-www-form-urlencoded") {
            string("checkout.fulfillment.pickupTab.pickup.storeLocator.showAllStores=true&checkout.fulfillment.pickupTab.pickup.storeLocator.selectStore=&checkout.fulfillment.pickupTab.pickup.storeLocator.searchInput=$searchInput&checkout.fulfillment.pickupTab.pickup.storeLocator.address.stateCitySelectorForCheckout.city=$city&checkout.fulfillment.pickupTab.pickup.storeLocator.address.stateCitySelectorForCheckout.state=$state&checkout.fulfillment.pickupTab.pickup.storeLocator.address.stateCitySelectorForCheckout.provinceCityDistrict=$provinceCityDistrict&checkout.fulfillment.pickupTab.pickup.storeLocator.address.stateCitySelectorForCheckout.countryCode=CN&checkout.fulfillment.pickupTab.pickup.storeLocator.address.stateCitySelectorForCheckout.district=$district")
        }
    }.body()?.string()?.also {
        println(it)
    }?.fromJson(AvailableStockResp::class.java)
        ?: throw RuntimeException("请求查询店铺库存接口失败"))
    println("查询库存成功")
    return availableStockResp

}

fun selectStoreLocator(
    stk: String,
    cookie: String,
    selectStore: String,
    searchInput: String,
    city: String,
    state: String,
    provinceCityDistrict: String,
    district: String
): AvailableStockResp {
    val availableStockResp = (httpPost {
        url("https://$urlHead.www.apple.com.cn/shop/checkoutx?_a=search&_m=checkout.fulfillment.pickupTab.pickup.storeLocator")
        appleHeader(stk, cookie)
        body("application/x-www-form-urlencoded") {
            string("checkout.fulfillment.pickupTab.pickup.storeLocator.showAllStores=false&checkout.fulfillment.pickupTab.pickup.storeLocator.selectStore=$selectStore&checkout.fulfillment.pickupTab.pickup.storeLocator.searchInput=$searchInput&checkout.fulfillment.pickupTab.pickup.storeLocator.address.stateCitySelectorForCheckout.city=$city&checkout.fulfillment.pickupTab.pickup.storeLocator.address.stateCitySelectorForCheckout.state=$state&checkout.fulfillment.pickupTab.pickup.storeLocator.address.stateCitySelectorForCheckout.provinceCityDistrict=$provinceCityDistrict&checkout.fulfillment.pickupTab.pickup.storeLocator.address.stateCitySelectorForCheckout.countryCode=CN&checkout.fulfillment.pickupTab.pickup.storeLocator.address.stateCitySelectorForCheckout.district=$district")
        }
    }.body()?.string()?.also {
        println(it)
    }?.fromJson(AvailableStockResp::class.java)
        ?: throw RuntimeException("请求查询店铺库存接口失败"))
    println("查询库存成功")
    return availableStockResp

}

/**
 * 确认提货店铺
 */
fun fulfillment(
    stk: String,
    cookie: String,
    selectStore: String,
    searchInput: String,
    city: String,
    provinceCityDistrict: String,
    district: String,
    startTime: String,
    endTime: String,
    date: String,
    timeSlotValue: String,
    dayRadio: String,
    slotId: String,
    signKey: String
) {
    httpPost {
        url("https://$urlHead.www.apple.com.cn/shop/checkoutx?_a=continue&_m=checkout.fulfillment")
        appleHeader(stk, cookie)
        body("application/x-www-form-urlencoded") {
            string(
                "checkout.fulfillment.fulfillmentOptions.selectFulfillmentLocation=RETAIL&checkout.fulfillment.pickupTab.pickup.storeLocator.showAllStores=false&checkout.fulfillment.pickupTab.pickup.storeLocator.selectStore=$selectStore&checkout.fulfillment.pickupTab.pickup.storeLocator.searchInput=$searchInput&checkout.fulfillment.pickupTab.pickup.storeLocator.address.stateCitySelectorForCheckout.city=$city&checkout.fulfillment.pickupTab.pickup.storeLocator.address.stateCitySelectorForCheckout.state=$city&checkout.fulfillment.pickupTab.pickup.storeLocator.address.stateCitySelectorForCheckout.provinceCityDistrict=$provinceCityDistrict&checkout.fulfillment.pickupTab.pickup.storeLocator.address.stateCitySelectorForCheckout.countryCode=CN&checkout.fulfillment.pickupTab.pickup.storeLocator.address.stateCitySelectorForCheckout.district=$district&checkout.fulfillment.pickupTab.pickup.timeSlot.dateTimeSlots.startTime=$startTime&checkout.fulfillment.pickupTab.pickup.timeSlot.dateTimeSlots.displayEndTime=&checkout.fulfillment.pickupTab.pickup.timeSlot.dateTimeSlots.isRecommended=false&checkout.fulfillment.pickupTab.pickup.timeSlot.dateTimeSlots.endTime=$endTime&checkout.fulfillment.pickupTab.pickup.timeSlot.dateTimeSlots.date=$date&checkout.fulfillment.pickupTab.pickup.timeSlot.dateTimeSlots.timeSlotId=$slotId&checkout.fulfillment.pickupTab.pickup.timeSlot.dateTimeSlots.signKey=$signKey&checkout.fulfillment.pickupTab.pickup.timeSlot.dateTimeSlots.timeZone=Asia/Shanghai&checkout.fulfillment.pickupTab.pickup.timeSlot.dateTimeSlots.timeSlotValue=$timeSlotValue&checkout.fulfillment.pickupTab.pickup.timeSlot.dateTimeSlots.dayRadio=$dayRadio&checkout.fulfillment.pickupTab.pickup.timeSlot.dateTimeSlots.displayStartTime="
            )
        }
    }.body()?.string()?.also { println(it) }?.fromJson(Resp::class.java)?.check("确认提货店铺失败")
        ?: throw RuntimeException("请求接口失败")
    println("确认提货店铺成功")
    WechatSender().sendMsg("确认提货店铺成功")
}

private fun readyShopIphone12(driver: ChromeDriver) {
    driver["https://www.apple.com.cn/shop/buy-iphone/iphone-12"]
    //选机型
    driver.retryFindElement(By.xpath("/html/body/div[2]/div[5]/div[5]/div[2]/div[4]/div[2]/div[2]/div[1]/fieldset/div/div[1]/label"))
        .jsClick(driver)
    //颜色
    driver.retryFindElement(By.xpath("/html/body/div[2]/div[5]/div[5]/div[2]/div[4]/div[2]/div[2]/div[2]/fieldset/div/div[2]/label"))
        .jsClick(driver)
    //存储容量
    driver.retryFindElement(By.xpath("/html/body/div[2]/div[5]/div[5]/div[2]/div[4]/div[2]/div[2]/div[3]/fieldset/div/div[2]/label"))
        .jsClick(driver)
    //你是否有智能手机要折抵？
    driver.retryFindElement(By.xpath("/html/body/div[2]/div[5]/div[5]/div[2]/div[4]/div[2]/div[3]/div[1]/div/fieldset/div/div[1]/label/span"))
        .jsClick(driver)
    //是否要添加 AppleCare+ 服务计划？
    driver.retryFindElement(By.xpath("/html/body/div[2]/div[5]/div[5]/div[2]/div[4]/div[2]/div[4]/div/div[1]/fieldset/div[3]/div/div/ul/div/input"))
        .jsClick(driver)
    //添加
    driver.retryFindElement(By.xpath("/html/body/div[3]/div/div/div/div[1]/div[2]/div[1]/button")).jsClick(driver)
    //添加到购物车
    driver.retryFindElement(By.xpath("/html/body/div[2]/div[5]/div[5]/div[2]/div[4]/div[2]/div[5]/div[1]/div/div[3]/div[2]/form/div/span/button/span"))
        .jsClick(driver)
    Thread.sleep(2000)
    //查看购物袋
    driver.retryFindElement(By.xpath("/html/body/div[2]/div[4]/div[2]/div[2]/div/div/div[2]/div/form/button"))
        .jsClick(driver)
    //结账
    driver.retryFindElement(By.xpath("/html/body/div[3]/div[4]/div[2]/div[1]/div[3]/div/div/div/button"))
        .jsClick(driver)
    //结账
    driver.retryFindElement(By.ById("shoppingCart.actions.checkout")).jsClick(driver)
    //游客登录
    driver.retryFindElement(By.ById("signIn.guestLogin.guestLogin")).jsClick(driver)
}

private fun refreshAppStore(driver: ChromeDriver) {
    //我要取货
    driver.retryFindElement(By.ById("fulfillmentOptionButtonGroup1")).jsClick(driver)
    Thread.sleep(500)
    //显示零售店
    driver.retryFindElement(By.ByClassName("as-address-accordion")).jsClick(driver)
    //是否保存地址
    val saveLocation = driver.retryFindElement(By.id("checkout.fulfillment.pickupTab.pickup-locationConsent"))
    //记录地区
    saveLocation.retryFindElement(driver, By.xpath("../label")).jsClick(driver)
}

private fun readyShopIphone13Pro(driver: ChromeDriver) {
    driver["https://www.apple.com.cn/shop/buy-iphone/iphone-13-pro"]
    //选机型
    driver.findVisibilityElement(By.xpath("/html/body/div[2]/div[5]/div[5]/div[2]/div[4]/div[2]/div[2]/div[1]/fieldset/div/div[1]/label"))
        .jsClick(driver)
    //颜色
    driver.findVisibilityElement(By.xpath("/html/body/div[2]/div[5]/div[5]/div[2]/div[4]/div[2]/div[2]/div[2]/fieldset/div/div[1]/label"))
        .jsClick(driver)
    //存储容量
    driver.findVisibilityElement(By.xpath("/html/body/div[2]/div[5]/div[5]/div[2]/div[4]/div[2]/div[2]/div[3]/fieldset/div/div[2]/label"))
        .jsClick(driver)
    //你是否有智能手机要折抵？
    driver.findVisibilityElement(By.id("noTradeIn_label"))
        .jsClick(driver)
    //是否要添加 AppleCare+ 服务计划？ 不买
    driver.findVisibilityElement(By.id("iphonexs_ac_iup_noapplecare_label"))
        .jsClick(driver)
    //添加
//    driver.retryFindElement(By.xpath("/html/body/div[3]/div/div/div/div[1]/div[2]/div[1]/button")).jsClick(driver)
    //添加到购物车
    driver.findVisibilityElement(By.className("button-block"))
        .jsClick(driver)
    Thread.sleep(2000)
    //查看购物袋
    driver.findVisibilityElement(By.xpath("/html/body/div[2]/div[4]/div[2]/div[2]/div/div/div[2]/div/form/button"))
        .jsClick(driver)
    //结账
    driver.findVisibilityElement(By.xpath("/html/body/div[3]/div[4]/div[2]/div[1]/div[3]/div/div/div/button"))
        .jsClick(driver)
    //结账
    driver.findVisibilityElement(By.ById("shoppingCart.actions.checkout")).jsClick(driver)
    //游客登录
    driver.findVisibilityElement(By.ById("signIn.guestLogin.guestLogin")).jsClick(driver)
    //我要取货
    driver.findVisibilityElement(By.ById("fulfillmentOptionButtonGroup1")).jsClick(driver)
}

/**
 * 填写人的信息
 */
private fun pickup(stk: String, cookie: String) {
    val lastName = "袁"
    val firstName = "华健"
    val emailAddress = "13093687239@163.com"
    val fullDaytimePhone = "13093687239"
    val nationalId = "342601199607122419"

    retry(10) {
        httpPost {
            url("https://$urlHead.www.apple.com.cn/shop/checkoutx?_a=continue&_m=checkout.pickupContact")
            appleHeader(stk, cookie)
            body("application/x-www-form-urlencoded") {
                string("checkout.pickupContact.selfPickupContact.selfContact.address.lastName=${lastName}&checkout.pickupContact.selfPickupContact.selfContact.address.firstName=${firstName}&checkout.pickupContact.selfPickupContact.selfContact.address.emailAddress=${emailAddress}&checkout.pickupContact.selfPickupContact.selfContact.address.fullDaytimePhone=${fullDaytimePhone}&checkout.pickupContact.eFapiaoSelector.selectFapiao=none&checkout.pickupContact.nationalID.nationalId=${nationalId}")
            }
        }.body()?.string()?.also { println(it) }?.fromJson(Resp::class.java)?.check("填写下单信息有误")
            ?: throw RuntimeException("请求填写信息接口失败")
    }

    println("填写下单信息成功")
    WechatSender().sendMsg("填写下单信息成功")
}

/**
 * 确认支付方式
 */
private fun billing(stk: String, cookie: String) {
    val selectBillingOption = "installments0001321713" //招行分期
    val selectInstallmentOption = "24"
//    val selectBillingOption = "installments0001243254" //支付宝分期
//    val selectInstallmentOption = "12"
    retry(10) {
        httpPost {
            url("https://$urlHead.www.apple.com.cn/shop/checkoutx?_a=selectBillingOptionAction&_m=checkout.billing.billingOptions")
            appleHeader(stk, cookie)
            body("application/x-www-form-urlencoded") {
                //支付宝分期12期
//            string("checkout.billing.billingOptions.selectBillingOption=installments0001321713&checkout.billing.billingOptions.selectedBillingOptions.installments.installmentOptions.selectInstallmentOption=12&checkout.locationConsent.locationConsent=false")
                //招商银行24期
                string("checkout.billing.billingOptions.selectBillingOption=$selectBillingOption&checkout.billing.billingOptions.selectedBillingOptions.installments.installmentOptions.selectInstallmentOption=$selectInstallmentOption&checkout.locationConsent.locationConsent=false")

            }
        }.body()?.string()?.also { println(it) }?.fromJson(Resp::class.java)?.check("选择支付方式有误")
            ?: throw RuntimeException("请求billingOptions接口失败")
    }
    println("请求billingOptions成功")
    retry(10) {
        httpPost {
            url("https://$urlHead.www.apple.com.cn/shop/checkoutx?_a=continue&_m=checkout.billing")
            appleHeader(stk, cookie)
            body("application/x-www-form-urlencoded") {
                string("checkout.billing.billingOptions.selectBillingOption=$selectBillingOption&checkout.billing.billingOptions.selectedBillingOptions.installments.installmentOptions.selectInstallmentOption=$selectInstallmentOption")
            }
        }.body()?.string()?.also { println(it) }?.fromJson(Resp::class.java)?.check("选择支付方式有误")
            ?: throw RuntimeException("选择付款方式接口失败")
    }
    println("选择付款方式成功")
    WechatSender().sendMsg("选择付款方式成功")
}

/**
 * 确认下单
 */
private fun reviewOrder(stk: String, cookie: String) {
    httpPost {
        url("https://$urlHead.www.apple.com.cn/shop/checkoutx?_a=continue&_m=checkout.review.placeOrder")
        appleHeader(stk, cookie)
        body("application/x-www-form-urlencoded") {
            string("")
        }
    }.body()?.string()?.also { println(it) }?.fromJson(Resp::class.java)?.checkOrderSuccess()
        ?: throw RuntimeException("请求确认下单接口失败")
    println("确认下单成功")
    WechatSender().sendMsg("确认下单成功")
}

fun HttpPostContext.appleHeader(
    stk: String,
    cookie: String
) {
    header {
        "Connection" to "keep-alive"
        "sec-ch-ua" to "\"Google Chrome\";v=\"95\", \"Chromium\";v=\"95\", \";Not A Brand\";v=\"99\""
        "authority" to "secure4.www.apple.com.cn"
        "syntax" to "graviton"
        "sec-ch-ua-mobile" to "?0"
        "User-Agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/95.0.4638.54 Safari/537.36"
        "content-type" to "application/x-www-form-urlencoded"
        "x-aos-stk" to stk
        "x-aos-model-page" to "checkoutPage"
        "x-requested-with" to "XMLHttpRequest"
        "modelversion" to "v2"
        "sec-ch-ua-platform" to "\"macOS\""
        "accept" to "*/*"
        "origin" to "https://secure4.www.apple.com.cn"
        "sec-fetch-site" to "same-origin"
        "sec-fetch-mode" to "cors"
        "sec-fetch-dest" to "empty"
        "cookie" to cookie
    }
}

private fun monitorStock(apply: () -> Unit) {
    val location = URLEncoder.encode("上海 上海")
    val phone = "MLTE3CH" //256g iphone 13 pro 远空蓝
    var errCount = 0
    var proxyClient = proxyClient()

    while (true) {
        val applePhoneResp: ApplePhoneResp = try {
            //请求苹果
            "https://www.apple.com.cn/shop/fulfillment-messages?pl=true&parts.0=$phone/A&location=$location".httpGet(
                proxyClient
            ).body()?.string()?.also { println(it) }?.fromJson(ApplePhoneResp::class.java)!!
        } catch (e: Exception) {
            println(e.printStackTrace())
            Thread.sleep(3000)
            errCount++
            if (errCount > 5) {
                proxyClient = proxyClient()
                //重新计数
                errCount = 0
            }
            continue
        }
        val availableStore = applePhoneResp.body.content.pickupMessage.stores
            .map { store -> store.partsAvailability.phone }
            .filterNot { p -> p.storePickupQuote.contains(Regex(".*苏州.*|.*无锡.*|.*杭州.*|.*西湖.*|.*天一.*")) }
            .filter { p -> p.pickupDisplay == "available" }
        if (availableStore.isNotEmpty()) {
            println(availableStore)
            WechatSender().sendMsg(availableStore.joinToString(",") { s -> s.storePickupQuote })
            apply.invoke()
        }
        Thread.sleep(3000)
    }
}

data class Resp(
    val head: Head,
) {
    fun check(str: String) {
        if (this.head.status == 302 && this.head.data.url.contains("sorry")) {
            throw RuntimeException(str)
        } else if (!listOf(302, 200).contains(this.head.status)) {
            throw RuntimeException(str)
        }
    }

    fun checkOrderSuccess() {
        if (this.head.status != 302 && this.head.data.url != "/shop/checkout/status") {
            throw RuntimeException("下单失败")
        }
    }
}

data class Head(
    val status: Int,
    val data: AppleData

)

data class ApplePhoneResp(
    val body: Body,
)

data class AppleData(
    val url: String
)

data class Body(
    val content: Content
)

data class Content(
    val pickupMessage: PickupMessage1
)

data class PickupMessage1(

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


class NoStockRuntimeException(str: String) : RuntimeException(str)


data class AvailableStockResp(
    val body: AvailableStockBody,
    val head: AvailableStockHead
)

data class AvailableStockBody(
    val checkout: Checkout,
)

data class AvailableStockHead(
    val status: Int
)

data class Checkout(
    val fulfillment: Fulfillment
)

data class Fulfillment(
    val a: AXXXXX,
    val b: BXXXXXX,
    val c: List<String>,
    val d: DXXXXXXX,
    val footnotes: Footnotes,
    val fulfillmentOptions: FulfillmentOptions,
    val pickupTab: PickupTab,
    val t: TXXXXXXXX
)

data class A(
    val edit: Edit
)

data class B(
    val edit: EditX
)

data class D(
    val editLabel: String,
    val editLabelA11y: String,
    val label: String
)

data class P(
    val enabled: Boolean
)

data class T(
    val change: Change
)

data class Edit(
    val module: String,
    val submit: Boolean,
    val url: String,
    val validate: Boolean
)

data class EditX(
    val events: List<Event>,
    val id: String
)

data class Event(
    val `do`: String,
    val metric: String,
    val on: String
)

data class Change(
    val microEvents: List<MicroEvent>
)

data class MicroEvent(
    val feature: String,
    val key: String,
    val slot: String,
    val value: String
)

data class AX(
    val edit: EditXX
)

data class BX(
    val edit: EditXXX
)

data class DXX(
    val hideDetails: Boolean,
    val label: String
)

data class PickupSummary(
    val a: AXX,
    val b: BXX,
    val d: DXXX,
    val t: TX,
    val view: String
)

data class TXX(
    val change: ChangeXX
)

data class EditXX(
    val module: String,
    val submit: Boolean,
    val url: String,
    val validate: Boolean
)

data class EditXXX(
    val events: List<EventX>,
    val id: String
)

data class EventX(
    val `do`: String,
    val metric: String,
    val on: String
)

data class AXX(
    val edit: EditXXXX
)

data class BXX(
    val edit: EditXXXXX
)

data class DXXX(
    val editPickupLabel: String,
    val editPickupLabelA11y: String,
    val id: String,
    val pickupLabel: String,
    val retailDeliveryDates: String,
    val store_name: String,
    val timeSlotInfo: String
)

data class TX(
    val change: ChangeX
)

data class EditXXXX(
    val module: String,
    val submit: Boolean,
    val url: String,
    val validate: Boolean
)

data class EditXXXXX(
    val events: List<EventXX>,
    val id: String
)

data class EventXX(
    val `do`: String,
    val metric: String,
    val on: String
)

data class ChangeX(
    val microEvents: List<MicroEventX>
)

data class MicroEventX(
    val feature: String,
    val key: String,
    val slot: String,
    val value: String
)

data class ChangeXX(
    val microEvents: List<MicroEventXX>
)

data class MicroEventXX(
    val feature: String,
    val key: String,
    val slot: String,
    val value: String
)

data class AXXX(
    val edit: EditXXXXXX
)

data class BXXX(
    val edit: EditXXXXXXX
)

data class DXXXX(
    val editLabel: String,
    val editLabelA11y: String,
    val fapiaoLabel: String,
    val fapiaoType: String,
    val label: String,
    val pickupContactLabel: String
)

data class PX(
    val enabled: Boolean
)

data class TXXX(
    val change: ChangeXXX
)

data class EditXXXXXX(
    val module: String,
    val submit: Boolean,
    val url: String,
    val validate: Boolean
)

data class EditXXXXXXX(
    val events: List<EventXXX>,
    val id: String
)

data class EventXXX(
    val `do`: String,
    val metric: String,
    val on: String
)

data class ChangeXXX(
    val microEvents: List<MicroEventXXX>
)

data class MicroEventXXX(
    val feature: String,
    val key: String,
    val slot: String,
    val value: String
)

data class AXXXX(
    val edit: EditXXXXXXXX
)

data class BXXXX(
    val edit: EditXXXXXXXXX
)

data class DXXXXX(
    val editLabel: String,
    val editLabelA11y: String,
    val label: String
)

data class PXX(
    val enabled: Boolean
)

data class TXXXX(
    val change: ChangeXXXX
)

data class EditXXXXXXXX(
    val module: String,
    val submit: Boolean,
    val url: String,
    val validate: Boolean
)

data class EditXXXXXXXXX(
    val events: List<EventXXXX>,
    val id: String
)

data class EventXXXX(
    val `do`: String,
    val metric: String,
    val on: String
)

data class ChangeXXXX(
    val microEvents: List<MicroEventXXXX>
)

data class MicroEventXXXX(
    val feature: String,
    val key: String,
    val slot: String,
    val value: String
)

data class BXXXXX(
    val editBagLink: EditBagLink
)

data class DXXXXXX(
    val continueBagLink: String,
    val editBagLink: Any,
    val editLabel: String,
    val editLabelA11y: String,
    val items: String,
    val pickupSummary: Boolean,
    val showBuacMessage: Boolean,
    val showEditBag: Boolean,
    val showTaxInclusivePrices: Boolean,
    val title: String,
    val total: String,
    val totalNoShip: String
)

data class TXXXXX(
    val editBagCompanionBar: EditBagCompanionBar
)

data class EditBagLink(
    val events: List<EventXXXXX>,
    val id: String,
    val key: String
)

data class EventXXXXX(
    val `do`: List<Do>,
    val metric: String,
    val on: String
)

data class Do(
    val `set`: String,
    val to: Boolean
)

data class EditBagCompanionBar(
    val microEvents: List<MicroEventXXXXX>
)

data class MicroEventXXXXX(
    val feature: String,
    val key: String,
    val slot: String,
    val value: String
)

data class AXXXXX(
    val `continue`: Continue
)

data class BXXXXXX(
    val `continue`: ContinueX
)

data class DXXXXXXX(
    val continueLabel: String,
    val enableContinue: Boolean,
    val shouldShowChat: Boolean
)

data class Footnotes(
    val d: DXXXXXXXX
)

data class FulfillmentOptions(
    val a: AXXXXXX,
    val b: BXXXXXXX,
    val d: DXXXXXXXXX,
    val p: PXXX
)

data class PickupTab(
    val b: BXXXXXXXX,
    val c: List<String>,
    val p: PXXXX,
    val pickup: Pickup,
    val t: TXXXXXXX
)

data class TXXXXXXXX(
    val continueAction: ContinueAction,
    val emptySearchError: EmptySearchError,
    val timeSlotNotSelectedError: TimeSlotNotSelectedError
)

data class Continue(
    val submit: Boolean,
    val url: String
)

data class ContinueX(
    val events: List<EventXXXXXX>,
    val id: String,
    val name: String
)

data class EventXXXXXX(
    val condition: Condition,
    val `do`: String,
    val metric: String,
    val on: String
)

data class Condition(
    val args: List<Any>,
    val op: String
)

data class DXXXXXXXX(
    val isFlagshipEnable: Boolean,
    val isGeoLocationEnabled: Boolean
)

data class AXXXXXX(
    val selectFulfillmentLocationAction: SelectFulfillmentLocationAction
)

data class BXXXXXXX(
    val selectFulfillmentLocation: SelectFulfillmentLocation
)

data class DXXXXXXXXX(
    val options: List<Option>,
    val selectFulfillmentLocation: String
)

data class PXXX(
    val enabled: Boolean
)

data class SelectFulfillmentLocationAction(
    val submit: Boolean,
    val url: String
)

data class SelectFulfillmentLocation(
    val events: List<EventXXXXXXX>,
    val id: String,
    val key: String,
    val submit: Boolean
)

data class EventXXXXXXX(
    val `do`: String,
    val on: String
)

data class Option(
    val className: String,
    val name: String,
    val title: String,
    val value: String
)

data class BXXXXXXXX(
    val pickupTab: PickupTabX
)

data class PXXXX(
    val enabled: Boolean
)

data class Pickup(
    val c: List<String>,
    val d: DXXXXXXXXXX,
    val storeLocator: StoreLocator,
    val timeSlot: TimeSlot
)

data class TXXXXXXX(
    val pickupTab: PickupTabXX
)

data class PickupTabX(
    val events: List<EventXXXXXXXX>,
    val id: String,
    val name: String
)

data class EventXXXXXXXX(
    val metric: String,
    val on: String
)

data class DXXXXXXXXXX(
    val header: String
)

data class StoreLocator(
    val searchResults: SearchResults
)

data class TimeSlot(
    val c: List<String>,
    val d: DXXXXXXXXXXXXXX,
    val dateTimeSlots: DateTimeSlots
)

data class DXXXXXXXXXXX(
    val alt: String,
    val canShareCartItem: Boolean,
    val dataScaleParams1: String,
    val hasAppleCareInstallment: Boolean,
    val hasCloudSubscription: Boolean,
    val hasMusicSubscription: Boolean,
    val height: Int,
    val id: String,
    val isBlockProductDetails: Boolean,
    val name: String,
    val productEvar1: String,
    val productFamilyDisplayName: String,
    val productMetricId: String,
    val productUrl: String,
    val showQuantity: Boolean,
    val showSingleHeader: Boolean,
    val src: String,
    val totalPrice: String,
    val unitPrice: String,
    val viewDetailsLinkEnabled: Boolean,
    val width: Int
)

data class F(
    val `abstract`: String
)

data class ItemDelivery(
    val a: AXXXXXXX,
    val d: DXXXXXXXXXXXX,
    val f: FX
)

data class AXXXXXXX(
    val storeLocatorView: StoreLocatorView
)

data class DXXXXXXXXXXXX(
    val id: String,
    val pickup: PickupX,
    val ships: Ships,
    val sthEnabled: Boolean
)

data class FX(
    val `abstract`: String
)

data class StoreLocatorView(
    val url: String
)

data class PickupX(
    val dudeAttributeQuote: String,
    val dudeAttributeStore: String,
    val enableStoreSelectorLink: Boolean,
    val hasRemoveGiftMessageLink: Boolean,
    val isPickupStoreAvailable: Boolean,
    val pickupLabel: String,
    val quote: String,
    val storeSelectorLinkText: String,
    val storeSelectorLinkTextA11y: String,
    val warmStateForPickup: Boolean
)

data class Ships(
    val resolvedShipLabel: Any,
    val shipMethodQuote: List<ShipMethodQuote>,
    val showShipLabel: Boolean
)

data class ShipMethodQuote(
    val date: String,
    val postfix: String,
    val tooltip: String
)

data class SearchResults(
    val d: DXXXXXXXXXXXXX
)

data class DXXXXXXXXXXXXX(
    val retailStores: List<RetailStore>
)

data class RetailStore(
    val availability: Availability,
    val distance: String,
    val imageUrl: String,
    val lockerBoxDetail: Any,
    val personalSetupDetail: String,
    val phoneNumber: String,
    val pickupMessageCommonLabel: String,
    val pickupMessages: List<PickupMessage>,
    val pupAuthorizedLabel: Any,
    val retailAddress: RetailAddress,
    val specialHours: List<Any>,
    val specialHoursLabel: String,
    val storeDisabled: Boolean,
    val storeHours: List<StoreHour>,
    val storeId: String,
    val storeMessages: String,
    val storeName: String
)

data class Availability(
    val availableNowForAllLines: Boolean,
    val lineItemAvailability: List<LineItemAvailability>,
    val pickupMethod: String,
    val sameAvailabilityForAllLineItems: Boolean,
    val storeAvailability: String,
    val waitTimeMessage: String,
    val waitTimeMessageHeader: String
)

data class PickupMessage(
    val description: String,
    val pickupMethod: String
)

data class RetailAddress(
    val city: String,
    val district: String,
    val layout: List<Layout>,
    val mode: String,
    val street: String
)

data class StoreHour(
    val storeDays: String,
    val storeTimings: String
)

data class LineItemAvailability(
    val availabilityQuote: String,
    val availableNowForLine: Boolean,
    val partId: String,
    val partName: String
)

data class Layout(
    val fields: List<List<Field>>
)

data class Field(
    val label: String,
    val name: String,
    val type: String
)

data class DXXXXXXXXXXXXXX(
    val enableTimeSlotWindows: Boolean,
    val storeId: String
)

data class DateTimeSlots(
    val b: BXXXXXXXXX,
    val d: DXXXXXXXXXXXXXXX,
    val t: TXXXXXX
)

data class BXXXXXXXXX(
    val date: Date,
    val dateTimeSlots: DateTimeSlotsX,
    val dayRadio: DayRadio,
    val displayEndTime: DisplayEndTime,
    val displayStartTime: DisplayStartTime,
    val endTime: EndTime,
    val isRecommended: IsRecommended,
    val signKey: SignKey,
    val startTime: StartTime,
    val timeSlotId: TimeSlotId,
    val timeSlotValue: TimeSlotValue,
    val timeZone: TimeZone
)

data class DXXXXXXXXXXXXXXX(
    val availableWindows: String,
    val date: String,
    val dayRadio: String,
    val displayEndTime: String,
    val displayStartTime: String,
    val endTime: String,
    val firstAvailableDate: String,
    val isRecommended: Boolean,
    val pickUpDates: List<PickUpDate>,
    val selectADay: String,
    val signKey: String,
    val startTime: String,
    val storeSelected: String,
    val timeSlotId: String,
    val timeSlotInstruction: String,
    val timeSlotValue: String,
    val timeSlotWindows: List<Map<String, List<TimeSlotItem>>>,
    val timeZone: String
)

data class TXXXXXX(
    val bartDisplayed: BartDisplayed,
    val dayChanged: DayChanged,
    val firstDateSelected: FirstDateSelected,
    val timeSlotSelected: TimeSlotSelected
)

data class Date(
    val id: String,
    val key: String,
    val submit: Boolean
)

data class DateTimeSlotsX(
    val events: List<EventXXXXXXXXX>,
    val id: String
)

data class DayRadio(
    val events: List<EventXXXXXXXXXX>,
    val id: String,
    val key: String,
    val submit: Boolean
)

data class DisplayEndTime(
    val id: String,
    val key: String,
    val submit: Boolean
)

data class DisplayStartTime(
    val id: String,
    val key: String,
    val submit: Boolean
)

data class EndTime(
    val id: String,
    val key: String,
    val submit: Boolean
)

data class IsRecommended(
    val id: String,
    val key: String,
    val submit: Boolean
)

data class SignKey(
    val id: String,
    val key: String,
    val submit: Boolean
)

data class StartTime(
    val id: String,
    val key: String,
    val submit: Boolean
)

data class TimeSlotId(
    val id: String,
    val key: String,
    val submit: Boolean
)

data class TimeSlotValue(
    val events: List<EventXXXXXXXXXXX>,
    val id: String,
    val key: String,
    val p: PXXXXX,
    val submit: Boolean
)

data class TimeZone(
    val id: String,
    val key: String,
    val submit: Boolean
)

data class EventXXXXXXXXX(
    val metric: String,
    val on: String
)

data class EventXXXXXXXXXX(
    val metric: String,
    val on: String
)

data class EventXXXXXXXXXXX(
    val condition: ConditionX,
    val metric: String,
    val on: String
)

data class PXXXXX(
    val valid: Valid
)

data class ConditionX(
    val args: List<Any>,
    val op: String
)

data class Valid(
    val `if`: If,
    val then: Then
)

data class If(
    val args: List<Any>,
    val op: String
)

data class Then(
    val error: String
)

data class PickUpDate(
    val date: String,
    val dayOfMonth: String,
    val dayOfWeek: String,
    val fullDayOfWeek: String,
    val fullPickupDateMessage: String,
    val month: String,
    val monthValue: String,
    val pickupDateMessage: String
)

data class TimeSlotItem(
    val Label: String,
    val SlotId: String,
    val checkInEnd: String,
    val checkInStart: String,
    val signKey: String,
    val timeSlotValue: String,
    val timeZone: String
)

data class BartDisplayed(
    val microEvents: List<MicroEventXXXXXX>
)

data class DayChanged(
    val microEvents: List<MicroEventXXXXXXX>
)

data class FirstDateSelected(
    val microEvents: List<MicroEventXXXXXXXX>
)

data class TimeSlotSelected(
    val microEvents: List<MicroEventXXXXXXXXX>
)

data class MicroEventXXXXXX(
    val feature: String,
    val key: String,
    val slot: String,
    val value: String
)

data class MicroEventXXXXXXX(
    val feature: String,
    val key: String,
    val slot: String,
    val value: String
)

data class MicroEventXXXXXXXX(
    val feature: Feature,
    val key: String,
    val slot: String,
    val value: Value
)

data class Feature(
    val `get`: String
)

data class Value(
    val `get`: String
)

data class MicroEventXXXXXXXXX(
    val feature: String,
    val key: String,
    val slot: String,
    val value: String
)

data class PickupTabXX(
    val microEvents: List<MicroEventXXXXXXXXXX>
)

data class MicroEventXXXXXXXXXX(
    val key: String,
    val slot: String,
    val value: String
)

data class ContinueAction(
    val macroEvents: MacroEvents,
    val microEvents: List<MicroEventXXXXXXXXXXX>
)

data class EmptySearchError(
    val microEvents: List<MicroEventXXXXXXXXXXXX>
)

data class TimeSlotNotSelectedError(
    val microEvents: List<MicroEventXXXXXXXXXXXXX>
)

data class MacroEvents(
    val args: List<Any>,
    val op: String
)

data class MicroEventXXXXXXXXXXX(
    val key: String,
    val slot: String,
    val value: String
)

data class MicroEventXXXXXXXXXXXX(
    val key: String,
    val slot: String,
    val value: String
)

data class MicroEventXXXXXXXXXXXXX(
    val key: String,
    val slot: String,
    val value: String
)
