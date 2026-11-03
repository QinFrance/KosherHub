import androidx.compose.runtime.*
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.await
import kotlinx.serialization.json.Json
import org.jetbrains.compose.web.attributes.*
import org.jetbrains.compose.web.dom.*
import org.jetbrains.compose.web.renderComposable

private const val repo="https://github.com/QinFrance/KosherHub"
private val scope=MainScope()
private val json=Json { ignoreUnknownKeys=true }
private var catalog by mutableStateOf(Catalog())
private var loading by mutableStateOf(true)
private var error by mutableStateOf(false)
private var page by mutableStateOf("home")
private var query by mutableStateOf("")
private var form by mutableStateOf("all")
private var selectedDevice by mutableStateOf<Device?>(null)
private var selectedApp by mutableStateOf<AppRecord?>(null)
private var support by mutableStateOf(false)
private var accent by mutableStateOf("blue")

fun main(){
    fun navigate(){page=window.location.hash.removePrefix("#").takeIf { it in listOf("home","phones","apps","roms") } ?: "home";query="";form="all";window.scrollTo(0.0,0.0)}
    navigate();window.onhashchange={navigate()};window.onkeydown={event->if(event.key=="Escape"){selectedDevice=null;selectedApp=null;support=false};null}
    scope.launch { try { val r=window.fetch("catalog.json").await();if(!r.ok)error("Catalog unavailable");catalog=json.decodeFromString<Catalog>(r.text().await()) }catch(e:Exception){error=true}finally{loading=false} }
    renderComposable("root") { App() }
}
@Composable private fun Icon(name:String,extra:String=""){Span({classes("icon");if(extra.isNotBlank())classes(extra);attr("aria-hidden","true")}){Text(when(name){"home"->"⌂";"phones"->"▯";"apps"->"⊞";"roms"->"▧";"arrow"->"↗";"coffee"->"☕";"search"->"⌕";"close"->"×";else->name})}}
@Composable private fun GitHub(){Img("icons/github.svg",alt="",attrs={classes("github-icon");attr("aria-hidden","true")})}
@Composable private fun Route(name:String,label:String){A(href="#$name",attrs={classes("nav-item");if(page==name)classes("active");if(page==name)attr("aria-current","page")}){Icon(name);Span{Text(label)}}}
@Composable private fun App(){
 Div({classes("app-shell","theme-$accent")}){
  Aside({classes("desktop-rail")}){
   A("#home",{classes("wordmark")}){Img("icons/mark.svg",alt="",attrs={classes("mark")});Text("kosherhub");Span{Text("beta")}}
   P({classes("rail-note")}){Text("Small screens. Big possibilities.")}
   Nav{Route("home","Discover");Route("phones","Phone library");Route("apps","App shelf");Route("roms","ROMs & firmware")}
   Div({classes("rail-bottom")}){
    Div({classes("theme-picker");attr("aria-label","Choose accent color")}){listOf("blue","lilac","mint").forEach{color->Button({classes("swatch",color);if(accent==color)classes("chosen");attr("aria-label","$color accent");onClick{accent=color}}){}}}
    A(repo,attrs={classes("rail-source");attr("target","_blank");attr("rel","noopener")}){GitHub();Text("Made in the open");Icon("arrow")}
    Span({classes("tiny")}){Text("A little project, for our community.")}
   }
  }
  Div({classes("main-shell")}){
   Header({classes("topbar")}){
    A("#home",{classes("mobile-brand")}){Img("icons/mark.svg",alt="",attrs={classes("mark")});Text("kosherhub")}
    Div({classes("breadcrumb")}){Text("Your intentional tech corner");Span{Text(" / ${if(page=="home")"Discover" else page.replaceFirstChar { it.uppercase() }}")}}
    Div({classes("top-actions")}){Button({classes("coffee-button");onClick{support=true}}){Icon("coffee");Span{Text("Buy me a coffee")}};A(repo,{classes("github-button");attr("target","_blank");attr("rel","noopener");attr("aria-label","GitHub source code")}){GitHub()}}
   }
   Main{
    if(page=="home")Home() else Directory()
    Footer{
     Div{B{Text("Less noise. More intention.")};P{Text("Independent. Community-built. Always a work in progress.")}}
     Div({classes("footer-links")}){A(repo,{attr("target","_blank");attr("rel","noopener")}){Text("Source code ↗")};A("$repo/issues/new?template=contact.yml",{attr("target","_blank");attr("rel","noopener")}){Text("Say hello ↗")};Button({onClick{support=true}}){Text("☕ Buy me a coffee")}}
     P({classes("fineprint")}){Text("No accounts, cookies, trackers, or ads on this site. Device and app listings are resources, not kosher certification. Check suitability with your own provider.")}
    }
   }
  }
  Nav({classes("mobile-dock");attr("aria-label","Main navigation")}){Route("home","Discover");Route("phones","Phones");Route("apps","Apps");Route("roms","ROMs")}
  selectedDevice?.let{DeviceDialog(it)}
  selectedApp?.let{AppDialog(it)}
  if(support)Dialog("A little support. A big thank you.",{support=false}){Span({classes("support-emoji")}){Text("☕")};P{Text("Enjoying the project? A star, a useful bug report, or a kind word means a lot.")};P({classes("muted")}){Text("Coffee payments aren’t connected yet. No payment is taken here.")};A(repo,{classes("button","filled");attr("target","_blank");attr("rel","noopener")}){Text("Leave a star on GitHub ↗")}}
 }
}
@Composable private fun Home(){
 Section({classes("hero")}){
  Div({classes("hero-copy")}){Span({classes("eyebrow")}){Span({classes("live-dot")});Text("A SMALL COMMUNITY PROJECT")};H1{Text("A little more");Br();Span{Text("intentional.")}};P{Text("Find your phone. Make it useful. Keep what matters. ");Br();Text("Phone docs, Android apps, and ROMs — in one calm corner.")};Div({classes("hero-actions")}){A("#phones",{classes("button","filled")}){Text("Find your phone");Icon("→")};A("#apps",{classes("button","tonal")}){Text("Explore apps");Icon("apps")}};Div({classes("hero-footnote")}){Span{Text("Built with Kotlin & Compose")};Span{Text("✦")};Span{Text("Open source, always")}}}
  Div({classes("phone-playground");attr("aria-label","Four illustrated phone styles")}){Div({classes("shape","shape-one")});Div({classes("shape","shape-two")});listOf("bar","flip","qwerty","touch").forEachIndexed{i,type->Img("icons/$type.svg",alt="$type phone illustration",attrs={classes("hero-phone","phone-$i")})};Span({classes("doodle")}){Text("find your kind of simple ↘")}}
 }
 Section({classes("quick-nav")}){listOf(Triple("phones","Phone documentation","Know your device."),Triple("apps","The app shelf","Useful, without the noise."),Triple("roms","ROMs & firmware","A fresh start, with care.")).forEach{(id,title,desc)->A("#$id",{classes("quick-card")}){Icon(id);Div{H3{Text(title)};P{Text(desc)}};Icon("arrow")}}}
 Section({classes("section")}){
  SectionTitle("FIND YOUR EVERYDAY","Phones, with personality.","phones","See the library")
  Div({classes("phone-types")}){listOf("bar" to "Bar phones","flip" to "Flip phones","qwerty" to "QWERTY","touch" to "Touchscreen").forEach{(id,label)->Button({classes("type-tile");onClick{window.history.pushState(null,"","#phones");page="phones";query="";form=id;window.scrollTo(0.0,0.0)}}){Img("icons/$id.svg",alt="",attrs={attr("aria-hidden","true")});Span{Text(label)};Span({classes("type-count")}){Text("${catalog.devices.count{it.form==id}} in the library")}}}}
  if(loading)P({classes("state-message")}){Text("Gathering the good stuff…")}else if(error)LoadError()else Div({classes("device-grid","home-devices")}){catalog.devices.take(3).forEach{DeviceCard(it)}}
 }
 Section({classes("apps-section","section")}){SectionTitle("SMALL TOOLS. REAL USE.","A little less app overload.","apps","Open the app shelf");Div({classes("app-grid")}){catalog.apps.take(3).forEach{AppCard(it)}};if(catalog.apps.isEmpty()&&!loading)Empty("The first apps are on their way.","Only real, identifiable releases belong on this shelf.")}
 Section({classes("rom-feature")}){Div{Span({classes("eyebrow")}){Text("YOUR PHONE. YOUR PACE.")};H2{Text("A fresh start,");Br();Text("not a leap of faith.")};P{Text("Find firmware references, device-specific guides, and the details to check before you flash.")};A("#roms",{classes("button","filled")}){Text("Explore ROM resources");Icon("→")}};Div({classes("rom-illustration")}){Span{Text("{ }")};Div{Text("check model");Br();Text("back up");Br();Text("verify checksum");Br();B{Text("then explore.")}}}}
 Section({classes("community-card")}){Icon("✧");Div{H2{Text("Better when it’s shared.")};P{Text("Automatic source checks keep things fresh. Found a missing app or an update? GitHub is where we build together.")}};A("$repo/issues/new/choose",{classes("button","tonal");attr("target","_blank");attr("rel","noopener")}){Text("Suggest an app or update ↗")}}
}
@Composable private fun SectionTitle(eyebrow:String,title:String,path:String,label:String){Div({classes("section-title")}){Div{Span({classes("eyebrow")}){Text(eyebrow)};H2{Text(title)}};A("#$path",{classes("text-link")}){Text(label+" →")}}}
@Composable private fun Directory(){
 val labels=when(page){"phones"->Triple("THE PHONE LIBRARY","Get to know your phone.","Model-specific documentation, tools, and references. No guessing games.");"apps"->Triple("THE APP SHELF","Useful looks good on you.","Android tools and community builds, with clear sources and download details.");else->Triple("ROMS & FIRMWARE","A fresh start starts here.","Firmware resources grouped by exact device. Read before you flash.")}
 Section({classes("directory-heading")}){Span({classes("eyebrow")}){Text(labels.first)};H1{Text(labels.second)};P{Text(labels.third)}}
 Div({classes("directory-toolbar")}){Label(attrs={classes("search")}){Icon("search");Input(InputType.Search,attrs={value(query);attr("aria-label","Search $page");placeholder("Search $page…");onInput{query=it.value}})};if(page=="phones")Div({classes("chips")}){listOf("all","bar","flip","qwerty","touch").forEach{id->Button({classes("chip");if(form==id)classes("selected");onClick{form=id}}){Text(when(id){"all"->"All phones";"qwerty"->"QWERTY";else->id.replaceFirstChar{it.uppercase()}})}}}}
 if(page=="apps")Div({classes("chips");attr("style","margin-bottom:24px")}){listOf("all" to "All apps","hosted" to "Hosted here","cfop" to "CFOP catalog").forEach{(id,label)->Button({classes("chip");if(form==id)classes("selected");onClick{form=id}}){Text(label)}}}
 if(loading)P({classes("state-message")}){Text("Loading the library…")}else if(error)LoadError()else when(page){
  "phones"->{val rows=catalog.devices.filter{(form=="all"||it.form==form)&&(it.name+it.brand+it.description).contains(query,true)};Div({classes("device-grid")}){rows.forEach{DeviceCard(it)}};if(rows.isEmpty())Empty("Nothing here just yet.","Try a different phone style or search.")}
  "apps"->{val rows=catalog.apps.filter{(form!="hosted"||it.status=="mirrored")&&(form!="cfop"||it.id.startsWith("cfop-"))&&(it.name+it.category+it.description).contains(query,true)};Div({classes("app-grid")}){rows.forEach{AppCard(it)}};if(rows.isEmpty())Empty("No apps match that search.","Try another name or category.");Div({classes("notice")}){Text("An open-source license does not make an app kosher. Review its features and filtering requirements before installing.")}}
  else->{val devices=catalog.devices.filter{d->d.resources.any{it.kind=="firmware"}&&(d.name+d.resources.joinToString{it.title}).contains(query,true)};devices.forEach{d->Section({classes("firmware-group")}){H2{Text(d.name)};d.resources.filter{it.kind=="firmware"}.forEach{ResourceRow(it)}}};if(devices.isEmpty())Empty("No firmware matches that search.","Firmware files without redistribution permission stay at their original source.");Div({classes("notice")}){Text("Before flashing: confirm the exact hardware revision, back up your data, verify checksums, and read the recovery procedure. Changing firmware can erase data or stop a phone from starting.")}}
 }
}
@Composable private fun DeviceCard(d:Device){Button({classes("device-card");onClick{selectedDevice=d}}){Div({classes("device-art")}){Span({classes("tag")}){Text(d.form.uppercase())};Img("icons/${d.form}.svg",alt="${d.form} phone illustration")};Div({classes("card-copy")}){Span({classes("eyebrow")}){Text(d.brand)};H3{Text(d.name)};P{Text(d.description)};Div({classes("card-tail")}){Text("${d.resources.size} resources");Span{Text("Read the docs →")}}}}}
@Composable private fun AppCard(a:AppRecord){Button({classes("app-card");onClick{selectedApp=a}}){Div({classes("app-symbol")}){Text(if(a.category=="Keyboard")"Aa"else if(a.category=="Calculator")"="else"✦")};Div({classes("app-card-info")}){Span({classes("app-category")}){Text(a.category)};H3{Text(a.name)};P{Text(a.description)};Div({classes("app-meta")}){Span{Text(a.license)};Span{Text(if(a.version.isBlank())"Release source"else a.version)}};if(a.status=="mirrored")Span({classes("download-label")}){Text("↓ APK hosted here")}else Span({classes("download-label","muted")}){Text("View release details →")}}}}
@Composable private fun ResourceRow(r:Resource){A(if(r.mirrorUrl.isNotBlank())r.mirrorUrl else r.url,attrs={classes("resource-row");attr("target","_blank");attr("rel","noopener")}){Icon(if(r.kind=="firmware")"roms"else"↗");Div{B{Text(r.title)};Span{Text(if(r.mirrorUrl.isNotBlank())"Hosted by KosherHub"else"Source reference · ${r.source}")}};Icon("arrow")}}
@Composable private fun DeviceDialog(d:Device){Dialog(d.name,{selectedDevice=null}){P{Text(d.description)};Div({classes("notice")}){Text("Illustrations show the form factor, not an exact product render. Check the exact model and your kosher/filtering requirements with your provider.")};d.resources.groupBy{it.kind}.forEach{(kind,rows)->H3({classes("dialog-heading")}){Text(kind.replaceFirstChar{it.uppercase()})};rows.forEach{ResourceRow(it)}};if(d.resources.isEmpty())P{Text("More device-specific resources are being checked.")};A(d.sourceUrl,{classes("text-link");attr("target","_blank");attr("rel","noopener")}){Text("Source & attribution: DietDroid ↗")}}}
@Composable private fun AppDialog(a:AppRecord){Dialog(a.name,{selectedApp=null}){P{Text(a.description)};Div({classes("detail-meta")}){Span{Text("License · ${a.license}")};Span{Text("Version · ${a.version.ifBlank{"See release"}}")}};if(a.status=="mirrored"){A(a.downloadUrl,{classes("button","filled")}){Text("Download APK ↓")};A(a.downloadUrl.replace(".apk",".LICENSE.txt"),{classes("text-link");attr("target","_blank");attr("rel","noopener")}){Text("License ↗")};A(a.downloadUrl.replace(".apk",".NOTICE.txt"),{classes("text-link");attr("target","_blank");attr("rel","noopener")}){Text("Attribution ↗")};if(a.sourceArchive.isNotBlank())A(a.sourceArchive,{classes("button","tonal")}){Text("Corresponding source ↓")}}else if(a.downloadUrl.isNotBlank()){A(a.downloadUrl,{classes("button","tonal");attr("target","_blank");attr("rel","noopener")}){Text(if(a.status=="community-release")"Source APK release ↗"else"Official APK release ↗")}};if(a.sha256.isNotBlank())P({classes("checksum")}){Text("SHA-256: ${a.sha256}")};P({classes("notice")}){Text(a.notes.ifBlank{"Community resource, not kosher certification. Check suitability and requested permissions before installing."})};A(a.repository,{classes("text-link");attr("target","_blank");attr("rel","noopener")}){Text(if(a.status=="community-release")"Source catalog & rights ↗"else"Original project & license ↗")}}}
@Composable private fun Dialog(title:String,close:()->Unit,body:@Composable ()->Unit){Div({classes("dialog-overlay");onClick{close()}}){Div({classes("dialog");attr("role","dialog");attr("aria-modal","true");attr("aria-label",title);onClick{it.stopPropagation()}}){Button({classes("close-button");attr("aria-label","Close dialog");onClick{close()}}){Text("×")};H2{Text(title)};body()}}}
@Composable private fun Empty(title:String,description:String){Div({classes("empty")}){Icon("✧");H3{Text(title)};P{Text(description)}}}
@Composable private fun LoadError(){Div({classes("empty")}){H3{Text("The library needs a moment.")};P{Text("Please refresh the page to try again.")};Button({classes("button","tonal");onClick{window.location.reload()}}){Text("Try again")}}}
