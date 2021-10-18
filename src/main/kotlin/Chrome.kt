import org.openqa.selenium.By
import org.openqa.selenium.PageLoadStrategy
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.devtools.v94.network.Network
import java.time.Duration
import java.util.*


fun main() {


    System.setProperty("webdriver.chrome.driver", "/Users/mars/chromedriver");
    val chromeOptions = ChromeOptions().apply {
        setPageLoadStrategy(PageLoadStrategy.EAGER)
        setImplicitWaitTimeout(Duration.ofSeconds(10))
        setPageLoadTimeout(Duration.ofSeconds(10))
        setScriptTimeout(Duration.ofSeconds(10))
    }
    val driver = ChromeDriver(chromeOptions)
    driver.devTools.apply {
        //从链接中获取seriesId
        createSession()
        send(Network.enable(Optional.empty(), Optional.empty(), Optional.empty()))
        addListener(Network.requestWillBeSent()) { entry ->
            if (entry.request.url.startsWith("https://www.apple.com.cn/shop/bagx/checkout_now?_a=checkout&_m=shoppingCart.actions")) {
                println("获取到的header${entry.request.headers}")
            }
        }
    }
    driver["https://www.apple.com.cn/shop/buy-iphone/iphone-12"]
    //选机型
    driver.findElement(By.xpath("/html/body/div[2]/div[5]/div[5]/div[2]/div[4]/div[2]/div[2]/div[1]/fieldset/div/div[1]/label"))
        .jsClick(driver)
    //颜色
    driver.findElement(By.xpath("/html/body/div[2]/div[5]/div[5]/div[2]/div[4]/div[2]/div[2]/div[2]/fieldset/div/div[2]/label"))
        .jsClick(driver)

    //存储容量
    driver.findElement(By.xpath("/html/body/div[2]/div[5]/div[5]/div[2]/div[4]/div[2]/div[2]/div[3]/fieldset/div/div[2]/label"))
        .jsClick(driver)

    //你是否有智能手机要折抵？
    driver.findElement(By.xpath("/html/body/div[2]/div[5]/div[5]/div[2]/div[4]/div[2]/div[3]/div[1]/div/fieldset/div/div[1]/label/span"))
        .jsClick(driver)

    //是否要添加 AppleCare+ 服务计划？
    driver.findElement(By.xpath("/html/body/div[2]/div[5]/div[5]/div[2]/div[4]/div[2]/div[4]/div/div[1]/fieldset/div[3]/div/div/ul/div/input"))
        .jsClick(driver)

    //添加
    driver.findElement(By.xpath("/html/body/div[3]/div/div/div/div[1]/div[2]/div[1]/button")).jsClick(driver)
    Thread.sleep(1000)
    //添加到购物车
    driver.findElement(By.xpath("/html/body/div[2]/div[5]/div[5]/div[2]/div[4]/div[2]/div[5]/div[1]/div/div[3]/div[2]/form/div/span/button/span"))
        .jsClick(driver)
    Thread.sleep(500)
    //查看购物袋
    driver.findElement(By.xpath("/html/body/div[2]/div[4]/div[2]/div[2]/div/div/div[2]/div/form/button"))
        .jsClick(driver)
    //结账
    driver.findElement(By.xpath("/html/body/div[3]/div[4]/div[2]/div[1]/div[3]/div/div/div/button"))
        .jsClick(driver)
    //结账
    driver.findElement(By.ById("shoppingCart.actions.checkout")).jsClick(driver)
    //游客登录
    driver.findElement(By.ById("signIn.guestLogin.guestLogin")).jsClick(driver)
    //我要取货
    driver.findElement(By.ById("fulfillmentOptionButtonGroup1")).jsClick(driver)
    //选择地区
    driver.findElement(By.ById("recon-0-40-selectdistrict")).jsClick(driver)
    // 选择上海
    driver.findElement(By.ById("checkout.fulfillment.pickupTab.pickup.storeLocator.address.stateCitySelectorForCheckout.state-1")).jsClick(driver)
    //选择 地区
//    driver.findElement(By.ById("checkout.fulfillment.pickupTab.pickup.storeLocator.address.stateCitySelectorForCheckout.state-1")).jsClick(driver)
    //找到可取货的店铺并点击
    val element =
        driver.findElements(By.ByCssSelector("#checkout\\.fulfillment\\.pickupTab\\.pickup\\.storeLocator-R683-label > span.row > span.column.form-selector-right-col.large-6.small-5 > span.as-storelocator-available-quote"))
            .find { e -> e.text.equals("今天 可取货") }
    element?.findElement(By.xpath("./../../.."))?.jsClick(driver) ?: driver.quit()
    // 5.退出浏览器
    driver.quit()
}