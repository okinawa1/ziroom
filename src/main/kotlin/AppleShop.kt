import io.github.rybalkinsd.kohttp.dsl.context.HttpGetContext
import io.github.rybalkinsd.kohttp.dsl.context.HttpPostContext
import io.github.rybalkinsd.kohttp.dsl.httpGet
import io.github.rybalkinsd.kohttp.dsl.httpPost
import io.github.rybalkinsd.kohttp.ext.httpGet
import io.github.rybalkinsd.kohttp.ext.url
import okhttp3.Headers
import org.openqa.selenium.By
import org.openqa.selenium.Cookie
import org.openqa.selenium.PageLoadStrategy
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.devtools.v95.network.Network
import org.openqa.selenium.net.HostIdentifier
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import java.net.URLEncoder
import java.time.Duration
import java.util.*
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.timerTask

val shanghaiShop = listOf("R390", "R359", "R401", "R389", "R683", "R581", "R705")
//val shanghaiShop = listOf("R401")
//val shanghaiShop = listOf("R390", "R359")
//val shanghaiShop = listOf("R390")

val searchInput = "上海 上海 黄浦区"
val city = "上海"
val provinceCityDistrict = "上海 黄浦区"
val district = "黄浦区"

val chromeOptions = ChromeOptions().apply {
    //无头的问题
    setHeadless(true)
    addArguments(
        "--no-sandbox",
        "--disable-gpu",
        "--disable-images",
        "window-size=1200x600",
        "user-agent=Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.198 Safari/537.36"
    )
    addArguments("--incognito")
    addArguments("--disable-blink-features=AutomationControlled")
    setExperimentalOption("excludeSwitches", listOf("enable-automation"))
    setExperimentalOption("useAutomationExtension", false)
    setPageLoadStrategy(PageLoadStrategy.NORMAL)
    setImplicitWaitTimeout(Duration.ofSeconds(10))
}

fun main() {
    System.setProperty("webdriver.chrome.driver", "/Users/mars/chromedriver")
//    System.setProperty("webdriver.chrome.driver", "/opt/bin/chromedriver")
    HostIdentifier.getHostAddress()
    while (true) {
//        val phone = "MLTE3CH/A" //远峰蓝
//        val phone = "MJQ73CH/A" //iphone12
        val phone = "MLTC3CH/A" //银色
        val task = MonitorStockTask(phone, "上海 上海 黄浦区", shanghaiShop.size)
        val thread = Thread(task, task.javaClass.simpleName)
        thread.start()
        val latch = CountDownLatch(shanghaiShop.size)
        println("开始抢购")
        shanghaiShop.forEach { store ->
            Thread { goToShopping(store, latch, task) }.start()
        }
        latch.await()
        thread.interrupt()
        println("退出抢购")
        Thread.sleep(2000)
    }
}

