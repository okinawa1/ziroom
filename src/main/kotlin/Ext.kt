import com.google.gson.Gson
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import java.time.Duration

val gson = Gson()

fun <T> String.fromJson(classOfT: Class<T>): T {
    return gson.fromJson(this, classOfT)
}

/**
 * js 点击
 */
fun WebElement.jsClick(driver: ChromeDriver) {
    driver.executeScript("arguments[0].click();", this)
}

fun WebElement.retrySendKeys(key: String) {
    val retry = 3
    for (i in 1..retry) {
        try {
            return this.sendKeys(key)
        } catch (e: Exception) {
            if (i == retry) {
                throw e
            }
            Thread.sleep(100)
        }
    }
    throw RuntimeException("send keys 失败, $this")
}

/**
 * 重试获取元素
 */
fun ChromeDriver.retryFindElement(locator: By, retry: Long = 3): WebElement {
//    WebDriverWait(this, Duration.ofSeconds(10)).until { ExpectedConditions.elementToBeClickable(locator) }
    for (i in 1..retry) {
        try {
            return this.findElement(locator)
        } catch (e: Exception) {
            if (i == retry) {
                throw e
            }
            Thread.sleep(1000)
        }
    }
    throw RuntimeException("find elements 失败, $this")

}

/**
 * 重试获取元素
 */
fun ChromeDriver.retryFindElements(locator: By, retry: Long = 3): List<WebElement> {
    WebDriverWait(this, Duration.ofSeconds(10)).until { ExpectedConditions.elementToBeClickable(locator) }
    for (i in 1..retry) {
        try {
            return this.findElements(locator)
        } catch (e: Exception) {
            if (i == retry) {
                throw e
            }
            Thread.sleep(1000)
        }
    }
    throw RuntimeException("find elements 失败, $this")

}

fun WebElement.retryFindElement(driver: ChromeDriver, locator: By, retry: Long = 3): WebElement {
    WebDriverWait(driver, Duration.ofSeconds(10)).until { ExpectedConditions.elementToBeClickable(locator) }
    for (i in 1..retry) {
        try {
            return this.findElement(locator)
        } catch (e: Exception) {
            if (i == retry) {
                throw e
            }
            Thread.sleep(1000)
        }
    }
    throw RuntimeException("find elements 失败, $this")

}


fun WebElement.retryFindElements(driver: ChromeDriver, locator: By, retry: Long = 10): List<WebElement> {
    WebDriverWait(driver, Duration.ofSeconds(retry)).until { ExpectedConditions.elementToBeClickable(locator) }
    for (i in 1..retry) {
        try {
            return this.findElements(locator)
        } catch (e: Exception) {
            if (i == retry) {
                throw e
            }
            Thread.sleep(1000)
        }
    }
    throw RuntimeException("find elements 失败, $this")
}


