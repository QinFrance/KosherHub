import kotlinx.serialization.json.*
import java.io.File

private const val CFOP="https://raw.githubusercontent.com/cfopuser/app-store/main/"
fun cfopRecord(id:String,metadata:JsonObject,releases:JsonArray):AppRecord? {
 require(Regex("[a-z0-9-]+").matches(id))
 val release=releases.map{it.jsonObject}.filter{it["tag_name"]?.jsonPrimitive?.content?.startsWith("$id-v")==true}.maxByOrNull{it["published_at"]?.jsonPrimitive?.content?:""}?:return null
 val asset=release["assets"]?.jsonArray?.map{it.jsonObject}?.firstOrNull{it["name"]?.jsonPrimitive?.content?.endsWith(".apk")==true}?:return null
 val tag=release["tag_name"]!!.jsonPrimitive.content
 val url=asset["browser_download_url"]!!.jsonPrimitive.content
 require(url.startsWith("https://github.com/cfopuser/app-store/releases/download/"))
 val name=metadata["name"]?.jsonPrimitive?.content?:id
 return AppRecord(id="cfop-$id",name=name,description="A CFOP-maintained Android build of $name. Review the source release and device requirements.",category=metadata["category"]?.jsonPrimitive?.content?:"Community",repository="https://github.com/cfopuser/app-store",license="Binary rights unverified",version=tag,downloadUrl=url,sha256=asset["digest"]?.jsonPrimitive?.contentOrNull?.removePrefix("sha256:")?:"",bytes=asset["size"]?.jsonPrimitive?.longOrNull?:0,status="community-release",notes="CFOP community-modified build, not an official publisher release. Kosher suitability and binary redistribution rights have not been verified by KosherHub. Factual catalog metadata is attributed to cfopuser/app-store (CC-BY-NC-SA-4.0); that catalog license does not establish rights to third-party APKs. This APK is not mirrored by KosherHub.")
}
fun mergeCfop(catalog:Catalog):Catalog = try {
 val ids=catalogJson.parseToJsonElement(request(CFOP+"apps.json").decodeToString()).jsonArray.map{it.jsonPrimitive.content}
 val releases=catalogJson.parseToJsonElement(request(CFOP+"releases.json").decodeToString()).jsonArray
 val apps=catalog.apps.associateBy{it.id}.toMutableMap();val imported=mutableListOf<AppRecord>()
 for(id in ids)try{val config=catalogJson.parseToJsonElement(request(CFOP+"apps/$id/app.json").decodeToString()).jsonObject;cfopRecord(id,config["metadata"]!!.jsonObject,releases)?.let{apps[it.id]=it;imported+=it}}catch(e:Exception){println("Keeping last known CFOP record for $id")}
 File("data/cfop-catalog.json").writeText(catalogJson.encodeToString(Catalog.serializer(),Catalog(updated=catalog.updated,apps=imported,notices=listOf("Metadata source: https://github.com/cfopuser/app-store · CC-BY-NC-SA-4.0. No binary rights granted or implied."))))
 println("Refreshed ${imported.size} CFOP community app records; no binaries mirrored.")
 catalog.copy(apps=apps.values.toList(),notices=(catalog.notices+"CFOP community app metadata: cfopuser/app-store, CC-BY-NC-SA-4.0. Third-party APK rights are separate and unverified.").distinct())
}catch(e:Exception){println("CFOP source unavailable; retaining last known catalog.");catalog}
