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
fun loadCatalog(path:String = System.getenv("CATALOG_PATH")?:"data/catalog.json"):Catalog = catalogJson.decodeFromString(File(path).readText())
fun Application.module(catalog:Catalog = loadCatalog()) {
    install(ContentNegotiation){json(catalogJson)}
    routing {
        get("/health"){call.respond(mapOf("status" to "ok","stack" to "Kotlin/Ktor","cookies" to "none"))}
        get("/api/catalog"){call.respond(catalog)}
        get("/api/devices"){call.respond(catalog.devices)}
        get("/api/apps"){call.respond(catalog.apps)}
        get("/api/roms"){call.respond(catalog.devices.flatMap{d->d.resources.filter{it.kind=="firmware"}})}
        get("/catalog.json"){call.respond(catalog)}
        staticFiles("/",File(System.getenv("FRONTEND_PATH")?:"frontend/build/dist/js/productionExecutable"))
    }
}
fun main(args:Array<String>){
    if("--sync" in args){syncCatalog();return}
    if("--mirror" in args){mirrorApps();return}
    if("--export" in args){val output=File("frontend/src/jsMain/resources/catalog.json");output.parentFile.mkdirs();output.writeText(catalogJson.encodeToString(Catalog.serializer(),loadCatalog()));println("Exported catalog for the cookie-free static site.");return}
    embeddedServer(Netty,port=(System.getenv("PORT")?:"8080").toInt(),host="0.0.0.0"){module()}.start(wait=true)
}
