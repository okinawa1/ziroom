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

fun ChromeDriver.getCookie(): String {
    return this.manage().cookies.joinToString(separator = "; ", transform = { "${it.name}=${it.value}" })
}

fun WebElement.retrySendKeys(driver: ChromeDriver, key: String) {
    WebDriverWait(driver, Duration.ofSeconds(10)).until { ExpectedConditions.visibilityOf(this) }
    this.clear()
    return this.sendKeys(key)
}

/**
 * 重试获取元素
 */
fun ChromeDriver.retryFindElement(locator: By, retry: Long = 3): WebElement {
    WebDriverWait(this, Duration.ofSeconds(10)).until { ExpectedConditions.elementToBeClickable(locator) }
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

fun ChromeDriver.findVisibilityElement(locator: By, retry: Long = 3): WebElement {
    WebDriverWait(this, Duration.ofSeconds(10)).until { ExpectedConditions.invisibilityOfElementLocated(locator) }
    return this.findElement(locator)
}

fun retry(retry: Int = 3, apply: () -> Unit) {
    for (i in 1..retry) {
        try {
            return apply.invoke()
        } catch (e: Exception) {
            println(e)
            if (i == retry) {
                throw e
            }
            Thread.sleep(1000)
        }
    }
}

fun <T> retryReturn(retry: Int = 3, apply: () -> T) :T {
    for (i in 1..retry) {
        try {
            return apply.invoke()
        } catch (e: Exception) {
            println(e)
            if (i == retry) {
                throw e
            }
            Thread.sleep(1000)
        }
    }
    throw RuntimeException("调用失败")
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


