import com.google.gson.annotations.SerializedName
import io.github.rybalkinsd.kohttp.ext.httpGet
import org.openqa.selenium.By
import org.openqa.selenium.PageLoadStrategy
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.support.ui.Select
import java.net.URLEncoder
import java.time.Duration
import java.util.*
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.timerTask

//是否正在购物
@Volatile
var shopping = false

fun main() {
    System.setProperty("webdriver.chrome.driver", "/Users/mars/chromedriver")
    val chromeOptions = ChromeOptions().apply {
        //无头的问题
//        addArguments("--window-size=1920,1080","--start-maximized","--ignore-certificate-errors","--allow-running-insecure-content", "--disable-extensions", "--headless", "--no-sandbox", "--disable-gpu", "--disable-dev-shm-usage", "--proxy-server=direct://")
        setPageLoadStrategy(PageLoadStrategy.EAGER)
        setImplicitWaitTimeout(Duration.ofSeconds(5))
    }
    var driver: ChromeDriver? = null
    var timer: Timer? = null
    while (true) {
        try {
            driver = ChromeDriver(chromeOptions)
            timer = Timer()
            //加购
//            readyShopIphone12(driver)
            readyShopIphone13Pro(driver)
            val latch = CountDownLatch(1)
            //定时刷新页面，5秒一次,检查是否有库存
            timer.schedule(timerTask {
                //如果正在下单，则跳过本次任务
                if (shopping) return@timerTask
                try {
                    if (driver.currentUrl.contains("www.apple.com.cn/shop/checkout?_s=Fulfillment-init")) {
                        println("监听线下是否有货。。。。")
                        //当前页面有库存吗
                        shopping = true
                        //TODO 把初始化和下单区分开来
                        refreshAppStore(driver)
                        shopping(driver)
                        //下单成功，取消任务
                        this.cancel()
                        latch.countDown()
                        WechatSender().sendMsg("苹果商店下单成功，请尽快支付")
                    } else {
                        return@timerTask
                    }
                } catch (e: NoStockRuntimeException) {
                    println("商品无库存，刷新页面。。。")
                    shopping = false
                    driver.navigate().refresh()
                } catch (e: Exception) {
                    println("下单失败，${e.printStackTrace()}")
                    WechatSender().sendMsg("苹果商店下单失败，请检查程序，${e.printStackTrace()}")
                    shopping = false
                    //重新下单
                    driver["https://secure2.www.apple.com.cn/shop/checkout?_s=Fulfillment-init"]
                }
            }, 0L, 6 * 1000L)
            latch.await() //下单成功才会继续执行。
            break
        } catch (e: Exception) {
            println(e.printStackTrace())
            WechatSender().sendMsg("系统异常,${e.printStackTrace()}")
        } finally {
            timer?.cancel()
            driver?.quit()
        }
    }
}

