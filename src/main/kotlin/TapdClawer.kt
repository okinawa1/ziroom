import io.github.rybalkinsd.kohttp.dsl.httpGet
import io.github.rybalkinsd.kohttp.ext.asStream
import io.github.rybalkinsd.kohttp.ext.url
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File

const val picHost = "https://www.tapd.cn"
val baseDir = ".${File.separator}downloads"
val picRegex = Regex("!\\[.+\\]\\((.+)\\)")

val wikiTree = mutableMapOf<String, Pair<Wiki, MutableList<Wiki>>>()

fun main() {
    runBlocking {
        //curl -u 'OvvP1uuu:AD4A1F9B-9158-3AF0-B680-AA1F89BC73A5' 'https://api.tapd.cn/tapd_wikis?workspace_id=?'
        //39130625
        //52861821
        //46645505
        //44257689

        val limit = 100
        val workspaceId = 44257689
        repeat(2) { it ->
            val page = it + 1
            val httpGet = httpGet {
                url("https://api.tapd.cn/tapd_wikis?workspace_id=$workspaceId&page=$page&limit=$limit")
                header {
                    "Host" to "api.tapd.cn"
                    "Authorization" to "Basic [T3Z2UDF1dXU6QUQ0QTFGOUItOTE1OC0zQUYwLUI2ODAtQUExRjg5QkM3M0E1]"
                }
            }
            val string = httpGet.body()?.string()
            println(string)
            gson.fromJson(string, TapdWikiResp::class.java)
                .data.forEach { it.Wiki.register() }
//                    }.forEach { wiki -> wiki.toLocal() }
        }
        downloadTree()
    }

//    PicPath("tfl/pictures/202004/tapd_39130625_1586833917_85.png").downloadPic()
}

suspend fun downloadTree() {
//    树形结构开始遍历。
    //找到根节点并创建文件夹
    wikiTree.values.map { it.first }.first { wiki -> wiki.isRoot() }.let { downloadByRoot(it.getChilds(), baseDir) }
}

suspend fun downloadByRoot(root: List<Wiki>, basePath: String, deep: Int = 0) {
    //根节点。
    root.forEach {
        if (it.isDir()) {
            //创建文件夹
            val newPath = it.createDir(basePath, deep)
            downloadByRoot(it.getChilds(), newPath, deep + 1)
        } else {
            //只是文件
            it.download(basePath, deep)
        }
    }
}

data class TapdWikiResp(
    val `data`: List<TapdData>,
    val info: String,
    val status: Int
)

data class TapdData(
    val Wiki: Wiki
)

data class Wiki(
    val id: String,
    val markdown_description: String,
    val name: String,
    val parent_wiki_id: String,
    var content: String = ""
) {


    fun register() {
        //注册，获取儿子
        val childs = wikiTree.values.map { it.first }.filter { wiki -> wiki.parent_wiki_id == id }.toMutableList()
        wikiTree[id] = (this to childs)

        //找到父亲并链接
        var parent = wikiTree[parent_wiki_id]
        if (parent == null) {
            parent = (Wiki(parent_wiki_id, "", "", "") to mutableListOf())
            wikiTree[parent_wiki_id] = parent
        }
        parent.second.add(this)
    }

    fun getParent(): Wiki? {
        return wikiTree[parent_wiki_id]?.first
    }

    fun getChilds(): List<Wiki> {
        return wikiTree[id]?.second ?: mutableListOf()
    }

    fun isDir(): Boolean {
        //是否应该作为文件夹，有儿子
        return getChilds().isNotEmpty()
    }

    private fun isDirWithContent(): Boolean {
        return isDir() && markdown_description.isNotBlank()
    }

    /**
     * downloads/规范
     * @return newPath
     */
    suspend fun createDir(basePath: String, deep: Int): String {
        val dirPath = "${basePath}${File.separator}$name${File.separator}"
//        File(dirPath).create()
        if (isDirWithContent()) {
            download(dirPath, deep)
        }
        return dirPath
    }

    fun isRoot(): Boolean {
        //没有父节点
        return getParent() == null
    }

    private fun dealContent(): String {
        return markdown_description
            .replace("\\r\\n", "\n")
            .replace("\\t", "    ")
    }

    suspend fun download(dirPath: String, deep: Int) {
        content = dealContent()
        if (content.isBlank()) {
            return
        }
        //下载 要求可以树形下载
        downloadPic(deep)
        File("$dirPath${File.separator}$name.md")
            .also { f -> f.create() }
            .writeText(content)
    }

    private suspend fun downloadPic(deep: Int) = coroutineScope {
        getPicUrlsFromContent().forEach { url ->
            val newUrl = (1..deep).joinToString(separator = "/") { ".." } + url
            content = content.replace(url, newUrl)
            launch {
                kotlin.runCatching {
                    PicPath(url).downloadPic()
                }.onSuccess {
                    println("$url 爬取图片成功")
                }.onFailure {
                    println("$url 爬取图片失败: ${it.message}")
                }
            }
        }
    }

    /**
     * 从文章中获取图片url
     */
    private fun getPicUrlsFromContent(): List<String> {
        return picRegex.findAll(content).map { it.groups[1]?.value ?: "" }.toList()
    }
}

data class PicPath(val picUrl: String) {

    fun downloadPic() {
        File(getLocalPath())
            .also { f -> f.create() }
            .takeIf { file -> file.length() == 0L }?.apply {
                //如果本地文件为空，则下载图片到本地
                httpGet {
                    url(getNetworkPath())
                    header {
                        "User-Agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/98.0.4758.102 Safari/537.36"
                        "Cookie" to "tapdsession=1645012418706c362a2ff703c7055f56593056f2c2ae1489918ed7137efc51f708567c24ea;"
                    }
                }.asStream()?.copyTo(outputStream())
            }
    }

    private fun getNetworkPath(): String {
        return "$picHost${File.separator}$picUrl"
    }

    private fun getLocalPath(): String {
        return "$baseDir${File.separator}$picUrl"
    }
}

