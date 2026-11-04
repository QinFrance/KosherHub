@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, kotlin.js.ExperimentalWasmJsInterop::class)

import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import org.kosherhub.resources.*

private const val repository = "https://github.com/QinFrance/KosherHub"
private val json = Json { ignoreUnknownKeys = true }
private val sections = catalogSections.map { it.key }
private data class Route(val section: String = "home", val id: String = "", val resource: Int? = null, val entry: Boolean = false) {
    val hash: String get() = buildString {
        append("#"); append(section)
        if (id.isNotEmpty()) { if(entry) append("/entry"); append("/"); append(id); resource?.let { append("/"); append(it) } }
    }
    val parent: Route get() = if (resource != null) copy(resource = null) else if (id.isNotEmpty()) Route(section) else Route()
}
private fun routeFromHash(): Route {
    val parts = window.location.hash.removePrefix("#").split('/')
    val section=parts.firstOrNull().takeIf { it in sections || it in listOf("home","apps") } ?: "home"
    val entry=parts.getOrNull(1)=="entry"
    return Route(section,parts.getOrElse(if(entry)2 else 1){""}.take(100),if(entry)null else parts.getOrNull(2)?.toIntOrNull(),entry)
}
private fun go(route: Route) { window.location.hash = route.hash }
private fun activeSection(section: String) = if(section=="apps") "tools" else section
private fun open(url: String) {
    if (url.startsWith("https://") || url.startsWith("downloads/")) window.open(url, "_blank", "noopener,noreferrer")
}
private fun title(section: String) = if(section=="apps") "Apps" else CatalogSection.fromKey(section)?.label ?: "Explore"
private fun symbol(section: String) = when(activeSection(section)) {
    "phones" -> KosherSymbols.Phone; "roms" -> KosherSymbols.Firmware; "guides" -> KosherSymbols.Document
    "tools" -> KosherSymbols.Settings; "filters" -> KosherSymbols.Filter; "reviews" -> KosherSymbols.Bookmark
    else -> KosherSymbols.Home
}

/** Every Material role belongs to one of two families: blue or achromatic neutral. */
private fun palette(dark: Boolean): ColorScheme {
    val blue = if (dark) Color(0xFFAEC6FF) else Color(0xFF2857C5)
    val onBlue = if (dark) Color(0xFF102D68) else Color.White
    val container = if (dark) Color(0xFF243D70) else Color(0xFFE1EAFF)
    val onContainer = if (dark) Color(0xFFE1EAFF) else Color(0xFF15336C)
    val surface = if (dark) Color(0xFF16181C) else Color(0xFFFAFAFA)
    val raised = if (dark) Color(0xFF202226) else Color.White
    val text = if (dark) Color(0xFFE8E8E8) else Color(0xFF242424)
    val muted = if (dark) Color(0xFFB7B7B7) else Color(0xFF626262)
    val line = if (dark) Color(0xFF414141) else Color(0xFFDEDEDE)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary=blue, onPrimary=onBlue, primaryContainer=container, onPrimaryContainer=onContainer,
        inversePrimary=if(dark) Color(0xFF2857C5) else Color(0xFFAEC6FF),
        secondary=blue, onSecondary=onBlue, secondaryContainer=container, onSecondaryContainer=onContainer,
        tertiary=blue, onTertiary=onBlue, tertiaryContainer=container, onTertiaryContainer=onContainer,
        error=blue, onError=onBlue, errorContainer=container, onErrorContainer=onContainer,
        background=surface, onBackground=text, surface=surface, onSurface=text,
        surfaceVariant=raised, onSurfaceVariant=muted, surfaceTint=blue,
        inverseSurface=text, inverseOnSurface=surface, outline=muted, outlineVariant=line,
        scrim=Color(0xFF16181C), surfaceBright=raised, surfaceDim=surface,
        surfaceContainerLowest=raised, surfaceContainerLow=raised, surfaceContainer=raised,
        surfaceContainerHigh=if(dark) Color(0xFF292B2F) else Color(0xFFF0F0F0),
        surfaceContainerHighest=if(dark) Color(0xFF333539) else Color(0xFFE8E8E8)
    )
}

@OptIn(ExperimentalComposeUiApi::class)
fun startCatalog() {
    document.getElementById("loading")?.remove()
    ComposeViewport(document.body!!, configure = { isA11YEnabled = true }) { CatalogApp() }
}


