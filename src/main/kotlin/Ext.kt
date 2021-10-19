import com.google.gson.Gson
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import org.openqa.selenium.chrome.ChromeDriver

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
fun ChromeDriver.retryFindElement(locator: By, retry: Int = 3): WebElement {
    for (i in 1..retry) {
        try {
            return this.findElement(locator)
        } catch (e: Exception) {
            println("未找到元素")
            if (i == retry) {
                throw e
            }
            Thread.sleep(1000)
        }
    }
    throw RuntimeException("定位不到元素, $locator")
}

/**
 * 重试获取元素
 */
fun ChromeDriver.retryFindElements(locator: By, retry: Int = 3): List<WebElement> {
    for (i in 1..retry) {
        try {
            return this.findElements(locator)
        } catch (e: Exception) {
            println("未找到元素")
            if (i == retry) {
                throw e
            }
            Thread.sleep(1000)
        }
    }
    throw RuntimeException("定位不到元素, $locator")
}

fun WebElement.retryFindElement(locator: By, retry: Int = 3): WebElement {
    for (i in 1..retry) {
        try {
            return this.findElement(locator)
        } catch (e: Exception) {
            println("未找到元素")
            if (i == retry) {
                throw e
            }
            Thread.sleep(1000)
        }
    }
    throw RuntimeException("定位不到元素, $locator")
}


fun WebElement.retryFindElements(locator: By, retry: Int = 3): List<WebElement> {
    for (i in 1..retry) {
        try {
            return this.findElements(locator)
        } catch (e: Exception) {
            println("未找到元素")
            if (i == retry) {
                throw e
            }
            Thread.sleep(1000)
        }
    }
    throw RuntimeException("定位不到元素, $locator")
}


