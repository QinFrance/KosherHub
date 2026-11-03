import java.io.File
import java.security.MessageDigest
import java.net.URI

fun sha256(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
fun mirrorApps(){
 val catalog=loadCatalog();val registry=sources().associateBy{it.id};val root=File("frontend/src/jsMain/resources/downloads");root.mkdirs()
 val apps=catalog.apps.map{app->val source=registry[app.id]?:return@map app
  if(!source.mirrorApproved||source.license !in setOf("Apache-2.0","MIT","BSD-3-Clause"))return@map app
  try{
   require(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+").matches(source.repository));require(app.status=="mirrored"||app.downloadUrl.startsWith("https://github.com/${source.repository}/releases/download/"))
   val tag=app.version;require(Regex("[A-Za-z0-9_.-]+").matches(tag));val license=request("https://raw.githubusercontent.com/${source.repository}/$tag/LICENSE").decodeToString()
   require((source.license=="Apache-2.0"&&license.contains("Apache License"))||(source.license=="MIT"&&license.contains("Permission is hereby granted"))||(source.license=="BSD-3-Clause"&&license.contains("Redistribution"))){"Upstream license no longer matches approval"}
   val slug="${app.id}-$tag";val bytes=if(app.status=="mirrored"&&File(root,"$slug.apk").exists())File(root,"$slug.apk").readBytes()else request("https://github.com/${source.repository}/releases/download/$tag/"+source.assetName.ifBlank{File(URI(app.downloadUrl).path).name},32_000_000)
   require(bytes.size>=4&&bytes[0]==80.toByte()&&bytes[1]==75.toByte()){ "Not an APK ZIP container" }
   val hash=sha256(bytes);if(app.sha256.isNotBlank())require(hash==app.sha256){"APK checksum changed"}
   File(root,"$slug.apk").writeBytes(bytes);File(root,"$slug.LICENSE.txt").writeText(license);File(root,"$slug.NOTICE.txt").writeText("${app.name}\nUpstream: ${app.repository}\nVersion: $tag\n${source.attribution}\nLicense: ${source.license}\nUnmodified upstream APK, redistributed by KosherHub.\nNo kosher certification is asserted.\nSource: https://github.com/${source.repository}/tree/$tag\nSHA-256: $hash\n")
   println("Mirrored ${app.id} with checksum and license notices.");app.copy(downloadUrl="downloads/$slug.apk",sha256=hash,bytes=bytes.size.toLong(),status="mirrored",notes=app.notes+" License and attribution: downloads/$slug.LICENSE.txt and downloads/$slug.NOTICE.txt.")
  }catch(e:Exception){println("Mirror skipped for ${app.id}: ${e.message}");app}
 }
 File("data/catalog.json").writeText(catalogJson.encodeToString(Catalog.serializer(),catalog.copy(apps=apps)))
}
