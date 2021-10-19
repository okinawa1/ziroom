import org.openqa.selenium.By
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.support.ui.Select
import java.time.Duration


fun main() {
    System.setProperty("webdriver.chrome.driver", "/Users/mars/chromedriver")
    val chromeOptions = ChromeOptions().apply {
//        setPageLoadStrategy(PageLoadStrategy.EAGER)
        setImplicitWaitTimeout(Duration.ofSeconds(10))
        setPageLoadTimeout(Duration.ofSeconds(10))
        setScriptTimeout(Duration.ofSeconds(10))
    }
    var driver: ChromeDriver? = null
    while (true) {
        try {
            driver = ChromeDriver(chromeOptions);
            apple(driver)
            println("下单成功")
            WechatSender().sendMsg("苹果商店下单成功，请尽快支付")
            break
        } catch (e: Exception) {
            println(e.printStackTrace())
            WechatSender().sendMsg("下单失败，检查异常,${e.printStackTrace()}")
        } finally {
            driver?.quit()
        }
    }
}

fun apple(driver: ChromeDriver) {
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
//    //选择地区，优化一下，直接勾选当地
//    driver.findElement(By.ByClassName("as-address-accordion")).jsClick(driver)
//    Thread.sleep(1000)
//    // 选择上海
//    driver.findElement(By.ById("checkout.fulfillment.pickupTab.pickup.storeLocator.address.stateCitySelectorForCheckout.state-1"))
//        .jsClick(driver)
//    Thread.sleep(2000)
//    //选择 地区
//    driver.findElement(By.ById("checkout.fulfillment.pickupTab.pickup.storeLocator.address.stateCitySelectorForCheckout.district-0"))
//        .jsClick(driver)
    //结账
    driver.retryFindElement(By.ById("shoppingCart.actions.checkout")).jsClick(driver)
    //游客登录
    driver.retryFindElement(By.ById("signIn.guestLogin.guestLogin")).jsClick(driver)
    //我要取货
    driver.retryFindElement(By.ById("fulfillmentOptionButtonGroup1")).jsClick(driver)
    //找到可取货的店铺并点击
    val availableShop = driver.retryFindElements(By.className("as-storelocator-searchitem"))
        .find { e -> e.retryFindElement(By.className("as-storelocator-available-quote")).text.equals("今天 可取货") }
        ?: return
    //选择店铺点击
    availableShop.retryFindElement(By.className("as-storelocator-label")).jsClick(driver)
    //选择时间
    val selectTime = availableShop.retryFindElements(By.className("form-dropdown-select")).first()
    Select(selectTime).selectByIndex(1)
    //继续填写取货详情
    driver.retryFindElement(By.ById("rs-checkout-continue-button-bottom")).jsClick(driver)
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
}