@Composable private fun catalogTypography(): Typography {
    val family=FontFamily(
        Font(Res.font.catalog_sans,FontWeight.Normal),
        Font(Res.font.catalog_sans,FontWeight.Medium),
        Font(Res.font.catalog_sans,FontWeight.SemiBold),
        Font(Res.font.catalog_sans,FontWeight.Bold)
    )
    return Typography().run { Typography(
        displayLarge=displayLarge.copy(fontFamily=family), displayMedium=displayMedium.copy(fontFamily=family), displaySmall=displaySmall.copy(fontFamily=family),
        headlineLarge=headlineLarge.copy(fontFamily=family), headlineMedium=headlineMedium.copy(fontFamily=family), headlineSmall=headlineSmall.copy(fontFamily=family),
        titleLarge=titleLarge.copy(fontFamily=family), titleMedium=titleMedium.copy(fontFamily=family), titleSmall=titleSmall.copy(fontFamily=family),
        bodyLarge=bodyLarge.copy(fontFamily=family), bodyMedium=bodyMedium.copy(fontFamily=family), bodySmall=bodySmall.copy(fontFamily=family),
        labelLarge=labelLarge.copy(fontFamily=family), labelMedium=labelMedium.copy(fontFamily=family), labelSmall=labelSmall.copy(fontFamily=family)
    ) }
}

@Composable private fun CatalogApp() {
    var route by remember { mutableStateOf(routeFromHash()) }
    var catalog by remember { mutableStateOf(Catalog()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var retry by remember { mutableStateOf(0) }
    var dark by remember { mutableStateOf(window.matchMedia("(prefers-color-scheme: dark)").matches) }
    var overlay by remember { mutableStateOf("") }
    val queries = remember { mutableStateMapOf<String,String>() }
    val filters = remember { mutableStateMapOf<String,String>() }
    DisposableEffect(Unit) {
        val listener: (org.w3c.dom.events.Event) -> Unit = { route=routeFromHash(); overlay="" }
        window.addEventListener("hashchange", listener)
        onDispose { window.removeEventListener("hashchange", listener) }
    }
    LaunchedEffect(retry) {
        loading=true; failed=false
        try {
            val response=window.fetch("catalog.json").await<org.w3c.fetch.Response>()
            check(response.ok)
            catalog=json.decodeFromString<Catalog>(response.text().await<kotlin.js.JsString>().toString())
        } catch (_: Exception) { failed=true } finally { loading=false }
    }
    LaunchedEffect(route) { document.title = "${title(route.section)} · KosherHub" }
    MaterialExpressiveTheme(colorScheme=palette(dark), typography=catalogTypography()) {
        Surface(Modifier.fillMaxSize(), color=MaterialTheme.colorScheme.background) {
            BoxWithConstraints {
                val compact=maxWidth < 760.dp
                Row(Modifier.fillMaxSize()) {
                    if (!compact) Sidebar(route, catalog, { go(it) }, { overlay=it })
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        Header(route, compact, dark, { dark=!dark }, { go(route.parent) }, { overlay=it })
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            when {
                                loading -> Box(Modifier.fillMaxSize(), contentAlignment=Alignment.Center) { CircularProgressIndicator(Modifier.size(32.dp)) }
                                failed -> EmptyState(KosherSymbols.Refresh, "Couldn’t load the catalog") { FilledTonalButton(onClick={ retry++ }) { Text("Retry") } }
                                else -> AnimatedContent(route, transitionSpec={ androidx.compose.animation.fadeIn(tween(130)) togetherWith androidx.compose.animation.fadeOut(tween(90)) }, label="page") { current ->
                                    val device=catalog.devices.find { it.id==current.id }
                                    val app=catalog.apps.find { it.id==current.id }
                                    val entry=catalog.entries.find { it.id==current.id && it.section==current.section }
                                    when {
                                        current.entry && entry!=null -> EntryDetail(entry,catalog,compact)
                                        !current.entry && current.id.isNotEmpty() && current.section=="apps" && app!=null -> AppDetail(app, compact, { overlay="app:${app.id}" })
                                        !current.entry && current.id.isNotEmpty() && device!=null && current.resource!=null -> ResourceDetail(device, current.resource, compact)
                                        !current.entry && current.id.isNotEmpty() && device!=null -> PhoneDetail(device, current.section, compact)
                                        current.id.isNotEmpty() -> EmptyState(KosherSymbols.Search, "Not found") { TextButton(onClick={go(current.parent)}) { Text("Back") } }
                                        current.section=="home" -> Home(catalog, compact)
                                        else -> Directory(current.section,catalog,compact,queries[current.section].orEmpty(),filters[current.section] ?: if(current.section=="tools") "downloads" else "all", {queries[current.section]=it}, {filters[current.section]=it})
                                    }
                                }
                            }
                        }
                        if(compact) BottomNavigation(route) { go(it) }
                    }
                }
            }
            if(overlay.isNotEmpty()) {
                val app=catalog.apps.find { "app:${it.id}"==overlay }
                AlertDialog(
                    onDismissRequest={overlay=""},
                    icon={ Glyph(if(overlay=="coffee") KosherSymbols.Coffee else if(overlay=="contact") KosherSymbols.Mail else KosherSymbols.Info, null, 28) },
                    title={Text(when(overlay){"coffee"->"Buy me a coffee";"contact"->"Contact";else->"Source & details"})},
                    text={Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState()), verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        when {
                            overlay=="coffee" -> Text("Coffee payments aren’t connected yet. Thanks for supporting this little project.")
                            overlay=="contact" -> TextButton(onClick={open("$repository/issues/new?template=contact.yml")}) { Glyph(KosherSymbols.Mail,null); Spacer(Modifier.width(8.dp)); Text("Open a GitHub issue") }
                            app!=null -> {
                                Text(app.license)
                                Text(app.notes.substringBefore(" License and attribution:").ifBlank { "Check device compatibility and permissions before installing." })
                                if(app.sha256.isNotBlank()) SelectionContainer { Text("SHA-256\n${app.sha256}",style=MaterialTheme.typography.bodySmall) }
                                TextButton(onClick={open(app.repository)}) { Glyph(KosherSymbols.Github,null); Spacer(Modifier.width(8.dp)); Text("Original project") }
                                if(app.status=="mirrored") TextButton(onClick={open(app.downloadUrl.replace(".apk",".LICENSE.txt"))}) { Glyph(KosherSymbols.Document,null); Spacer(Modifier.width(8.dp)); Text("License") }
                            }
                        }
                    }},
                    confirmButton={TextButton(onClick={overlay=""}) {Text("Done")}}
                )
            }
        }
    }
}

