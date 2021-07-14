import Constants.filterRoomList
import Constants.gson
import Constants.noViewCommunities
import com.github.houbb.email.bs.EmailBs
import com.google.gson.Gson
import io.github.rybalkinsd.kohttp.client.defaultHttpClient
import io.github.rybalkinsd.kohttp.dsl.httpGet
import io.github.rybalkinsd.kohttp.ext.url
import java.util.*
import java.util.concurrent.TimeUnit

fun main() {
    println("找房任务 start....")
    var result = mutableListOf<Room>();
    val data = getZiroomList()
    println("搜寻到 ${20 * data.first} 个房源")
    result.addAll(data.second)
    var cur = 1
    while (cur < data.first) {
        val ziroomList = getZiroomList(++cur)
        result.addAll(ziroomList.second)
    }
    if (result.isEmpty()) {
        println("没有合适房源 end....")
        return
    }
    EmailBs.auth("13093687239@163.com", "EQHUKPQABLXVIKOS")
        .content("房源提醒", "${result.map { z -> "房屋名称${z.name}, 房屋价格${z.price} \n" }}\n")
        .sendTo("13093687239@163.com")
    println("找到房源 end....")
}


fun getZiroomList(page: Int = 1): Pair<Int, List<Room>> {
    val resp = httpGet(defaultHttpClient.newBuilder().readTimeout(20, TimeUnit.SECONDS).build()) {
        url("https://sh.ziroom.com/map/room/list")
        param {
            "min_lng" to 121.338027   // 七宝
            "max_lng" to 121.426528   //桂林公园
            "min_lat" to 31.134076   // 龙柏新村
            "max_lat" to 31.174706  // 莲花路
            "zoom" to 20
            "p" to page
        }

        header {
            "X-Requested-With" to "XMLHttpRequest"
            "sec-ch-ua-mobile" to "?0"
            "User-Agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.114 Safari/537.36"
            "Sec-Fetch-Site" to "same-origin"
            "Sec-Fetch-Mode" to "cors"
            "Sec-Fetch-Dest" to "empty"
            "Referer" to "https://sh.ziroom.com/map/"
            "Accept-Language" to "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7"
            "Cookie" to "gr_user_id=d3e40555-aca0-4aa3-a9c1-04bdaf06e4bb; CURRENT_CITY_NAME=%E5%8C%97%E4%BA%AC; gr_session_id_8da2730aaedd7628=058e7d54-84cb-4a85-8a69-c3752c038515; Hm_lvt_4f083817a81bcb8eed537963fc1bbf10=1625192209,1626017231; gr_session_id_8da2730aaedd7628_058e7d54-84cb-4a85-8a69-c3752c038515=true; sensorsdata2015jssdkcross=%7B%22distinct_id%22%3A%2217a6501193c131-0c9adaf8a9a54e-34647600-2073600-17a6501193d13c%22%2C%22%24device_id%22%3A%2217a6501193c131-0c9adaf8a9a54e-34647600-2073600-17a6501193d13c%22%2C%22props%22%3A%7B%22%24latest_referrer%22%3A%22%22%2C%22%24latest_referrer_host%22%3A%22%22%2C%22%24latest_traffic_source_type%22%3A%22%E7%9B%B4%E6%8E%A5%E6%B5%81%E9%87%8F%22%2C%22%24latest_search_keyword%22%3A%22%E6%9C%AA%E5%8F%96%E5%88%B0%E5%80%BC_%E7%9B%B4%E6%8E%A5%E6%89%93%E5%BC%80%22%7D%7D; CURRENT_CITY_CODE=310000; _csrf=Uiy5G0lEOTFUicB4T34WH5ZWyVg-7FUF; visitHistory=%5B%22807575344%22%5D; PHPSESSID=k8dkmf5ndb1iqd681i8ht56uu7; Hm_lpvt_4f083817a81bcb8eed537963fc1bbf10=1626017294"
        }
    }
    val rawRes = gson.fromJson(resp.body()?.string(), ZiroomList::class.java)
    val ziroomList = rawRes
        .data.rooms.asSequence()
        .filter { r -> r.noViewRoom() }  //房间是否未被排除不看
        .filter { r -> r.noViewCommunity() } // 小区是否未被排除不看
        .filter { r -> r.isShare() && r.isNotFaceNorth() } //是否合租，不朝北
        .filter { r -> r.isNotFirstRoom() } //是否非首次出租
        .filter { r -> r.isGoodFloor() } //是否电梯房或低楼层
        .filter { r -> r.isLargeRoom() } //是否超过10平
        .filter { r -> r.priceLimit() } //价格是否在预期内
        .filter { r -> r.isNotMoreThanFourRoom() } //房间是否超过4室
        .filter { r -> r.isSignSoon() } //签约时间是否太久
        .filter { r -> r.isCloseToStation() } // 是否离公共交通近
        .toList()
    ziroomList.forEach { println("房屋id = ${it.id}, 房屋名称 = ${it.name}， 房屋价格 = ${it.price}") }
    return rawRes.data.pages to ziroomList
}

