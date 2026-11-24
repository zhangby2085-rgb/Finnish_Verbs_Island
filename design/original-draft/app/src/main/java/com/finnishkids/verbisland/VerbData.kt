package com.finnishkids.verbisland

enum class Pronoun(
    val fi: String,
    val cn: String,
    val index: Int,
    val isStrongStem: Boolean,
    val negWord: String
) {
    MINA("Minä", "我", 0, false, "en"),
    SINA("Sinä", "你", 1, false, "et"),
    HAN("Hän", "他/她", 2, true, "ei"),
    ME("Me", "我们", 3, false, "emme"),
    TE("Te", "你们", 4, false, "ette"),
    HE("He", "他们", 5, true, "eivät")
}

data class VerbItem(
    val infinitive: String,
    val chinese: String,
    val stage: Int, // 1..5
    val forms: List<String>, // [Minä, Sinä, Hän, Me, Te, He]
    val negativeStem: String, // 光杆词干 (用于第5关否定句)
    val kptRule: String? = null, // KPT 变化规则提示
    val isReversedKpt: Boolean = false // 如 tykätä, kuunnella 反向变强
)

object VerbRepository {
    val allVerbs: List<VerbItem> = listOf(
        // ================= 第1关 & 第2关：规则动词（17词） =================
        VerbItem("puhua", "说话", 1, listOf("puhun", "puhut", "puhuu", "puhumme", "puhutte", "puhuvat"), "puhu"),
        VerbItem("asua", "居住", 2, listOf("asun", "asut", "asuu", "asumme", "asutte", "asuvat"), "asu"),
        VerbItem("istua", "坐", 2, listOf("istun", "istut", "istuu", "istumme", "istutte", "istuvat"), "istu"),
        VerbItem("kysyä", "问", 2, listOf("kysyn", "kysyt", "kysyy", "kysymme", "kysytte", "kysyvät"), "kysy"),
        VerbItem("sanoa", "说", 2, listOf("sanon", "sanot", "sanoo", "sanomme", "sanotte", "sanovat"), "sano"),
        VerbItem("katsoa", "看", 2, listOf("katson", "katsot", "katsoo", "katsomme", "katsotte", "katsovat"), "katso"),
        VerbItem("ostaa", "买", 2, listOf("ostan", "ostat", "ostaa", "ostamme", "ostatte", "ostavat"), "osta"),
        VerbItem("maksaa", "付钱/花费", 2, listOf("maksan", "maksat", "maksaa", "maksamme", "maksatte", "maksavat"), "maksa"),
        VerbItem("etsiä", "寻找", 2, listOf("etsin", "etsit", "etsii", "etsimme", "etsitte", "etsivät"), "etsi"),
        VerbItem("muistaa", "记得", 2, listOf("muistan", "muistat", "muistaa", "muistamme", "muistatte", "muistavat"), "muista"),
        VerbItem("unohtaa", "忘记", 2, listOf("unohdan", "unohdat", "unohtaa", "unohdamme", "unohdatte", "unohtavat"), "unohda", "nt -> nd 软化"),
        VerbItem("nauraa", "笑", 2, listOf("nauran", "naurat", "nauraa", "nauramme", "nauratte", "nauravat"), "naura"),
        VerbItem("itkeä", "哭", 2, listOf("itken", "itket", "itkee", "itkemme", "itkette", "itkevät"), "itke"),
        VerbItem("seisoa", "站立", 2, listOf("seison", "seisot", "seisoo", "seisomme", "seisotte", "seisovat"), "seiso"),
        VerbItem("laulaa", "唱歌", 2, listOf("laulan", "laulat", "laulaa", "laulamme", "laulatte", "laulavat"), "laula"),
        VerbItem("tanssia", "跳舞", 2, listOf("tanssin", "tanssit", "tanssii", "tanssimme", "tanssitte", "tanssivat"), "tanssi"),
        VerbItem("uida", "游泳", 2, listOf("uin", "uit", "ui", "uimme", "uitte", "uivat"), "ui"),

        // ================= 第3关：KPT 辅音变软魔法（14词） =================
        VerbItem("lukea", "读", 3, listOf("luen", "luet", "lukee", "luemme", "luette", "lukevat"), "lue", "k 消失 (lukea -> luen)"),
        VerbItem("kirjoittaa", "写", 3, listOf("kirjoitan", "kirjoitat", "kirjoittaa", "kirjoitamme", "kirjoitatte", "kirjoittavat"), "kirjoita", "tt -> t 变软"),
        VerbItem("nukkua", "睡觉", 3, listOf("nukun", "nukut", "nukkuu", "nukumme", "nukutte", "nukkuvat"), "nuku", "kk -> k 变软"),
        VerbItem("ottaa", "拿/取", 3, listOf("otan", "otat", "ottaa", "otamme", "otatte", "ottavat"), "ota", "tt -> t 变软"),
        VerbItem("oppia", "学习", 3, listOf("opin", "opit", "oppii", "opimme", "opitte", "oppivat"), "opi", "pp -> p 变软"),
        VerbItem("auttaa", "帮助", 3, listOf("autan", "autat", "auttaa", "autamme", "autatte", "auttavat"), "auta", "tt -> t 变软"),
        VerbItem("soittaa", "打电话/弹奏", 3, listOf("soitan", "soitat", "soittaa", "soitamme", "soitatte", "soittavat"), "soita", "tt -> t 变软"),
        VerbItem("odottaa", "等待", 3, listOf("odotan", "odotat", "odottaa", "odotamme", "odotatte", "odottavat"), "odota", "tt -> t 变软"),
        VerbItem("leikkiä", "玩耍", 3, listOf("leikin", "leikit", "leikkii", "leikimme", "leikitte", "leikkivät"), "leiki", "kk -> k 变软"),
        VerbItem("tietää", "知道", 3, listOf("tiedän", "tiedät", "tietää", "tiedämme", "tiedätte", "tietävät"), "tiedä", "t -> d 变软"),
        VerbItem("löytää", "找到", 3, listOf("löydän", "löydät", "löytää", "löydämme", "löydätte", "löytävät"), "löydä", "t -> d 变软"),
        VerbItem("ymmärtää", "理解/听懂", 3, listOf("ymmärrän", "ymmärrät", "ymmärtää", "ymmärrämme", "ymmärrätte", "ymmärtävät"), "ymmärrä", "rt -> rr 变软"),
        VerbItem("piirtää", "画画", 3, listOf("piirrän", "piirrät", "piirtää", "piirrämme", "piirrätte", "piirtävät"), "piirrä", "rt -> rr 变软"),
        VerbItem("tykätä", "喜欢", 3, listOf("tykkään", "tykkäät", "tykkää", "tykkäämme", "tykkäätte", "tykkäävät"), "tykkää", "反向变强 k -> kk", isReversedKpt = true),

        // ================= 第4关：吃喝与校园日常高频词（20词） =================
        VerbItem("syödä", "吃", 4, listOf("syön", "syöt", "syö", "syömme", "syötte", "syövät"), "syö"),
        VerbItem("juoda", "喝", 4, listOf("juon", "juot", "juo", "juomme", "juotte", "juovat"), "juo"),
        VerbItem("saada", "得到/可以", 4, listOf("saan", "saat", "saa", "saamme", "saatte", "saavat"), "saa"),
        VerbItem("voida", "能够/感觉", 4, listOf("voin", "voit", "voi", "voimme", "voitte", "voivat"), "voi"),
        VerbItem("tuoda", "带来", 4, listOf("tuon", "tuot", "tuo", "tuomme", "tuotte", "tuovat"), "tuo"),
        VerbItem("viedä", "带走/送去", 4, listOf("vien", "viet", "vie", "viemme", "viette", "vievät"), "vie"),
        VerbItem("käydä", "去并回来/拜访", 4, listOf("käyn", "käyt", "käy", "käymme", "käytte", "käyvät"), "käy"),
        VerbItem("tehdä", "做", 4, listOf("teen", "teet", "tekee", "teemme", "teette", "tekevät"), "tee", "特殊变化: tehdään -> teen / tekee"),
        VerbItem("nähdä", "看见", 4, listOf("näen", "näet", "näkee", "näemme", "näette", "näkevät"), "näe", "特殊变化: nähdä -> näen / näkee"),
        VerbItem("tulla", "来", 4, listOf("tulen", "tulet", "tulee", "tulemme", "tulette", "tulevat"), "tule"),
        VerbItem("mennä", "去", 4, listOf("menen", "menet", "menee", "menemme", "menette", "menevät"), "mene"),
        VerbItem("olla", "是/在", 4, listOf("olen", "olet", "on", "olemme", "olette", "ovat"), "ole", "最高频词: Hän on / He ovat"),
        VerbItem("kävellä", "走路/散步", 4, listOf("kävelen", "kävelet", "kävelee", "kävelemme", "kävelette", "kävelevät"), "kävele"),
        VerbItem("juosta", "跑步", 4, listOf("juoksen", "juokset", "juoksee", "juoksemme", "juoksette", "juoksevat"), "juokse"),
        VerbItem("pestä", "洗", 4, listOf("pesen", "peset", "pesee", "pesemme", "pesette", "pesevät"), "pese"),
        VerbItem("opiskella", "学习功课", 4, listOf("opiskelen", "opiskelet", "opiskelee", "opiskelemme", "opiskelette", "opiskelevat"), "opiskele"),
        VerbItem("kuunnella", "听", 4, listOf("kuuntelen", "kuuntelet", "kuuntelee", "kuuntelemme", "kuuntelette", "kuuntelevat"), "kuuntele", "反向变强 nn -> nt"),
        VerbItem("hymyillä", "微笑", 4, listOf("hymyilen", "hymyilet", "hymyilee", "hymyilemme", "hymyilette", "hymyilevät"), "hymyile"),
        VerbItem("urheilla", "做运动", 4, listOf("urheilen", "urheilet", "urheilee", "urheilemme", "urheilette", "urheilevat"), "urheile"),
        VerbItem("riidellä", "吵架", 4, listOf("riitelen", "riitelet", "riitelee", "riitelemme", "riitelette", "riitelevät"), "riitele", "反向变强 d -> t"),

        // ================= 第5关：状态、需求与否定句（14词含补充） =================
        VerbItem("haluta", "想要", 5, listOf("haluan", "haluat", "haluaa", "haluamme", "haluatte", "haluavat"), "halua"),
        VerbItem("osata", "会(技能)", 5, listOf("osaan", "osaat", "osaa", "osaamme", "osaatte", "osaavat"), "osaa"),
        VerbItem("pelata", "玩(球/游戏)", 5, listOf("pelaan", "pelaat", "pelaa", "pelaamme", "pelaatte", "pelaavat"), "pelaa"),
        VerbItem("herätä", "醒来", 5, listOf("herään", "heräät", "herää", "heräämme", "heräätte", "heräävät"), "herää"),
        VerbItem("siivota", "打扫", 5, listOf("siivoan", "siivoat", "siivoaa", "siivoamme", "siivoatte", "siivoavat"), "siivoa"),
        VerbItem("tavata", "见面", 5, listOf("tapaan", "tapaat", "tapaa", "tapaamme", "tapaatte", "tapaavat"), "tapaa", "反向变强 v -> p"),
        VerbItem("hypätä", "跳", 5, listOf("hyppään", "hyppäät", "hyppää", "hyppäämme", "hyppäätte", "hyppäävät"), "hyppää", "反向变强 p -> pp"),
        VerbItem("vastata", "回答", 5, listOf("vastaan", "vastaat", "vastaa", "vastaamme", "vastaatte", "vastaavat"), "vastaa"),
        VerbItem("avata", "打开", 5, listOf("avaan", "avaat", "avaa", "avaamme", "avaatte", "avaavat"), "avaa"),
        VerbItem("tarvita", "需要", 5, listOf("tarvitsen", "tarvitset", "tarvitsee", "tarvitsemme", "tarvitsette", "tarvitsevat"), "tarvitse"),
        VerbItem("valita", "选择", 5, listOf("valitsen", "valitset", "valitsee", "valitsemme", "valitsette", "valitsevat"), "valitse")
    )
}
