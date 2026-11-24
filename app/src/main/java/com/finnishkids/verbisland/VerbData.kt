package com.finnishkids.verbisland

/*
 * 芬兰语动词闯关岛 —— 全部词库（65 个动词）
 *
 * 每个动词一行： v(关卡, 原形, 中文, "Minä Sinä Hän Me Te He 六个变位", 动词类型, ...)
 * tools/make_voice.py 会读取这个文件，为每个原形、每个“人称 + 变位”和“人称 + 否定”生成内置芬兰语录音，
 * 所以改了词库以后要重新运行它（见 README）。
 */

/** 六个人称。颜色：代词卡片和它的词尾卡片用同一种柔和颜色（Minä 与 -n 是蓝色，Me 与 -mme 是暖绿色……） */
enum class Person(
    val fi: String,
    val cn: String,
    val neg: String,          // 否定词 en / et / ei / emme / ette / eivät
    val strong: Boolean,      // KPT：Hän 和 He 是“大力士”，词干保持原样
    val color: Long,          // 主色（文字、边框）
    val soft: Long,           // 柔和底色（卡片）
) {
    MINA("Minä", "我", "en", false, 0xFF3F86D9, 0xFFDCEBFF),
    SINA("Sinä", "你", "et", false, 0xFF8A63D2, 0xFFEDE4FC),
    HAN("Hän", "他/她", "ei", true, 0xFFE8871E, 0xFFFFEBD3),
    ME("Me", "我们", "emme", false, 0xFF3FA34D, 0xFFDDF3DC),
    TE("Te", "你们", "ette", false, 0xFFD6558F, 0xFFFCE2EE),
    HE("He", "他们", "eivät", true, 0xFF17998C, 0xFFD5F1EE);

    /** 词尾说明（中文） */
    val endingTip: String
        get() = when (this) {
            MINA -> "-n"
            SINA -> "-t"
            HAN -> "拉长最后一个元音"
            ME -> "-mme"
            TE -> "-tte"
            HE -> "-vat / -vät"
        }
}

/** 动词类型（芬兰语课本里的 verbityyppi） */
enum class VerbGroup(val title: String) {
    T1("第一类：-a / -ä 结尾"),
    T2("第二类：-da / -dä 结尾"),
    T3("第三类：-la / -na / -ra / -sta 结尾"),
    T4("第四类：-ata / -ota / -ätä 结尾"),
    T5("第五类：-ita 结尾"),
    SPECIAL("特殊动词，要单独记"),
}

/**
 * KPT 辅音强弱变化。用方括号标出会变的那几个字母，例如 from = "kirjoi[tt]a"，to = "kirjoi[t]a"。
 * 普通变软（reverse = false）：from 是强词干（Hän / He 用），to 是软词干（Minä / Sinä / Me / Te 用）。
 * 反向变强（reverse = true）：from 是原形里的软辅音，to 是现在时 6 个人称都用的强词干（tykätä → tykkää-）。
 */
data class Kpt(val from: String, val to: String, val rule: String, val reverse: Boolean = false) {
    val prefix: String get() = from.substringBefore('[')
    val fromSeg: String get() = from.substringAfter('[').substringBefore(']')
    val toSeg: String get() = to.substringAfter('[').substringBefore(']')
    val strongSeg: String get() = if (reverse) toSeg else fromSeg
    val weakSeg: String get() = if (reverse) fromSeg else toSeg
    fun plain(markup: String) = markup.replace("[", "").replace("]", "")
}