data class Room(
    val activities: List<Any>,
    val activity_marks: List<Any>,
    val agent_end_date: Int,
    val air_vacancy: Any,
    val apartment_type: Int,
    val can_sign_long: Int,
    val can_sign_time: Int,
    val commute_info: String,
    val desc: String,
    val detail_url: String,
    val discount: String,
    val discount_price: Int,
    val discounts: Int,
    val erebus_sale_status: Int,
    val has_3d: Int,
    val has_video: Int,
    val id: String,
    val location: List<Location>,
    val name: String,
    val photo: String,
    val photo_alt: String,
    val price: Int,
    val price_unit: String,
    val resblock_id: String,
    val resblock_name: String,
    val sale_class: String,
    val sale_status: Int,
    val sign_date: String = "",
    val tags: List<Tag>

) {
    fun noViewRoom(): Boolean {
        return !filterRoomList.contains(this.id)
    }

    fun noViewCommunity(): Boolean {
        return noViewCommunities.none { c -> this.name.contains(c) }
    }

    fun isShare(): Boolean {
        return name.split("·")[0].contains("合")
    }

    fun isNotFaceNorth(): Boolean {
        return !name.split("-")[1].contains("北")
    }

    fun isNotMoreThanFourRoom(): Boolean {
        return listOf("5", "6", "7").none { s -> name.contains(s) }
    }

    fun isNotFirstRoom(): Boolean {
        return tags.none { it.title == "首次出租" }
    }

    /**
     * 是否是好得楼层
     * 不是一楼
     * 低楼层，不是四五六七楼
     */
    fun isGoodFloor(): Boolean {
        val matchEntire = ".+(\\d){1,2}/(\\d){1,2}层".toRegex().matchEntire(desc) ?: throw RuntimeException("未能匹配自如楼层描述")
        val groups = matchEntire.groupValues
        val curFloor = groups[1]
        val totalFloor = groups[2]
        if (curFloor == "1") {
            return false
        }

        if (listOf("5", "6", "7").contains(totalFloor) && listOf("4", "5", "6", "7").contains(curFloor)) {
            return false
        }
        return true
    }

    /**
     * 房间大小是否超过10平方
     */
    fun isLargeRoom(): Boolean {
        val matchEntire = "(\\d{1,2}\\.?\\d{0,2})㎡.+".toRegex().matchEntire(desc)
        val area = matchEntire!!.groupValues[1].toDouble()
        return area >= 10
    }

    fun priceLimit(): Boolean {
        return price in 2500..3600
    }

    /**
     * 是否近期签约
     */
    fun isSignSoon(): Boolean {
        if (sign_date.isBlank()) {
            return false
        }
        if (sign_date == "0") {
            return true
        }
        val matchEntire = "预计\\d{4}-(\\d\\d-\\d\\d)可入住".toRegex().matchEntire(sign_date)
        val dateStr = matchEntire!!.groupValues[1]
        val month = dateStr.split("-")[0].toInt()
        val date = dateStr.split("-")[1].toInt()
        if (month > 7 || (month == 7 && date >= 25)) {
            return false
        }
        return true;
    }

    /**
     * 是否离公共交通站点近
     */
    fun isCloseToStation(): Boolean {
        val location = ".+约(\\d+)米".toRegex().matchEntire(location[0].name)!!.groupValues[1].toInt()
        return location <= 1000
    }
}


object Constants {
    var gson = Gson()
    var p = Properties().also { it.load(this.javaClass.getResourceAsStream("ziroom.properties").reader()) }
    var filterRoomList = p.getProperty("noviewroomlist")?.split(",") ?: emptyList()
    var noViewCommunities = p.getProperty("noViewCommunity")?.split(",") ?: emptyList()
}

data class ZiroomList(
    val code: Int,
    val `data`: Data,
    val message: String
)

data class Data(
    val pages: Int,
    val rooms: List<Room>,
    val total: Int
)


data class Location(
    val name: String
)

data class Tag(
    val style: Style,
    val title: String
)

data class Style(
    val background: String,
    val color: String
)