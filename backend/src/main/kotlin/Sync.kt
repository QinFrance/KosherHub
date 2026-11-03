import org.jsoup.Jsoup
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

val http:HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).followRedirects(HttpClient.Redirect.NORMAL).build()
fun request(url:String,limit:Int=4_000_000):ByteArray {
    val uri=URI(url);require(uri.scheme=="https"&&uri.userInfo==null){"Only public HTTPS sources are accepted"}
    require(uri.host in setOf("dietdroid.com","api.github.com","github.com","raw.githubusercontent.com","codeload.github.com","f-droid.org")){"Source host is not allowlisted"}
    val builder=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(90)).header("User-Agent","KosherHub-catalog/1.0 (https://github.com/QinFrance/KosherHub)")
    // Public source GETs must stay anonymous: Actions tokens are scoped to this repository.
    val future=http.sendAsync(builder.GET().build(),HttpResponse.BodyHandlers.ofByteArray())
    val response=try{future.orTimeout(30,java.util.concurrent.TimeUnit.SECONDS).join()}catch(e:Exception){future.cancel(true);throw IllegalStateException("Source request timed out or failed",e)}
    require(response.statusCode()==200){"Source returned HTTP ${response.statusCode()}"}
    val body=response.body();require(body.size<=limit){"Source exceeds the size limit"};return body
}
fun githubJson(url:String):JsonObject = catalogJson.parseToJsonElement(request(url).decodeToString()).jsonObject
@Serializable data class ReviewedSource(val name:String,val url:String,val sha256:String)
@Serializable data class AppSource(val id:String,val repository:String,val license:String,val assetPattern:String,val mirrorApproved:Boolean=false,val attribution:String="",val version:String="",val assetName:String="",val sourceReviewVersion:String="",val binarySha256:String="",val sourceBundleUrl:String="",val sourceBundleSha256:String="",val auxiliarySources:List<ReviewedSource> = emptyList())
fun sources():List<AppSource> = catalogJson.decodeFromString(File("data/app-sources.json").readText())
fun syncCatalog(){
 val previous=loadCatalog();var successes=0
 val devices=previous.devices.map{device->try{
   val doc=Jsoup.parse(request(device.sourceUrl).decodeToString(),device.sourceUrl);var kind="";val resources=mutableListOf<Resource>()
   for(element in doc.body().allElements){
    if(element.tagName().matches(Regex("h[1-6]"))){val heading=element.text().trim().uppercase();kind=when{heading.contains("FIRMWARE")->"firmware";heading=="APKS"||heading=="APPS"->"apps";heading.contains("GUIDE")->"guide";heading.contains("TOOLS")||heading.contains("DRIVER")->"tools";else->""}}
    if(kind.isNotEmpty()&&element.tagName()=="a") {val url=element.absUrl("href");val title=element.text().trim();if(url.startsWith("https://")&&title.length in 2..180&&!url.contains("dietdroid.com/wp-admin"))resources+=Resource(title,url,kind)}
   }
   for(pane in doc.select(".kt-accordion-pane")) {
    val heading=pane.selectFirst(".kt-blocks-accordion-title")?.text()?.uppercase()?:continue
    val section=when{heading.contains("FIRMWARE")->"firmware";heading=="APKS"||heading=="APPS"->"apps";heading.contains("GUIDE")->"guide";heading.contains("TOOLS")||heading.contains("DRIVER")||heading.contains("MODULE")->"tools";else->continue}
    for(link in pane.select(".kt-accordion-panel a[href]")){val url=link.absUrl("href");val title=link.text().trim();if(url.startsWith("https://")&&title.length in 2..180&&!url.contains("usp=sharinghttps")&&!url.contains("f21-pro-plus-stock-unisoc-t310/"))resources+=Resource(title,url,section)}
   }
   if(resources.isNotEmpty()){successes++;device.copy(resources=resources.distinctBy{it.title to it.url})}else device
 }catch(e:Exception){println("Keeping last known resources for ${device.id}: ${e.message}");device}}
 val registry=sources().associateBy{it.id}
 val apps=previous.apps.map{app->val source=registry[app.id]?:return@map app;try{
  val release=githubJson("https://api.github.com/repos/${source.repository}/releases/latest");val tag=release["tag_name"]!!.jsonPrimitive.content
  val asset=release["assets"]!!.jsonArray.map{it.jsonObject}.firstOrNull{Regex(source.assetPattern).matches(it["name"]!!.jsonPrimitive.content)}?:return@map app
  val digest=asset["digest"]?.jsonPrimitive?.contentOrNull?.removePrefix("sha256:")?:""
  successes++
  if(app.status=="mirrored"&&app.version==tag)app else app.copy(version=tag,downloadUrl=asset["browser_download_url"]!!.jsonPrimitive.content,sourceArchive="https://github.com/${source.repository}/archive/refs/tags/$tag.tar.gz",sha256=digest,bytes=asset["size"]!!.jsonPrimitive.long,status="official-release")
 }catch(e:Exception){println("Keeping last known release for ${app.id}: ${e.message}");app}}
 val updated=mergeCfop(previous.copy(updated=if(successes>0)LocalDate.now().toString()else previous.updated,devices=devices,apps=apps))
 File("data/catalog.json").writeText(catalogJson.encodeToString(Catalog.serializer(),updated));println("Checked ${devices.size} devices and ${apps.size} apps; $successes sources refreshed.")
}