data class Verb(
    val stage: Int,
    val inf: String,
    val cn: String,
    val forms: List<String>,      // [Minä, Sinä, Hän, Me, Te, He]
    val group: VerbGroup,
    val kpt: Kpt? = null,
    val note: String? = null,      // 特别提醒（中文）
    val say: String? = null,       // 拼中文句子时用的短释义（默认取“/”前面的部分）
    val extra: Boolean = false,    // 为凑满 65 词补充的词
) {
    fun form(p: Person) = forms[p.ordinal]

    /** 否定句用的“光杆词干” = Minä 的形式去掉 -n（haluan → halua） */
    val negStem: String get() = forms[0].dropLast(1)

    /** Hän / He 用的词干 = He 的形式去掉 -vat / -vät */
    val strongStem: String get() = forms[5].dropLast(3)

    /** Minä / Sinä / Me / Te 用的词干 */
    val weakStem: String get() = negStem

    fun stem(p: Person) = if (p.strong) strongStem else weakStem

    val back: Boolean get() = forms[5].endsWith("vat")   // 元音和谐：a/o/u 用 -vat，否则 -vät

    val cnShort: String get() = say ?: cn.substringBefore('/').substringBefore('（').substringBefore('(')

    fun cnSentence(p: Person, negative: Boolean = false) =
        p.cn + (if (negative) "不" else "") + cnShort

    fun phrase(p: Person) = "${p.fi} ${form(p)}"
    fun negPhrase(p: Person) = "${p.fi} ${p.neg} $negStem"
}

private fun v(
    stage: Int, inf: String, cn: String, forms: String, group: VerbGroup,
    kpt: Kpt? = null, note: String? = null, say: String? = null, extra: Boolean = false,
) = Verb(stage, inf, cn, forms.trim().split(Regex("\\s+")), group, kpt, note, say, extra)

private val G1 = VerbGroup.T1
private val G2 = VerbGroup.T2
private val G3 = VerbGroup.T3
private val G4 = VerbGroup.T4
private val G5 = VerbGroup.T5
private val GS = VerbGroup.SPECIAL

object VerbRepository {

