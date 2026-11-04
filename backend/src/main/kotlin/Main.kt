import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.http.content.*
import kotlinx.serialization.json.Json
import java.io.File

val catalogJson=Json { ignoreUnknownKeys=true;prettyPrint=true;encodeDefaults=true }
fun loadCatalog(path:String = System.getenv("CATALOG_PATH")?:"data/catalog.json"):Catalog {
    val catalog = loadSectionEntries(catalogJson.decodeFromString<Catalog>(File(path).readText()))
    val mirrors = File("data/firmware-mirrors.json")
    return if (mirrors.exists()) applyFirmwareMirrors(catalog, catalogJson.decodeFromString<FirmwareMirrorIndex>(mirrors.readText()).assets) else catalog
}
fun Application.module(catalog:Catalog = loadCatalog()) {
    install(ContentNegotiation){json(catalogJson)}
    routing {
        get("/health"){call.respond(mapOf("status" to "ok","stack" to "Kotlin/Ktor","cookies" to "none"))}
        get("/api/catalog"){call.respond(catalog)}
        get("/api/devices"){call.respond(catalog.devices)}
        get("/api/apps"){call.respond(catalog.apps)}
        get("/api/sections"){call.respond(catalogSections.associate{it.key to sectionCount(catalog,it)})}
        get("/api/roms"){call.respond(sectionResources(catalog,"roms").map{it.resource})}
        get("/api/guides"){call.respond(sectionResources(catalog,"guides").map{it.resource})}
        get("/api/tools"){call.respond(sectionResources(catalog,"tools").map{it.resource})}
        get("/api/filters"){call.respond(sectionEntries(catalog,"filters"))}
        get("/api/reviews"){call.respond(sectionEntries(catalog,"reviews"))}
        get("/catalog.json"){call.respond(catalog)}
        staticFiles("/",File(System.getenv("FRONTEND_PATH")?:"frontend/build/dist/wasmJs/productionExecutable"))
    }
}
fun main(args:Array<String>){
    if("--assets" in args){prepareUiAssets();return}
    if("--mirror-releases" in args){mirrorReleaseAssets();return}
    if("--sync" in args){syncCatalog();return}
    if("--mirror" in args){mirrorApps();return}
    if("--export" in args){val output=File("frontend/src/jsMain/resources/catalog.json");output.parentFile.mkdirs();output.writeText(catalogJson.encodeToString(Catalog.serializer(),loadCatalog()));println("Exported catalog for the cookie-free static site.");return}
    embeddedServer(Netty,port=(System.getenv("PORT")?:"8080").toInt(),host="0.0.0.0"){module()}.start(wait=true)
}