@Composable private fun Glyph(path: SymbolPath, label: String?, size: Int=22, tint: Color=LocalContentColor.current) {
    val vector=remember(path) {
        ImageVector.Builder(defaultWidth=24.dp,defaultHeight=24.dp,viewportWidth=path.viewport,viewportHeight=path.viewport).apply {
            group(translationY=path.translateY) { path.paths.forEach { data -> addPath(PathParser().parsePathString(data).toNodes(), fill=SolidColor(Color.White)) } }
        }.build()
    }
    Icon(vector,contentDescription=label,modifier=Modifier.size(size.dp),tint=tint)
}
@Composable private fun Action(path: SymbolPath, label: String, click: ()->Unit) {
    IconButton(onClick=click,modifier=Modifier.size(44.dp)) { Glyph(path,label) }
}
@Composable private fun Brand(compact: Boolean=false) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.primary,modifier=Modifier.size(if(compact)32.dp else 38.dp)) {
            Box(contentAlignment=Alignment.Center) {Glyph(KosherSymbols.Logo,null,if(compact)23 else 28,MaterialTheme.colorScheme.onPrimary)}
        }
        Text("kosherhub",fontSize=if(compact)20.sp else 23.sp,fontWeight=FontWeight.Bold,letterSpacing=(-0.8).sp)
    }
}
@Composable private fun Sidebar(route: Route,catalog: Catalog,navigate:(Route)->Unit,overlay:(String)->Unit) {
    Column(Modifier.width(208.dp).fillMaxHeight().padding(horizontal=20.dp,vertical=26.dp)) {
        Box(Modifier.clickable {navigate(Route())}.padding(bottom=40.dp)) {Brand()}
        sections.forEach { section ->
            val selected=activeSection(route.section)==section
            val count=sectionCount(catalog,section)
            Surface(onClick={navigate(Route(section))},shape=RoundedCornerShape(18.dp),color=if(selected)MaterialTheme.colorScheme.primaryContainer else Color.Transparent,modifier=Modifier.fillMaxWidth().padding(bottom=8.dp)) {
                Row(Modifier.padding(horizontal=14.dp,vertical=15.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(13.dp)) {
                    Glyph(symbol(section),null,tint=if(selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(title(section),style=MaterialTheme.typography.labelLarge,modifier=Modifier.weight(1f),fontWeight=if(selected)FontWeight.Bold else FontWeight.Medium)
                    if(count>0) Text("$count",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
            Action(KosherSymbols.Github,"GitHub source") {open(repository)}
            Action(KosherSymbols.Mail,"Contact") {overlay("contact")}
            Action(KosherSymbols.Coffee,"Buy me a coffee") {overlay("coffee")}
        }
    }
}
@Composable private fun Header(route: Route, compact:Boolean,dark:Boolean,toggle:()->Unit,back:()->Unit,overlay:(String)->Unit) {
    var menu by remember {mutableStateOf(false)}
    Row(Modifier.fillMaxWidth().height(if(compact)68.dp else 80.dp).padding(horizontal=if(compact)12.dp else 30.dp),verticalAlignment=Alignment.CenterVertically) {
        if(route.section!="home" || route.id.isNotEmpty()) {Action(KosherSymbols.Back,"Back to ${title(route.parent.section)}",back);Spacer(Modifier.width(8.dp))}
        if(compact && route.section=="home") Brand(true)
        else Text(title(route.section),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        Action(if(dark)KosherSymbols.Sun else KosherSymbols.Moon,if(dark)"Light theme" else "Dark theme",toggle)
        if(compact) Box {
            Action(KosherSymbols.More,"More") {menu=true}
            DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                DropdownMenuItem(text={Text("Source")},leadingIcon={Glyph(KosherSymbols.Github,null)},onClick={menu=false;open(repository)})
                DropdownMenuItem(text={Text("Contact")},leadingIcon={Glyph(KosherSymbols.Mail,null)},onClick={menu=false;overlay("contact")})
                DropdownMenuItem(text={Text("Buy me a coffee")},leadingIcon={Glyph(KosherSymbols.Coffee,null)},onClick={menu=false;overlay("coffee")})
            }
        }
    }
}
@Composable private fun BottomNavigation(route:Route,navigate:(Route)->Unit) {
    var more by remember {mutableStateOf(false)}
    NavigationBar(containerColor=MaterialTheme.colorScheme.surfaceContainerLow,tonalElevation=0.dp) {
        val navigationRow=this
        listOf("phones","roms","guides").forEach { section -> NavigationBarItem(selected=route.section==section,onClick={navigate(Route(section))},icon={Glyph(symbol(section),null)},label={Text(title(section))}) }
        Box(Modifier.weight(1f)) {
            with(navigationRow) { NavigationBarItem(selected=activeSection(route.section) in listOf("tools","filters","reviews"),onClick={more=true},icon={Glyph(KosherSymbols.Apps,null)},label={Text("More")},modifier=Modifier.fillMaxWidth()) }
            DropdownMenu(expanded=more,onDismissRequest={more=false}) {
                listOf("tools","filters","reviews").forEach { section -> DropdownMenuItem(text={Text(title(section))},leadingIcon={Glyph(symbol(section),null)},onClick={more=false;navigate(Route(section))}) }
            }
        }
    }
}

@Composable private fun Home(catalog:Catalog,compact:Boolean) {
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=if(compact)20.dp else 36.dp,end=if(compact)20.dp else 36.dp,bottom=28.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
        item {
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Your phone. Your way.",style=if(compact)MaterialTheme.typography.headlineLarge else MaterialTheme.typography.displaySmall,fontWeight=FontWeight.Bold,letterSpacing=(-1.1).sp)
                Text("Explore the library.",style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                sections.chunked(if(compact)2 else 3).forEach { row ->
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {row.forEach {section ->CategoryTile(section,catalog,Modifier.weight(1f),compact)}}
                }
            }
        }
        item {SectionHeading("Tools",{go(Route("tools"))})}
        item {
            val top=catalog.apps.filter {it.status=="mirrored"}.take(3).ifEmpty{catalog.apps.take(3)}
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {top.forEach{AppTile(it)}}
        }
        item {SectionHeading("Phones",{go(Route("phones"))})}
        item {
            if(compact) Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {catalog.devices.take(3).forEach{PhoneTile(it,true)}}
            else Row(horizontalArrangement=Arrangement.spacedBy(14.dp)) {catalog.devices.take(3).forEach{PhoneTile(it,false,Modifier.weight(1f))}}
        }
    }
}
@Composable private fun CategoryTile(section:String,catalog:Catalog,modifier:Modifier,compact:Boolean) {
    val count=sectionCount(catalog,section)
    Card(onClick={go(Route(section))},modifier=modifier,colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer),shape=RoundedCornerShape(if(compact)24.dp else 28.dp)) {
        Column(Modifier.padding(if(compact)16.dp else 24.dp).height(if(compact)100.dp else 112.dp),verticalArrangement=Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {Glyph(symbol(section),null,if(compact)26 else 32,MaterialTheme.colorScheme.onPrimaryContainer);if(!compact)Glyph(KosherSymbols.Forward,null,tint=MaterialTheme.colorScheme.onPrimaryContainer)}
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.SpaceBetween) {Text(title(section),style=if(compact)MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.onPrimaryContainer);Text("$count",style=if(compact)MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.onPrimaryContainer)}
        }
    }
}
@Composable private fun SectionHeading(label:String,click:()->Unit) {Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {Text(label,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));Action(KosherSymbols.Forward,"All $label",click)}}

@Composable private fun Directory(section:String,catalog:Catalog,compact:Boolean,query:String,filter:String,search:(String)->Unit,select:(String)->Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal=if(compact)20.dp else 36.dp)) {
        if(!compact) Text(title(section),style=MaterialTheme.typography.displaySmall,fontWeight=FontWeight.Bold,modifier=Modifier.padding(bottom=20.dp))
        OutlinedTextField(value=query,onValueChange=search,placeholder={Text("Search")},leadingIcon={Glyph(KosherSymbols.Search,null)},trailingIcon={if(query.isNotEmpty())Action(KosherSymbols.Close,"Clear search"){search("")}},singleLine=true,shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth().heightIn(min=56.dp).semantics {contentDescription="Search ${title(section)}"},colors=OutlinedTextFieldDefaults.colors(unfocusedBorderColor=MaterialTheme.colorScheme.outlineVariant))
        val options=when(section) {
            "phones" -> listOf("all" to "All","bar" to "Bar","flip" to "Flip","touch" to "Touch")
            "tools" -> listOf("downloads" to "Downloads","apps" to "Apps","all" to "All","hosted" to "Hosted")
            "apps" -> listOf("all" to "All","hosted" to "Hosted","cfop" to "CFOP")
            "roms" -> listOf("all" to "All","hosted" to "Hosted")
            else -> emptyList()
        }
        if(options.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            options.forEach{(id,label)->FilterChip(selected=filter==id,onClick={select(id)},label={Text(label)},leadingIcon=if(filter==id){{Glyph(KosherSymbols.Check,null,16)}}else null)}
        } else Spacer(Modifier.height(16.dp))
        if(section=="phones") {
            val devices=catalog.devices.filter{(filter=="all"||it.form==filter)&&(it.name+it.brand).contains(query,true)}
            if(devices.isEmpty())EmptyState(KosherSymbols.Search,"No results") else LazyVerticalGrid(columns=GridCells.Adaptive(if(compact)300.dp else 250.dp),contentPadding=PaddingValues(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(14.dp),horizontalArrangement=Arrangement.spacedBy(16.dp)) {items(devices,key={it.id}){PhoneTile(it,compact)}}
        } else {
            val apps=sectionApps(catalog,section).filter {
                section!="tools" || filter!="downloads"
            }.filter { (filter!="hosted"||it.status=="mirrored")&&(filter!="cfop"||it.id.startsWith("cfop-"))&&(it.name+it.category).contains(query,true) }
            val resources=if(section=="apps"||(section=="tools"&&filter=="apps"))emptyList() else sectionResources(catalog,section).filter {(filter!="hosted"||it.resource.mirrorUrl.isNotEmpty())&&(it.device.name+it.resource.title).contains(query,true)}
            val entries=if(section=="apps"||(section=="tools"&&filter in listOf("apps","downloads","hosted")))emptyList() else sectionEntries(catalog,section).filter {(it.title+it.summary+it.body).contains(query,true)}
            if(apps.isEmpty()&&resources.isEmpty()&&entries.isEmpty()) {
                EmptyState(symbol(section),if(query.isNotEmpty()||filter!="all")"No results" else when(section){"filters"->"No filters yet";"reviews"->"No reviews yet";else->"No content yet"})
            } else LazyVerticalGrid(columns=GridCells.Adaptive(if(compact)300.dp else 330.dp),contentPadding=PaddingValues(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                items(apps,key={"app:${it.id}"}){AppTile(it)}
                items(resources,key={"resource:${it.routeKey}"}){SectionResourceTile(it,section)}
                items(entries,key={"entry:${it.id}"}){EntryTile(it)}
            }
        }
    }
}
@Composable private fun SectionResourceTile(reference:SectionResource,section:String) {
    Card(onClick={go(Route(section,reference.device.id,reference.resourceIndex))},shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow),modifier=Modifier.fillMaxWidth().heightIn(min=100.dp)) {
        Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            IconTile(symbol(section))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {Text(reference.resource.title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis);Text(reference.device.name,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            Glyph(if(reference.resource.mirrorUrl.isNotEmpty())KosherSymbols.Download else KosherSymbols.Chevron,null,20,MaterialTheme.colorScheme.primary)
        }
    }
}
@Composable private fun EntryTile(entry:ContentEntry) {
    Card(onClick={go(Route(entry.section,entry.id,entry=true))},shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow),modifier=Modifier.fillMaxWidth().heightIn(min=100.dp)) {
        Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            IconTile(symbol(entry.section));Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){Text(entry.title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold);if(entry.summary.isNotEmpty())Text(entry.summary,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)};Glyph(KosherSymbols.Chevron,null,20)
        }
    }
}
@Composable private fun EntryDetail(entry:ContentEntry,catalog:Catalog,compact:Boolean) {
    DetailLayout(compact) {
        Glyph(symbol(entry.section),null,44,MaterialTheme.colorScheme.primary)
        Text(entry.title,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
        if(entry.summary.isNotEmpty())Text(entry.summary,style=MaterialTheme.typography.bodyLarge)
        if(entry.body.isNotEmpty())SelectionContainer{Text(entry.body)}
        catalog.devices.find{it.id==entry.deviceId}?.let{device->FilledTonalButton(onClick={go(Route("phones",device.id))}){Glyph(KosherSymbols.Phone,null);Spacer(Modifier.width(8.dp));Text(device.name)}}
        if(entry.url.isNotEmpty())TextButton(onClick={open(entry.url)}){Glyph(KosherSymbols.External,null);Spacer(Modifier.width(8.dp));Text("Website")}
        if(entry.source.isNotEmpty())Text(entry.source,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun IconTile(icon:SymbolPath,size:Int=48) {Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.primaryContainer,modifier=Modifier.size(size.dp)){Box(contentAlignment=Alignment.Center){Glyph(icon,null,24,MaterialTheme.colorScheme.onPrimaryContainer)}}}

@Composable private fun AppLogo(app: AppRecord, size: Int = 48) {
    val resource = when (app.id) {
        "simple-keyboard" -> Res.drawable.app_simple_keyboard
        "fossify-calculator" -> Res.drawable.app_fossify_calculator
        "cfop-bezeq" -> Res.drawable.app_bezeq
        "cfop-bit" -> Res.drawable.app_bit
        "cfop-egg" -> Res.drawable.app_egg
        "cfop-hopon" -> Res.drawable.app_hopon
        "cfop-meld" -> Res.drawable.app_meld
        "cfop-metrolist" -> Res.drawable.app_metrolist
        "cfop-mizrahi" -> Res.drawable.app_mizrahi
        "cfop-osmand" -> Res.drawable.app_osmand
        "cfop-sealplus" -> Res.drawable.app_sealplus
        "cfop-spotify" -> Res.drawable.app_spotify
        "cfop-termux" -> Res.drawable.app_termux
        "cfop-waze" -> Res.drawable.app_waze
        "cfop-whatsapp" -> Res.drawable.app_whatsapp
        "cfop-yahav" -> Res.drawable.app_yahav
        else -> null
    }
    if (resource == null) IconTile(KosherSymbols.Apps,size) else Image(
        painter = painterResource(resource),
        contentDescription = null,
        modifier = Modifier.size(size.dp).clip(RoundedCornerShape((size / 3).dp)),
        colorFilter = ColorFilter.colorMatrix(remember { ColorMatrix().apply { setToSaturation(0f) } })
    )
}

@Composable private fun AppTile(app:AppRecord) {
    Card(onClick={go(Route("apps",app.id))},shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow),modifier=Modifier.fillMaxWidth().heightIn(min=100.dp)) {
        Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            AppLogo(app)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text(app.name,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text(app.category,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            Glyph(if(app.status=="mirrored")KosherSymbols.Download else KosherSymbols.Chevron,null,20,MaterialTheme.colorScheme.primary)
        }
    }
}
@Composable private fun PhoneTile(device:Device,compact:Boolean,modifier:Modifier=Modifier) {
    Card(onClick={go(Route("phones",device.id))},shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow),modifier=modifier.fillMaxWidth()) {
        if(compact) Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(18.dp)) {
            PhoneArt(device.form,Modifier.size(56.dp,72.dp));Column(Modifier.weight(1f)){Text(device.name,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold);Text(device.brand,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};Glyph(KosherSymbols.Chevron,null,20)
        } else Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {Box(Modifier.fillMaxWidth().height(136.dp),contentAlignment=Alignment.Center){PhoneArt(device.form,Modifier.size(80.dp,132.dp))};Text(device.name,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis);Text(device.brand,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
}
@Composable private fun PhoneArt(form:String,modifier:Modifier) {
    val c=MaterialTheme.colorScheme
    Canvas(modifier.clearAndSetSemantics {}) {
        val w=size.width; val h=size.height; val body=c.onPrimaryContainer;val screen=c.primaryContainer;val detail=c.primary
        val left=w*.16f; val width=w*.68f
        drawRoundRect(body,Offset(left,h*.03f),Size(width,h*.94f),CornerRadius(w*.13f))
        val screenBottom=if(form=="touch") .84f else if(form=="qwerty") .49f else .53f
        drawRoundRect(screen,Offset(left+w*.045f,h*.13f),Size(width-w*.09f,h*(screenBottom-.13f)),CornerRadius(w*.045f))
        drawRoundRect(screen,Offset(w*.41f,h*.075f),Size(w*.18f,h*.016f),CornerRadius(3f))
        if(form=="touch") {
            repeat(6){i->drawRoundRect(detail.copy(alpha=if(i%2==0).6f else .25f),Offset(w*(.27f+(i%3)*.18f),h*(.49f+(i/3)*.12f)),Size(w*.12f,w*.12f),CornerRadius(w*.04f))}
            drawLine(screen,Offset(w*.4f,h*.915f),Offset(w*.6f,h*.915f),w*.025f)
        } else {
            if(form=="flip")drawRoundRect(detail,Offset(w*.14f,h*.54f),Size(w*.72f,h*.028f),CornerRadius(w*.014f))
            drawCircle(screen,w*.065f,Offset(w*.5f,h*.625f))
            val cols=if(form=="qwerty")5 else 3
            repeat(4){row->repeat(cols){col-> val spacing=.51f/cols;drawRoundRect(screen.copy(alpha=.84f),Offset(w*(.245f+col*spacing),h*(.7f+row*.058f)),Size(w*spacing*.73f,h*.031f),CornerRadius(w*.02f))}}
        }
    }
}

@Composable private fun AppDetail(app:AppRecord,compact:Boolean,info:()->Unit) {
    DetailLayout(compact) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(18.dp)) {AppLogo(app,64);Column(Modifier.weight(1f)){Text(app.name,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold);Text(app.category,color=MaterialTheme.colorScheme.onSurfaceVariant)};Action(KosherSymbols.Info,"Source and license",info)}
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {Meta(app.version.ifBlank{"Release"});Meta(if(app.status=="mirrored")"Hosted" else "Community");if(app.bytes>0)Meta("${kotlin.math.round(app.bytes / 100_000.0) / 10} MB")}
        if(app.downloadUrl.isNotEmpty()) Button(onClick={open(app.downloadUrl)},contentPadding=PaddingValues(horizontal=24.dp,vertical=16.dp),shape=RoundedCornerShape(20.dp)) {Glyph(KosherSymbols.Download,null);Spacer(Modifier.width(10.dp));Text("Download APK");if(app.status!="mirrored"){Spacer(Modifier.width(10.dp));Glyph(KosherSymbols.External,null,16)}}
        if(app.status!="mirrored") Text("Hosted by ${if(app.id.startsWith("cfop-"))"CFOP" else "the original project"}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(app.description,style=MaterialTheme.typography.bodyLarge)
        if(app.sourceArchive.isNotEmpty()) FilledTonalButton(onClick={open(app.sourceArchive)}) {Glyph(KosherSymbols.Code,null);Spacer(Modifier.width(8.dp));Text("Source archive")}
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
        Text("Check device compatibility and permissions before installing.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun PhoneDetail(device:Device,section:String,compact:Boolean) {
    DetailLayout(compact) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(24.dp)) {PhoneArt(device.form,Modifier.size(68.dp,110.dp));Column(Modifier.weight(1f)){Text(device.brand,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.labelLarge);Text(device.name,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold);Text(if(section!="phones")title(section) else device.form.replaceFirstChar{it.uppercase()},color=MaterialTheme.colorScheme.onSurfaceVariant)}}
        if(section=="phones")Text(device.description,style=MaterialTheme.typography.bodyLarge)
        val resources=device.resources.withIndex().filter{section=="phones"||resourceSection(it.value)?.key==section}
        resources.forEach{(index,r)->
            Card(onClick={go(Route(section,device.id,index))},shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow),modifier=Modifier.fillMaxWidth()) {Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {Glyph(symbol(resourceSection(r)?.key ?: "guides"),null);Column(Modifier.weight(1f)){Text(r.title,fontWeight=FontWeight.Medium);Text(if(r.mirrorUrl.isNotEmpty())"Hosted" else title(resourceSection(r)?.key ?: "guides"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};Glyph(KosherSymbols.Chevron,null,20)}}
        }
        if(resources.isEmpty())Text("No files yet.",color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(section=="roms")Text("Match the exact model. Back up before flashing.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun ResourceDetail(device:Device,index:Int,compact:Boolean) {
    val r=device.resources.getOrNull(index)
    if(r==null){EmptyState(KosherSymbols.Search,"Not found");return}
    val section=resourceSection(r)?.key ?: "guides"
    DetailLayout(compact) {
        Glyph(symbol(section),null,44,MaterialTheme.colorScheme.primary)
        Text(r.title,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
        Text(device.name,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Meta(title(section));Meta(if(r.mirrorUrl.isNotEmpty())"Hosted" else "Source reference")}
        Button(onClick={open(r.mirrorUrl.ifEmpty{r.url})},shape=RoundedCornerShape(20.dp),contentPadding=PaddingValues(20.dp,16.dp)) {Glyph(if(section=="guides")KosherSymbols.Document else KosherSymbols.Download,null);Spacer(Modifier.width(10.dp));Text(if(section=="guides")"Read guide" else "Download");if(r.mirrorUrl.isEmpty()){Spacer(Modifier.width(8.dp));Glyph(KosherSymbols.External,null,16)}}
        if(r.sha256.isNotBlank())SelectionContainer{Text("SHA-256\n${r.sha256}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        Text("Source: ${r.source}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(section=="roms")Text("Exact hardware revision required. Back up before flashing.",style=MaterialTheme.typography.bodyMedium)
    }
}
@Composable private fun DetailLayout(compact:Boolean,body:@Composable ColumnScope.()->Unit) {
    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.TopCenter){Column(Modifier.widthIn(max=800.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=if(compact)22.dp else 36.dp,vertical=if(compact)12.dp else 26.dp),verticalArrangement=Arrangement.spacedBy(24.dp),content=body)}
}
@Composable private fun Meta(text:String){Surface(shape=RoundedCornerShape(10.dp),color=MaterialTheme.colorScheme.surfaceContainerHigh){Text(text,Modifier.padding(horizontal=10.dp,vertical=6.dp),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
@Composable private fun EmptyState(icon:SymbolPath,label:String,action:@Composable ()->Unit={}) {Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){Glyph(icon,null,36,MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.height(16.dp));Text(label,style=MaterialTheme.typography.titleMedium);Spacer(Modifier.height(12.dp));action()}}