    val all: List<Verb> = listOf(
        // ───────── 第 2 关：规则动词大挑战（17 词；puhua 同时是第 1 关的固定词干） ─────────
        v(2, "puhua", "说话", "puhun puhut puhuu puhumme puhutte puhuvat", G1),
        v(2, "asua", "居住", "asun asut asuu asumme asutte asuvat", G1, say = "住在这里"),
        v(2, "istua", "坐", "istun istut istuu istumme istutte istuvat", G1, say = "坐着"),
        v(2, "kysyä", "问", "kysyn kysyt kysyy kysymme kysytte kysyvät", G1),
        v(2, "sanoa", "说", "sanon sanot sanoo sanomme sanotte sanovat", G1),
        v(2, "katsoa", "看", "katson katsot katsoo katsomme katsotte katsovat", G1),
        v(2, "ostaa", "买", "ostan ostat ostaa ostamme ostatte ostavat", G1, say = "买东西"),
        v(2, "maksaa", "付钱/花费", "maksan maksat maksaa maksamme maksatte maksavat", G1),
        v(2, "etsiä", "寻找", "etsin etsit etsii etsimme etsitte etsivät", G1),
        v(2, "muistaa", "记得", "muistan muistat muistaa muistamme muistatte muistavat", G1),
        v(2, "unohtaa", "忘记", "unohdan unohdat unohtaa unohdamme unohdatte unohtavat", G1,
            kpt = Kpt("unoh[t]a", "unoh[d]a", "ht → hd"),
            note = "注意：h 后面的 t 会变软成 d —— Minä unohdan，但 Hän unohtaa。"),
        v(2, "nauraa", "笑", "nauran naurat nauraa nauramme nauratte nauravat", G1),
        v(2, "itkeä", "哭", "itken itket itkee itkemme itkette itkevät", G1),
        v(2, "seisoa", "站立", "seison seisot seisoo seisomme seisotte seisovat", G1, say = "站着"),
        v(2, "laulaa", "唱歌", "laulan laulat laulaa laulamme laulatte laulavat", G1),
        v(2, "tanssia", "跳舞", "tanssin tanssit tanssii tanssimme tanssitte tanssivat", G1),
        v(2, "uida", "游泳", "uin uit ui uimme uitte uivat", G2,
            note = "uida 去掉 -da 就是 ui-；Hän ui 不用再拉长（ui 已经是双元音）。"),

        // ───────── 第 3 关：KPT 辅音“变软”魔法（14 词） ─────────
        v(3, "lukea", "读", "luen luet lukee luemme luette lukevat", G1,
            kpt = Kpt("lu[k]e", "lu[]e", "k 消失")),
        v(3, "kirjoittaa", "写", "kirjoitan kirjoitat kirjoittaa kirjoitamme kirjoitatte kirjoittavat", G1,
            kpt = Kpt("kirjoi[tt]a", "kirjoi[t]a", "tt → t")),
        v(3, "nukkua", "睡觉", "nukun nukut nukkuu nukumme nukutte nukkuvat", G1,
            kpt = Kpt("nu[kk]u", "nu[k]u", "kk → k")),
        v(3, "ottaa", "拿/取", "otan otat ottaa otamme otatte ottavat", G1,
            kpt = Kpt("o[tt]a", "o[t]a", "tt → t")),
        v(3, "oppia", "学习", "opin opit oppii opimme opitte oppivat", G1,
            kpt = Kpt("o[pp]i", "o[p]i", "pp → p")),
        v(3, "auttaa", "帮助", "autan autat auttaa autamme autatte auttavat", G1,
            kpt = Kpt("au[tt]a", "au[t]a", "tt → t"), say = "帮忙"),
        v(3, "soittaa", "打电话/弹奏", "soitan soitat soittaa soitamme soitatte soittavat", G1,
            kpt = Kpt("soi[tt]a", "soi[t]a", "tt → t")),
        v(3, "odottaa", "等待", "odotan odotat odottaa odotamme odotatte odottavat", G1,
            kpt = Kpt("odo[tt]a", "odo[t]a", "tt → t")),
        v(3, "leikkiä", "玩耍", "leikin leikit leikkii leikimme leikitte leikkivät", G1,
            kpt = Kpt("lei[kk]i", "lei[k]i", "kk → k")),
        v(3, "tietää", "知道", "tiedän tiedät tietää tiedämme tiedätte tietävät", G1,
            kpt = Kpt("tie[t]ä", "tie[d]ä", "t → d")),
        v(3, "löytää", "找到", "löydän löydät löytää löydämme löydätte löytävät", G1,
            kpt = Kpt("löy[t]ä", "löy[d]ä", "t → d")),
        v(3, "ymmärtää", "理解/听懂", "ymmärrän ymmärrät ymmärtää ymmärrämme ymmärrätte ymmärtävät", G1,
            kpt = Kpt("ymmä[rt]ä", "ymmä[rr]ä", "rt → rr"), say = "听懂"),
        v(3, "piirtää", "画画", "piirrän piirrät piirtää piirrämme piirrätte piirtävät", G1,
            kpt = Kpt("pii[rt]ä", "pii[rr]ä", "rt → rr")),
        v(3, "tykätä", "喜欢", "tykkään tykkäät tykkää tykkäämme tykkäätte tykkäävät", G4,
            kpt = Kpt("ty[k]ä", "ty[kk]ää", "k → kk", reverse = true),
            note = "tykätä 是例外：它反过来变强，6 个人称都用 tykkää-。"),

        // ───────── 第 4 关：吃喝与校园日常高频词（20 词） ─────────
        // 吃喝动作（-da / -dä 类 + tehdä、nähdä）
        v(4, "syödä", "吃", "syön syöt syö syömme syötte syövät", G2, say = "吃饭"),
        v(4, "juoda", "喝", "juon juot juo juomme juotte juovat", G2, say = "喝水"),
        v(4, "saada", "得到/可以", "saan saat saa saamme saatte saavat", G2),
        v(4, "voida", "能够/感觉", "voin voit voi voimme voitte voivat", G2, say = "能"),
        v(4, "tuoda", "带来", "tuon tuot tuo tuomme tuotte tuovat", G2),
        v(4, "viedä", "带走/送去", "vien viet vie viemme viette vievät", G2),
        v(4, "käydä", "去并回来/拜访", "käyn käyt käy käymme käytte käyvät", G2, say = "去一趟"),
        v(4, "tehdä", "做", "teen teet tekee teemme teette tekevät", GS,
            note = "tehdä 很特别：Minä teen，但 Hän tekee、He tekevät（hd → k）。", say = "做事"),
        v(4, "nähdä", "看见", "näen näet näkee näemme näette näkevät", GS,
            note = "nähdä 很特别：Minä näen，但 Hän näkee、He näkevät（hd → k）。"),
        // 校园与运动（-la / -na / -ra / -sta 类）
        v(4, "tulla", "来", "tulen tulet tulee tulemme tulette tulevat", G3),
        v(4, "mennä", "去", "menen menet menee menemme menette menevät", G3),
        v(4, "olla", "是/在", "olen olet on olemme olette ovat", GS,
            note = "olla 是最常用的动词！要背熟：olen, olet, on, olemme, olette, ovat —— Hän on，He ovat。"),
        v(4, "kävellä", "走路/散步", "kävelen kävelet kävelee kävelemme kävelette kävelevät", G3),
        v(4, "juosta", "跑步", "juoksen juokset juoksee juoksemme juoksette juoksevat", G3,
            note = "juosta 很特别：去掉 -ta 加 e，还要在 s 前面加一个 k → juokse-。"),
        v(4, "pestä", "洗", "pesen peset pesee pesemme pesette pesevät", G3, say = "洗东西"),
        v(4, "opiskella", "学习功课", "opiskelen opiskelet opiskelee opiskelemme opiskelette opiskelevat", G3),
        v(4, "kuunnella", "听", "kuuntelen kuuntelet kuuntelee kuuntelemme kuuntelette kuuntelevat", G3,
            kpt = Kpt("kuu[nn]el", "kuu[nt]ele", "nn → nt", reverse = true),
            note = "kuunnella 反向变强：nn → nt，6 个人称都用 kuuntele-。"),
        v(4, "hymyillä", "微笑", "hymyilen hymyilet hymyilee hymyilemme hymyilette hymyilevät", G3),
        v(4, "urheilla", "做运动", "urheilen urheilet urheilee urheilemme urheilette urheilevat", G3),
        v(4, "riidellä", "吵架", "riitelen riitelet riitelee riitelemme riitelette riitelevät", G3,
            kpt = Kpt("rii[d]el", "rii[t]ele", "d → t", reverse = true),
            note = "riidellä 反向变强：d → t，6 个人称都用 riitele-。"),

        // ───────── 第 5 关：状态、需求与否定句实战（11 词 + 3 个补充词） ─────────
        v(5, "haluta", "想要", "haluan haluat haluaa haluamme haluatte haluavat", G4),
        v(5, "osata", "会（某种技能）", "osaan osaat osaa osaamme osaatte osaavat", G4, say = "会"),
        v(5, "pelata", "玩（球类/游戏）", "pelaan pelaat pelaa pelaamme pelaatte pelaavat", G4, say = "玩游戏"),
        v(5, "herätä", "醒来", "herään heräät herää heräämme heräätte heräävät", G4),
        v(5, "siivota", "打扫", "siivoan siivoat siivoaa siivoamme siivoatte siivoavat", G4),
        v(5, "tavata", "见面", "tapaan tapaat tapaa tapaamme tapaatte tapaavat", G4,
            kpt = Kpt("ta[v]a", "ta[p]aa", "v → p", reverse = true),
            note = "tavata 反向变强：v → p，6 个人称都用 tapaa-。", say = "见朋友"),
        v(5, "hypätä", "跳", "hyppään hyppäät hyppää hyppäämme hyppäätte hyppäävät", G4,
            kpt = Kpt("hy[p]ä", "hy[pp]ää", "p → pp", reverse = true),
            note = "hypätä 反向变强：p → pp，6 个人称都用 hyppää-。"),
        v(5, "vastata", "回答", "vastaan vastaat vastaa vastaamme vastaatte vastaavat", G4),
        v(5, "avata", "打开", "avaan avaat avaa avaamme avaatte avaavat", G4, say = "打开门"),
        v(5, "tarvita", "需要", "tarvitsen tarvitset tarvitsee tarvitsemme tarvitsette tarvitsevat", G5),
        v(5, "valita", "选择", "valitsen valitset valitsee valitsemme valitsette valitsevat", G5),
        // 补充词：用户清单列了 62 个，补 3 个 4 年级常用的 -ätä / -ota / -itä 动词凑满 65 个
        v(5, "kerätä", "收集", "kerään keräät kerää keräämme keräätte keräävät", G4, extra = true),
        v(5, "pudota", "掉下来", "putoan putoat putoaa putoamme putoatte putoavat", G4,
            kpt = Kpt("pu[d]o", "pu[t]oa", "d → t", reverse = true),
            note = "pudota 反向变强：d → t，6 个人称都用 putoa-。", say = "掉下来", extra = true),
        v(5, "häiritä", "打扰", "häiritsen häiritset häiritsee häiritsemme häiritsette häiritsevät", G5,
            say = "打扰别人", extra = true),
    )