private fun shopping(driver: ChromeDriver) {
    //找到可取货的店铺并点击
    val availableShop = driver.retryFindElements(By.className("form-selector-label"))
        .filterNot { e ->
            e.retryFindElement(
                driver,
                By.xpath("./span[2]/span[1]/span[2]")
            ).text.contains(Regex("Apple 苏州|Apple 无锡恒隆广场|Apple 天一广场|Apple 杭州万象城|Apple 西湖"))
        }
        .find { e ->
            !e.retryFindElement(driver, By.xpath("./span[2]/span[2]/span[1]")).getAttribute("textContent")
                .equals("目前不可取货")
        }
        ?: throw NoStockRuntimeException("没有可下单店铺")
    WechatSender().sendMsg("当前有货，正在下单")
    //选择店铺点击
    availableShop.jsClick(driver)
    //选择时间
    Select(driver.retryFindElement(By.className("form-dropdown-select"))).selectByIndex(1)
    //只要还在当前页面就不停点击
    while (driver.currentUrl.contains("/shop/checkout?_s=Fulfillment-init")) {
        //下一步
        driver.retryFindElement(By.ById("rs-checkout-continue-button-bottom")).jsClick(driver)
        Thread.sleep(3000)
    }
    //填单子
    driver.retryFindElement(By.ByName("lastName")).retrySendKeys("袁")
    driver.retryFindElement(By.ByName("firstName")).retrySendKeys("华健")
    driver.retryFindElement(By.ByName("emailAddress")).retrySendKeys("13093687239@163.com")
    driver.retryFindElement(By.ByName("fullDaytimePhone")).retrySendKeys("13093687239")
    driver.retryFindElement(By.ByName("nationalId")).retrySendKeys("342601199607122419")
    //支付方式
    driver.retryFindElement(By.ById("rs-checkout-continue-button-bottom")).jsClick(driver)
    //招商银行
    driver.retryFindElement(By.ByXPath("/html/body/div[2]/div[4]/div/div[7]/div[1]/div[2]/div/div/div[1]/div[1]/div[2]/fieldset/div/div/div[2]/div[3]/div[1]/div/div/label"))
        .jsClick(driver)
    //24免息
    driver.retryFindElement(By.ByXPath("/html/body/div[2]/div[4]/div/div[7]/div[1]/div[2]/div/div/div[1]/div[1]/div[2]/fieldset/div/div/div[2]/div[3]/div[2]/div/div[1]/div/ul/li[5]/div/label"))
        .jsClick(driver)
    //检查订单
    driver.retryFindElement(By.ById("rs-checkout-continue-button-bottom")).jsClick(driver)
    //立即下单
    driver.retryFindElement(By.ByXPath("/html/body/div[2]/div[4]/div/div[4]/div[1]/div[1]/div/div/div[2]/div[6]/div/div/div/div[1]/button"))
        .jsClick(driver)
    //是否成功
    driver.retryFindElement(By.ByClassName("rs-thankyouqr-header"))
    println("下单成功")
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
    //是否保存了位置信息
    //显示零售店
    val addressAccordion = driver.retryFindElement(By.ByClassName("as-address-accordion"))
    val value = addressAccordion.getAttribute("value")
    if (value.equals("") || !value.startsWith("上海")) {
        addressAccordion.jsClick(driver)
        //是否保存地址
        if (!driver.retryFindElement(By.id("checkout.fulfillment.pickupTab.pickup-locationConsent"))
                .getAttribute("checked").toBoolean()
        ) {
            //记录地区
            driver.retryFindElement(By.ByXPath("/html/body/div[2]/div[4]/div/div[6]/div[1]/div[2]/div/div/div[2]/div/div/div/div[1]/div/div/div/div/div/div/fieldset/div[1]/div/div/div/div[2]/div/div[1]/label"))
                .jsClick(driver)
        }
        // 选择上海
        driver.retryFindElement(
            By.ById("checkout.fulfillment.pickupTab.pickup.storeLocator.address.stateCitySelectorForCheckout.state-1"),
        ).jsClick(driver)
        Thread.sleep(500)
        //选择 地区 TODO @Mars 想办法直接优化掉。
        driver.retryFindElement(
            By.ById("checkout.fulfillment.pickupTab.pickup.storeLocator.address.stateCitySelectorForCheckout.district-0"),
        ).jsClick(driver)
        Thread.sleep(1500)
    }
    //显示更多
    driver.retryFindElement(By.ByClassName("rr-toggle")).jsClick(driver)
}

private fun readyShopIphone13Pro(driver: ChromeDriver) {
    driver["https://www.apple.com.cn/shop/buy-iphone/iphone-13-pro"]
    //选机型
    driver.retryFindElement(By.xpath("/html/body/div[2]/div[5]/div[5]/div[2]/div[4]/div[2]/div[2]/div[1]/fieldset/div/div[1]/label"))
        .jsClick(driver)
    //颜色
    driver.retryFindElement(By.xpath("/html/body/div[2]/div[5]/div[5]/div[2]/div[4]/div[2]/div[2]/div[2]/fieldset/div/div[1]/label"))
        .jsClick(driver)
    //存储容量
    driver.retryFindElement(By.xpath("/html/body/div[2]/div[5]/div[5]/div[2]/div[4]/div[2]/div[2]/div[3]/fieldset/div/div[2]/label"))
        .jsClick(driver)
    //你是否有智能手机要折抵？
    driver.retryFindElement(By.xpath("/html/body/div[2]/div[5]/div[5]/div[2]/div[4]/div[2]/div[3]/div[1]/div/fieldset/div/div[1]/label"))
        .jsClick(driver)
    //是否要添加 AppleCare+ 服务计划？
    driver.retryFindElement(By.xpath("/html/body/div[2]/div[5]/div[5]/div[2]/div[4]/div[2]/div[4]/div/div[1]/fieldset/div[3]/div/div/div/div/input"))
        .jsClick(driver)
    //添加
//    driver.retryFindElement(By.xpath("/html/body/div[3]/div/div/div/div[1]/div[2]/div[1]/button")).jsClick(driver)
    //添加到购物车
    driver.retryFindElement(By.xpath("/html/body/div[2]/div[5]/div[5]/div[2]/div[4]/div[2]/div[5]/div[1]/div/div[3]/div[2]/form/div/span/button"))
        .jsClick(driver)
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
    //我要取货
    driver.retryFindElement(By.ById("fulfillmentOptionButtonGroup1")).jsClick(driver)
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


class NoStockRuntimeException(str: String) : RuntimeException(str)