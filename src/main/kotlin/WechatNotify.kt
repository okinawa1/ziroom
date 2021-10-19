import Constants.gson
import io.github.rybalkinsd.kohttp.dsl.httpPost
import io.github.rybalkinsd.kohttp.ext.httpGet
import io.github.rybalkinsd.kohttp.ext.url

class WechatSender(
    private var weComCId: String = "ww258c6bf017f77b9b",// 企业Id①
    private var weComSecret: String = "i__6_HmAi0COd3aXasfTHObbFhmH3auXt5IkaYzDGJI",// 应用secret②
    private var weComAId: String = "1000002",// 应用ID③
    private val weComTouId: String = "@all"
) {
    // 获取Token
    fun sendMsg(content: String) {
        val resp =
            "https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=$weComCId&corpsecret=$weComSecret".httpGet()

        val string1 = resp.body()?.string()
        val accessToken = gson.fromJson(string1, Token::class.java).access_token

        httpPost {
            url("https://qyapi.weixin.qq.com/cgi-bin/message/send?access_token=${accessToken}")
            body { json(SendForm(weComTouId, weComAId, Text(content)).toJson()) }
        }.body()?.string()
    }

    data class Token(
        val access_token: String,
        val errcode: Int,
        val errmsg: String,
        val expires_in: Int
    )


    data class SendForm(
        var touser: String,
        var agentid: String,
        var text: Text,
        var msgtype: String = "text",
        val duplicate_check_interval: Int = 600,
    ) {
        fun toJson(): String {
            return gson.toJson(this)
        }
    }

    data class Text(var content: String)
}