    val puhua: Verb get() = all.first { it.inf == "puhua" }

    fun stage(n: Int): List<Verb> = if (n == 1) listOf(puhua) else all.filter { it.stage == n }

    fun byInf(inf: String): Verb? = all.firstOrNull { it.inf == inf }
}

/** 其它会朗读的芬兰语短句（也会生成内置录音） */
object Phrases {
    const val WELCOME = "Hei! Tervetuloa verbisaarelle!"
    const val GREAT = "Hienoa!"
    const val SUPER = "Mahtavaa! Kolme tähteä!"
    const val GOOD_WORK = "Hyvää työtä!"
    val all = listOf(WELCOME, GREAT, SUPER, GOOD_WORK)
}

/** 关卡信息 */
data class StageInfo(
    val n: Int,
    val title: String,
    val subtitle: String,
    val tip: String,      // 闯关时顶部置顶的小提示
)

object Stages {
    const val GOLDEN_RULE =
        "黄金口诀：只有 Hän (他) 和 He (他们) 很强壮，词干保持原样！其他四个人称词干都要变软（tykätä 除外）。"
    const val NEG_RULE =
        "否定词 (en/et/ei) 已经抢走了词尾，后面的动词要变成光杆词干哦！（例如：Minä en puhu）"

    val list = listOf(
        StageInfo(1, "词尾配对小达人", "只练 6 个人称词尾，词干不变",
            "小窍门：颜色一样的是好朋友！Minä 配 -n（蓝色），Me 配 -mme（绿色）……"),
        StageInfo(2, "规则动词大挑战", "17 个词干不变的常用动词",
            "去掉最后的 a / ä 就是词干，再加人称词尾。Hän 要把最后一个元音拉长！"),
        StageInfo(3, "KPT 辅音“变软”魔法", "14 个四年级核心难点动词", GOLDEN_RULE),
        StageInfo(4, "吃喝与校园日常", "20 个 -da/-dä 与 -la/-na/-ra 动词",
            "-da / -dä：去掉 -da 直接加词尾。-la / -na / -ra / -sta：去掉最后两个字母，加 e 再加词尾。"),
        StageInfo(5, "状态、需求与否定句", "14 个 -ata / -ita 动词 + 否定句",
            "-ata / -ota / -ätä：去掉 -ta 加 a。-ita：去掉 -ta 加 tse。"),
    )

    fun info(n: Int) = list[n - 1]
}
