import kotlinx.serialization.Serializable

@Serializable data class Resource(val title:String, val url:String, val kind:String="guide", val source:String="DietDroid", val license:String="Not specified", val mirrorUrl:String="", val sha256:String="")
@Serializable data class Device(val id:String,val name:String,val brand:String,val form:String,val description:String,val sourceUrl:String,val resources:List<Resource> = emptyList())
@Serializable data class AppRecord(val id:String,val name:String,val description:String,val category:String,val repository:String,val license:String,val version:String="",val downloadUrl:String="",val sourceArchive:String="",val sha256:String="",val bytes:Long=0,val status:String="source-only",val notes:String="")
@Serializable data class Catalog(val updated:String="",val devices:List<Device> = emptyList(),val apps:List<AppRecord> = emptyList(),val notices:List<String> = emptyList())
