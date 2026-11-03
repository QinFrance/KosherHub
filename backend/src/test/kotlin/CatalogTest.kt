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
}
