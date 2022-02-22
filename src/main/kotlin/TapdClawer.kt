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
//    Wiki(
//        "1",
//        "## 部署方式\\r\\n1. 因为是在 k8s 上部署，所以去除了 meta server，去除了自带的注册中心（Eureka）\\r\\n\\r\\n原有架构：\\r\\n![enter image description here](/tfl/pictures/202111/tapd_39130625_1637638588_89.png)\\r\\n简化后：\\r\\n![enter image description here](/tfl/pictures/202111/tapd_39130625_1637638637_27.png)\\r\\n\\r\\n## Portal 端\\r\\n### 1. 修改 Namespace 展示方式\\r\\n1. 默认收起 namespace\\r\\n2. namespace 添加分页和搜索功能\\r\\n![enter image description here](/tfl/pictures/202111/tapd_39130625_1637638366_76.png)\\r\\n### 2. 添加配置对比功能\\r\\n![enter image description here](/tfl/pictures/202111/tapd_39130625_1637638414_8.png)\\r\\n### 3. 配置规范校验\\r\\n该功能只会提示一些配置规范（如：命名，重复检查等）\\r\\n![enter image description here](/tfl/pictures/202111/tapd_39130625_1637638792_6.png)\\r\\n\\r\\n## Open API\\r\\n原有版本的开放接口不能满足，所以我们扩展了原有接口，参考：[Apollo Open API](https://www.tapd.cn/39130625/markdown_wikis/show/#1139130625001003592)\\r\\n第三方接入方式未改动\\r\\n\\r\\n## Client 端\\r\\n> 我们修改了 apillo-client 代码，并发布到私库中，未使用官方的 sdk\\r\\n\\r\\n### 1. 去除了原来必须声明 Namespace 的方式\\r\\n原来必须在 application.properties 指定要 load 的 namespace，这个对于新加 namespace 很不方便（我们会有很多关联公共 namespace 的操作），所以改为动态加载，在 Apollo 上有多少 namespace 就加载多少 namespace，而原来的方式作为一种 fallbcak，如果加载不到则走原来的方式\\r\\n\\r\\n### 2. 直接配置 config server 地址\\r\\n因为去除了 meta server，所以我们通过在 apollo-env.properties 文件中指定 config server 地址，来拉配置，配置如下：\\r\\n```\\r\\npro.config=http://service-apollo-config-server-pro.sre\\r\\nuat.config=http://service-apollo-config-server-uat.sre\\r\\nfat.config=http://service-apollo-config-server-fat.sre\\r\\ndev.config=http://service-apollo-config-server-dev.sre\\r\\n```\\r\\n格式为：`{环境名小写}.config={config server 地址}`\\r\\n\\r\\n### 3. DEBUG 模式\\r\\n> 因为现在开发的时候，如果需要修改配置文件，那么就需要去 apollo修改，或者使用 -D 的方式去覆盖，当改大量配置的时候，这样会变的异常的麻烦，\\r\\n所以这边推出了 debug模式\\r\\n\\r\\n#### 使用方法\\r\\n在启动项目的时候加上  `-Denv=debug`  即可\\r\\n在运行项目之后，将会在 项目的  `src/main/resources/apollo`  路径下面生成所有的配置文件，这里的配置信息全部来自  `DEV`  环境\\r\\n当要覆盖某一个配置项的时候 在前面加上  `@`  然后修改配置的值即可\\r\\n如:  `server.port=3200`  -->  `@server.port=4200`  这时候启动项目后，就会以 4200 端口运行\\r\\n同样如果是新增的配置，也需要在前面加上  `@` \\r\\n如果是要删除某个配置项，那么使用  `@#`  来删除\\r\\n如:  `server.port=3200`  -->  `@#server.port=3200`  这时候启动项目后，就会以 8080 端口运行（默认值）",
//        "SPA平台 v2.0",
//        "12312"
//    ).toLocal()
//    println(content)
//    PicPath("/tfl/pictures/202111/tapd_39130625_1637638637_27.png").downloadPic()
//
    runBlocking {
        //curl -u 'OvvP1uuu:AD4A1F9B-9158-3AF0-B680-AA1F89BC73A5' 'https://api.tapd.cn/tapd_wikis?workspace_id=39130625'
        //39130625 =>  文档_公共技术和基础架构   438个
        //52861821 =>  文档_业务中台系统  906个wiki
        //46645505 =>  团队_中台产品  112个
        //44257689 => 信息科技中心  129 个

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

