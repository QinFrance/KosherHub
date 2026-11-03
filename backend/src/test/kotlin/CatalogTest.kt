import kotlin.test.*
import io.ktor.server.testing.*
import io.ktor.http.*
import io.ktor.client.request.*
import kotlinx.serialization.json.Json
class CatalogTest {
 @Test fun publicApiNeedsNoCookie()=testApplication {
  application{module(Catalog(devices=listOf(Device("test","Test phone","Test","bar","Test description","https://dietdroid.com/"))))}
  val response=client.get("/api/catalog");assertEquals(HttpStatusCode.OK,response.status);assertNull(response.headers[HttpHeaders.SetCookie])
  assertEquals(HttpStatusCode.OK,client.get("/health").status)
 }
 @Test fun metadataRoundTrip(){val source=Catalog(apps=listOf(AppRecord("keyboard","Keyboard","Useful","Keyboard","https://github.com/example/app","Apache-2.0")));assertEquals(source,catalogJson.decodeFromString<Catalog>(catalogJson.encodeToString(Catalog.serializer(),source)))}
 @Test fun checksumsAreStable(){assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",sha256("abc".toByteArray()))}
 @Test fun cfopSelectsNewestAndNeverGrantsBinaryRights(){
  val releases=kotlinx.serialization.json.Json.parseToJsonElement("""[{"tag_name":"test-v1","published_at":"2025-01-01T00:00:00Z","assets":[{"name":"test.apk","browser_download_url":"https://github.com/cfopuser/app-store/releases/download/test-v1/test.apk","size":10}]},{"tag_name":"test-v2","published_at":"2026-01-01T00:00:00Z","assets":[{"name":"test.apk","browser_download_url":"https://github.com/cfopuser/app-store/releases/download/test-v2/test.apk","size":20}]}]""") as kotlinx.serialization.json.JsonArray
  val meta=kotlinx.serialization.json.Json.parseToJsonElement("""{"name":"Test app","category":"Tools"}""") as kotlinx.serialization.json.JsonObject
  val record=cfopRecord("test",meta,releases)!!
  assertEquals("test-v2",record.version);assertEquals("community-release",record.status);assertEquals("Binary rights unverified",record.license)
 }
}