private fun goToShopping(
    store: String,
    globalLatch: CountDownLatch,
    task: MonitorStockTask
) {
    while (true) {
        var timer: Timer? = null
        var stk = ""
        val driver = ChromeDriver(chromeOptions)
//            .apply {
//                executeCdpCommand("Page.addScriptToEvaluateOnNewDocument", mapOf("source" to js))
//            }

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
        //是否正在购物
        val latch = CountDownLatch(1)
        try {
            //加购
//            readyShopIphone12(driver)
//            readyShopIphone13Pro(driver)
            readyShopIphone13ProYingse(driver)
            refreshAppStore(driver, store)
            //当前页面有库存吗
            val urlHead = driver.currentUrl.subSequence(8, 15).toString()

            //定时刷新页面，180秒一次,检查是否有库存
            timer = Timer().apply {
                schedule(
                    timerTask {
                        driver.navigate().refresh()
                        Thread.sleep(2000)
                        if (driver.currentUrl.contains("session_expired") ||
                            driver.currentUrl.contains("message_generic")
                        ) {
                            //如果session过期就重开
                            latch.countDown()
                            return@timerTask
                        }
                    },
                    60 * 3000L,
                    60 * 3000L
                )
            }
            val cookies = driver.getCookies()
            //加购
            shopping(stk, cookies, store, urlHead)
            //注册下单
            task.register { s ->
                //只有有货的时候，才能往下走。
                if (store == s) {
                    var start = System.currentTimeMillis()
                    try {
                        pickup(stk, cookies, urlHead)
                        billing(stk, cookies, urlHead)
                        reviewOrder(stk, cookies, urlHead)
                        driver["https://$urlHead.www.apple.com.cn/shop/checkout/status"]
                        Thread.sleep(5000)
                        if (!driver.currentUrl.contains("/shop/checkout/thankyou")) {
                            start -= 5000 //减去睡眠的时间
                            throw RuntimeException("下单失败")
                        }
                        val end = System.currentTimeMillis()
                        WechatSender().sendMsg("苹果商店下单成功，请尽快支付, 耗时${(end - start) / 1000 - 5}s")
                    } catch (e: Exception) {
                        e.printStackTrace()
                        val end = System.currentTimeMillis()
                        WechatSender().sendMsg("苹果商店下单失败，耗时 ${(end - start) / 1000}s, 请检查程序${e.message}")
                    }
                }
                latch.countDown()
            } //这里不会抛异常
            latch.await() //下单成功才会继续执行。
            globalLatch.countDown() //执行完就全局countDown
            break
        } catch (e: Exception) {
            println("系统异常")
            e.printStackTrace()
        } finally {
            println("退出购买流程, $store")
            timer?.cancel()
            driver.quit()
        }
    }
}

private fun shopping(
    stk: String = "",
    cookies: MutableMap<String, String>,
    store: String,
    urlHead: String
) {
    val d = selectStoreLocator(
        stk,
        cookies,
        store,
        searchInput,
        city,
        city,
        provinceCityDistrict,
        district,
        urlHead
    ).body.checkout.fulfillment.pickupTab.pickup.timeSlot.dateTimeSlots.d
    val timeSlot = d.timeSlotWindows.find { map ->
        map.values.flatten().isNotEmpty()
    }?.map { entry ->
        val last = entry.value.last()
        entry.key to last
    }?.first() ?: throw RuntimeException("没有可选时间")

    val date = d.pickUpDates.find { pickUpDate -> pickUpDate.dayOfMonth == timeSlot.first }!!
        .date
    fulfillment(
        stk,
        cookies,
        store,
        searchInput,
        city,
        provinceCityDistrict,
        district,
        timeSlot.second.checkInStart, //开始事件
        timeSlot.second.checkInEnd, //结束时间
        date, //日期
        timeSlot.second.timeSlotValue, //时间段
        timeSlot.first,//日
        timeSlot.second.SlotId,
        timeSlot.second.signKey,
        urlHead
    )
}

fun selectStoreLocator(
    stk: String,
    cookies: MutableMap<String, String>,
    selectStore: String,
    searchInput: String,
    city: String,
    state: String,
    provinceCityDistrict: String,
    district: String,
    urlHead: String
): AvailableStockResp {
    val availableStockResp = retryReturn(60) {
        httpPost(default) {
            url("https://$urlHead.www.apple.com.cn/shop/checkoutx?_a=select&_m=checkout.fulfillment.pickupTab.pickup.storeLocator")
            appleHeader(stk, toCookies(cookies), urlHead)
            body("application/x-www-form-urlencoded") {
                string("checkout.fulfillment.pickupTab.pickup.storeLocator.showAllStores=false&checkout.fulfillment.pickupTab.pickup.storeLocator.selectStore=$selectStore&checkout.fulfillment.pickupTab.pickup.storeLocator.searchInput=$searchInput&checkout.fulfillment.pickupTab.pickup.storeLocator.address.stateCitySelectorForCheckout.city=$city&checkout.fulfillment.pickupTab.pickup.storeLocator.address.stateCitySelectorForCheckout.state=$state&checkout.fulfillment.pickupTab.pickup.storeLocator.address.stateCitySelectorForCheckout.provinceCityDistrict=$provinceCityDistrict&checkout.fulfillment.pickupTab.pickup.storeLocator.address.stateCitySelectorForCheckout.countryCode=CN&checkout.fulfillment.pickupTab.pickup.storeLocator.address.stateCitySelectorForCheckout.district=$district")
            }
        }.also {
            updateCookies(it.headers(), cookies)
        }.body()?.string()?.also {
            println(it)
        }?.fromJson(AvailableStockResp::class.java)
            ?: throw RuntimeException("请求查询店铺库存接口失败")
    }
    println("查询库存成功")
    return availableStockResp

}

