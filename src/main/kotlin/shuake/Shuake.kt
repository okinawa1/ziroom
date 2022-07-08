package shuake

import WechatSender
import findClickableElement
import findVisibilityElement
import jsClick
import org.openqa.selenium.By
import org.openqa.selenium.PageLoadStrategy
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.devtools.v101.network.Network
import retry
import java.time.Duration
import java.util.*


val chromeOptions = ChromeOptions().apply {
    //无头的问题
//    setHeadless(true)
    addArguments(
        "--no-sandbox",
        "--disable-gpu",
        "--mute-audio",
//        "--disable-images",
        "window-size=1200x600",
        "user-agent=Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.198 Safari/537.36"
    )
    addArguments("--incognito")
//    addArguments("--disable-blink-features=AutomationControlled")
    setPageLoadStrategy(PageLoadStrategy.EAGER)
    setImplicitWaitTimeout(Duration.ofSeconds(20))
}

var newPlayTime = System.currentTimeMillis()


fun main() {
    System.setProperty("webdriver.chrome.driver", "/Users/mars/Downloads/chromedriver")
    val driver = ChromeDriver(chromeOptions)


    driver.devTools.apply {
        createSession()
        send(Network.enable(Optional.empty(), Optional.empty(), Optional.empty()))
        addListener(Network.requestWillBeSent()) { entry ->
            //读取cookie
            if (entry.request.url.contains("https://aidedu.api.adksedu.com/front.v1/course/push")) {
                //更新时间
                newPlayTime = System.currentTimeMillis()
                //时间格式化
                val time = Date(newPlayTime).toString()
                println("当前最新时间$time")
            }
        }
    }

    retry {
        driver["http://shjg.yt.lllnet.cn"]
        //登录
        driver.findClickableElement(By.xpath("//*[@id=\"app\"]/div[1]/div[1]/div[2]/div/div[2]/span"))
            .jsClick(driver)
        //账号
        driver.findVisibilityElement(By.xpath("/html/body/div[2]/div/div[2]/div/div[2]/div/div[1]/div/input"))
            .sendKeys("13093687239")
        //密码
        driver.findVisibilityElement(By.xpath("/html/body/div[2]/div/div[2]/div/div[2]/div/div[1]/input"))
            .sendKeys("666666")
        driver.findClickableElement(By.xpath("/html/body/div[2]/div/div[2]/div/div[2]/div/button"))
            .jsClick(driver)
        Thread.sleep(2000)
        //班级计划
        driver.findClickableElement(By.xpath("//*[@id=\"app\"]/div[2]/div[2]/div/div/div/div[3]/ul/li[2]/div[2]/div/div"))
            .jsClick(driver)
        //课程列表
        driver.findClickableElement(By.xpath("//*[@id=\"app\"]/div[2]/div[2]/div/div/div[2]/ul/li[6]"))
            .jsClick(driver)
        //继续学习
        driver.findClickableElement(By.xpath("//*[@id=\"app\"]/div[2]/div[2]/div[2]/div[2]/div/button"))
            .jsClick(driver)
        Thread.sleep(2000)
        checkPlay(driver)
        checkPush()
        checkSuspend(driver)
        checkRefresh(driver)
    }
    //主线程停止 一天6小时学习。
    Thread.sleep(1000 * 60 * 60 * 6)
}

fun checkPlay(driver: ChromeDriver) {
    Thread {
        while (true) {
            try {
                val btn = driver.findElement(By.xpath("/html/body/div[1]/div[2]/div[2]/div[1]/div[3]/div[4]/div[2]"))
                btn.getDomAttribute("class").let {
                    if (it.contains("playing")) {
                        println("播放中")
                    } else {
                        println("确认播放")
                        btn.click()
                    }
                }
            } catch (e: Exception) {
                println("播放按钮不存在")
            }
            Thread.sleep(1000)
        }
    }.start()
}

fun checkRefresh(driver: ChromeDriver) {
    Thread {
        while (true) {
            try {
                val errorTips = driver.findElement(By.xpath("//*[@id=\"app\"]/div[2]/div[2]/div[1]/div[7]/button"))
                if (errorTips.isDisplayed) {
                    println("刷新当前页面")
                    driver.navigate().refresh()
                }
            } catch (e: Exception) {
                println(e.message)
            }
            Thread.sleep(1000)
        }
    }.start()
}

fun checkSuspend(driver: ChromeDriver) {
    Thread {
        while (true) {
            try {
                //确认提示
                val tips = driver.findElement(By.xpath("/html/body/div[2]/div/div[2]/div/div[2]/div[2]/button"))
                tips?.jsClick(driver)
                println("确认提示")
            } catch (e: Exception) {
                println(e.message)
            }
            Thread.sleep(1000)
        }
    }.start()
}

//检测是否持续发送地址
fun checkPush() {
    Thread {
        while (true) {
            if (System.currentTimeMillis() - newPlayTime > 1000 * 60) {
                println("视频观看失败，请重试，检查课程是否已经结束")
                WechatSender().sendMsg("视频观看失败，请重试，检查课程是否已经结束")
                Thread.sleep(1000)
            }
        }
    }.start()
}
