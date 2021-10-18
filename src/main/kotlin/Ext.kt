import com.google.gson.Gson
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