fun updateCookies(headers: Headers, cookies: MutableMap<String, String>) {
    //更新cookie
    headers.values("Set-Cookie").map { s ->
        val split = s.split(";")[0].split("=")
        split[0] to split[1]
    }.forEach { pair ->
        cookies[pair.first] = pair.second
    }
}

/**
 * 确认提货店铺
 */
fun fulfillment(
    stk: String,
    cookies: MutableMap<String, String>,
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
    signKey: String,
    urlHead: String
) {
    retry(60) {
        httpPost(default) {
            url("https://$urlHead.www.apple.com.cn/shop/checkoutx?_a=continue&_m=checkout.fulfillment")
            appleHeader(stk, toCookies(cookies), urlHead)
            body("application/x-www-form-urlencoded") {
                string(
                    "checkout.fulfillment.fulfillmentOptions.selectFulfillmentLocation=RETAIL&checkout.fulfillment.pickupTab.pickup.storeLocator.showAllStores=false&checkout.fulfillment.pickupTab.pickup.storeLocator.selectStore=$selectStore&checkout.fulfillment.pickupTab.pickup.storeLocator.searchInput=$searchInput&checkout.fulfillment.pickupTab.pickup.storeLocator.address.stateCitySelectorForCheckout.city=$city&checkout.fulfillment.pickupTab.pickup.storeLocator.address.stateCitySelectorForCheckout.state=$city&checkout.fulfillment.pickupTab.pickup.storeLocator.address.stateCitySelectorForCheckout.provinceCityDistrict=$provinceCityDistrict&checkout.fulfillment.pickupTab.pickup.storeLocator.address.stateCitySelectorForCheckout.countryCode=CN&checkout.fulfillment.pickupTab.pickup.storeLocator.address.stateCitySelectorForCheckout.district=$district&checkout.fulfillment.pickupTab.pickup.timeSlot.dateTimeSlots.startTime=$startTime&checkout.fulfillment.pickupTab.pickup.timeSlot.dateTimeSlots.displayEndTime=&checkout.fulfillment.pickupTab.pickup.timeSlot.dateTimeSlots.isRecommended=false&checkout.fulfillment.pickupTab.pickup.timeSlot.dateTimeSlots.endTime=$endTime&checkout.fulfillment.pickupTab.pickup.timeSlot.dateTimeSlots.date=$date&checkout.fulfillment.pickupTab.pickup.timeSlot.dateTimeSlots.timeSlotId=$slotId&checkout.fulfillment.pickupTab.pickup.timeSlot.dateTimeSlots.signKey=$signKey&checkout.fulfillment.pickupTab.pickup.timeSlot.dateTimeSlots.timeZone=Asia/Shanghai&checkout.fulfillment.pickupTab.pickup.timeSlot.dateTimeSlots.timeSlotValue=$timeSlotValue&checkout.fulfillment.pickupTab.pickup.timeSlot.dateTimeSlots.dayRadio=$dayRadio&checkout.fulfillment.pickupTab.pickup.timeSlot.dateTimeSlots.displayStartTime="
                )
            }
        }.body()?.string()?.also { println(it) }?.fromJson(Resp::class.java)?.check("确认提货店铺失败")
            ?: throw RuntimeException("请求接口失败")
    }
    println("确认提货店铺成功, 时间 = $timeSlotValue，店铺id = $selectStore")
    WechatSender().sendMsg("确认提货店铺成功, 时间 = $timeSlotValue，店铺id = $selectStore")
}

private fun readyShopIphone12(driver: ChromeDriver) {
    driver["https://www.apple.com.cn/shop/buy-iphone/iphone-12"]
    Thread.sleep(1000)
    addGoods("MJQ73CH/A", driver.manage().cookies) //iphone12
//    addGoods("MHJ83CH/A", driver.manage().cookies) //充电头
}

private fun refreshAppStore(driver: ChromeDriver, store: String) {
    driver["https://www.apple.com.cn/shop/bag"]
    //结账
    driver.findClickableElement(By.xpath("/html/body/div[3]/div[4]/div[2]/div[1]/div[3]/div/div/div/button"))
        .jsClick(driver)
    //结账
    driver.findClickableElement(By.ById("shoppingCart.actions.checkout")).jsClick(driver)
    //游客登录
//    driver.findClickableElement(By.ById("signIn.guestLogin.guestLogin")).jsClick(driver)
    //appid 登录
    driver.findVisibilityElement(By.ById("signIn.customerLogin.appleId")).sendKeys("13093687239@163.com")
    driver.findVisibilityElement(By.ById("signIn.customerLogin.password")).sendKeys("#103.?yhj%KK")
    driver.findClickableElement(By.ById("signin-submit-button")).jsClick(driver)
    Thread.sleep(2000)
    //我要取货
    driver.findClickableElement(By.ByCssSelector("[for=\"fulfillmentOptionButtonGroup1\"]")).jsClick(driver)
    Thread.sleep(5000)
    refreshLocation(driver)
//    driver.retryFindElement(By.ById("checkout.fulfillment.pickupTab.pickup.storeLocator-$store-label")).jsClick(driver)
//    Thread.sleep(3000)
//    Select(driver.retryFindElement(By.className("form-dropdown-select"))).selectByIndex(1)
//    //只要还在当前页面就不停点击
//    while (driver.currentUrl.contains("/shop/checkout?_s=Fulfillment-init")) {
//        //下一步
//        driver.retryFindElement(By.ById("rs-checkout-continue-button-bottom")).jsClick(driver)
//        Thread.sleep(3000)
//    }
}

private fun refreshLocation(driver: ChromeDriver) {
    //显示零售店
    driver.findClickableElement(By.ByClassName("rs-edit-location-button")).jsClick(driver)
    Thread.sleep(2000)
    driver.findClickableElement(By.className("form-checkbox-indicator")).jsClick(driver)
    Thread.sleep(2000)
    driver.findClickableElement(By.ByXPath("//*[@id=\"panel-rc-province-selector-tabs-0\"]/ol/li[2]/button"))
        .jsClick(driver)
    Thread.sleep(2000)
//    选择 地区
    driver.findClickableElement(By.ByXPath("//*[@id=\"panel-rc-province-selector-tabs-1\"]/ol/li[1]/button"))
        .jsClick(driver)
    Thread.sleep(2000)
//    等待<继续填写>按钮可点击
    WebDriverWait(
        driver,
        Duration.ofSeconds(10)
    ).until { ExpectedConditions.elementToBeClickable(By.id("rs-checkout-continue-button-bottom")) }
    //显示更多
    driver.findClickableElement(By.ByClassName("rt-storelocator-store-showmore")).jsClick(driver)
}

private fun readyShopIphone13Pro(driver: ChromeDriver) {
    driver["https://www.apple.com.cn/shop/buy-iphone/iphone-13-pro"]
    addGoods("MLTE3CH/A", driver.manage().cookies) //远峰蓝
    addGoods("MHJ83CH/A", driver.manage().cookies) //充电头
}

private fun readyShopIphone13ProYingse(driver: ChromeDriver) {
    driver["https://www.apple.com.cn/shop/buy-iphone/iphone-13-pro"]
    addGoods("MLTC3CH/A", driver.manage().cookies) //远峰蓝
    addGoods("MHJ83CH/A", driver.manage().cookies) //充电头
}

fun addGoods(goodsId: String, cookies: Collection<Cookie>) {
    val atbtoken = cookies.first { c -> c.name.equals("as_atb") }.value.split("|")[2]
    val resp = httpGet(default) {
        url("https://www.apple.com.cn/cn/shop/bag/add?product=$goodsId&atbtoken=$atbtoken")
        appleHeader(cookies.joinToString(separator = "; ", transform = { "${it.name}=${it.value}" }))
    }.body()?.string()?.fromJson(AddToBagResp::class.java)!!
    if (resp.errorCode != "SUCCESS") {
        throw RuntimeException("添加至购物车失败")
    }
}

/**
 * 填写人的信息
 */
fun pickup(stk: String, cookies: MutableMap<String, String>, urlHead: String) {
    val lastName = "袁"
    val firstName = "华健"
    val emailAddress = "13093687239@163.com"
    val fullDaytimePhone = "13093687239"
    val nationalId = "342601199607122419"

    retry(60) {
        httpPost(default) {
            url("https://$urlHead.www.apple.com.cn/shop/checkoutx?_a=continue&_m=checkout.pickupContact")
            appleHeader(stk, toCookies(cookies), urlHead)
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
fun billing(stk: String, cookies: MutableMap<String, String>, urlHead: String) {
    val selectBillingOption = "installments0001321713" //招行分期
    val selectInstallmentOption = "24"
//    val selectBillingOption = "installments0001243254" //支付宝分期
//    val selectInstallmentOption = "12"
    retry(60) {
        httpPost(default) {
            url("https://$urlHead.www.apple.com.cn/shop/checkoutx?_a=selectBillingOptionAction&_m=checkout.billing.billingOptions")
            appleHeader(stk, toCookies(cookies), urlHead)
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
    retry(60) {
        httpPost(default) {
            url("https://$urlHead.www.apple.com.cn/shop/checkoutx?_a=continue&_m=checkout.billing")
            appleHeader(stk, toCookies(cookies), urlHead)
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
fun reviewOrder(stk: String, cookies: MutableMap<String, String>, urlHead: String) {
    retry(60) {
        httpPost(default) {
            url("https://$urlHead.www.apple.com.cn/shop/checkoutx?_a=continue&_m=checkout.review.placeOrder")
            appleHeader(stk, toCookies(cookies), urlHead)
            body("application/x-www-form-urlencoded") {
                string("")
            }
        }.body()?.string()?.also { println(it) }?.fromJson(Resp::class.java)?.checkOrderSuccess()
            ?: throw RuntimeException("请求确认下单接口失败")
    }
    println("确认下单")
}

fun HttpPostContext.appleHeader(
    stk: String,
    cookie: String,
    urlHead: String
) {
    header {
        "sec-ch-ua" to "\"Google Chrome\";v=\"95\", \"Chromium\";v=\"95\", \";Not A Brand\";v=\"99\""
        "authority" to "$urlHead.www.apple.com.cn"
        "syntax" to "graviton"
        "sec-ch-ua-mobile" to "?0"
        "user-agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/95.0.4638.69 Safari/537.36"
        "content-type" to "application/x-www-form-urlencoded"
        "x-aos-stk" to stk
        "x-aos-model-page" to "checkoutPage"
        "x-requested-with" to "Fetch"
        "modelversion" to "v2"
        "sec-ch-ua-platform" to "\"macOS\""
        "accept" to "*/*"
        "origin" to "https://$urlHead.www.apple.com.cn"
        "sec-fetch-site" to "same-origin"
        "sec-fetch-mode" to "cors"
        "sec-fetch-dest" to "empty"
        "referer" to "https://$urlHead.www.apple.com.cn/shop/checkout?_s=Fulfillment-init"
        "accept-language" to "zh-CN,zh;q=0.9"
        "cookie" to cookie
    }
}

fun HttpGetContext.appleHeader(
    cookie: String
) {
    header {
        "Connection" to "keep-alive"
        "sec-ch-ua" to "\"Google Chrome\";v=\"95\", \"Chromium\";v=\"95\", \";Not A Brand\";v=\"99\""
        "syntax" to "graviton"
        "sec-ch-ua-mobile" to "?0"
        "User-Agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/95.0.4638.54 Safari/537.36"
        "content-type" to "application/x-www-form-urlencoded"
        "x-aos-model-page" to "checkoutPage"
        "x-requested-with" to "XMLHttpRequest"
        "modelversion" to "v2"
        "sec-ch-ua-platform" to "\"macOS\""
        "accept" to "*/*"
        "sec-fetch-site" to "same-origin"
        "sec-fetch-mode" to "cors"
        "sec-fetch-dest" to "empty"
        "cookie" to cookie
    }
}

class MonitorStockTask(private val phone: String, private val location: String, private val acceptListeners: Int) :
    Runnable {

    @Volatile
    private var listeners = arrayListOf<(String) -> Unit>()

    override fun run() {
        while (true) {
            if (listeners.size != acceptListeners) {
                continue
            }
            try {
                //请求苹果
                val resp =
                    "https://www.apple.com.cn/shop/fulfillment-messages?pl=true&parts.0=$phone&location=${
                        URLEncoder.encode(
                            location
                        )
                    }"
                        .httpGet().body()?.string()?.also { println("请求库存 $it") }
                        ?.fromJson(ApplePhoneResp::class.java)!!
                val availableStore = resp.body.content.pickupMessage.stores
                    .filter { s ->
                        shanghaiShop.contains(s.storeNumber)
                    }
                    .first { s -> s.partsAvailability[phone]!!.pickupDisplay == "available" }
                println(availableStore) //TODO 有货，正在下单
                WechatSender().sendMsg("当前商店有货，准备下单 ${availableStore.storeName}")
                val latch = CountDownLatch(listeners.size)
                listeners.forEach { a ->
                    Thread {
                        try {
                            a.invoke(availableStore.storeNumber)
                        } catch (e: Exception) {
                            println("下单失败，${e.message}")
//                        WechatSender().sendMsg("当前商店${availableStore.storeName}下单失败")
                        } finally {
                            latch.countDown()
                        }
                    }.start()
                }
                latch.await()
                listeners.clear()
                break
            } catch (e: Exception) {
                println(e.message)
                Thread.sleep(1000)
            }
        }
    }

    fun register(listener: (String) -> Unit) {
        listeners.add(listener)
        if (listeners.size == acceptListeners) {
            WechatSender().sendMsg("所有浏览器已就绪")
        }
    }
}

fun toCookies(cookies: MutableMap<String, String>): String {
    return cookies.entries.filter { it.value.isNotBlank() }
        .joinToString(separator = "; ", transform = { "${it.key}=${it.value}" })
}

data class AddToBagResp(
    val addedToBag: Boolean,
    val bagQuantity: Int,
    val errorCode: String,
    val message: String
)

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
    val partsAvailability: Map<String, Stock>,
    val storeName: String,
    val storeNumber: String,
)

data class Stock(
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
    val pickupTab: PickupTab,
)

data class PickupTab(
    val pickup: Pickup,
)

data class Pickup(
    val storeLocator: StoreLocator,
    val timeSlot: TimeSlot
)

data class StoreLocator(
    val searchResults: SearchResults
)

data class TimeSlot(
    val dateTimeSlots: DateTimeSlots
)

data class SearchResults(
    val d: DXXXXXXXXXXXXX
)

data class DXXXXXXXXXXXXX(
    val retailStores: List<RetailStore>
)

data class RetailStore(
    val availability: Availability,
    val storeId: String,
    val storeName: String
)

data class LineItemAvailability(
    val availabilityQuote: String,
    val availableNowForLine: Boolean,
    val partId: String,
    val partName: String
)

data class Availability(
    val storeAvailability: String,
    val lineItemAvailability: List<LineItemAvailability>
)

data class DateTimeSlots(
    val d: DXXXXXXXXXXXXXXX,
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
