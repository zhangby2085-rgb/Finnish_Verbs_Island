package com.finnishkids.verbisland

import kotlin.random.Random

/** 题型 */
enum class QKind(val label: String) {
    ENDING("选出正确的词尾"),
    FORM("选出正确的变位"),
    WHO("谁在做这件事？"),
    NEG("变成否定句"),
}

/** 选错时属于哪一类错误（决定给哪句中文解析） */
enum class Mistake { NONE, OTHER_PERSON, WRONG_GRADE, WRONG_STEM, INFINITIVE, KEEP_ENDING, WRONG_NEG }

data class Choice(
    val label: String,
    val correct: Boolean,
    val mistake: Mistake = Mistake.NONE,
    val person: Person? = null,
)

data class Question(
    val kind: QKind,
    val verb: Verb,
    val person: Person,
    val choices: List<Choice>,
) {
    val answer: String get() = choices.first { it.correct }.label

    /** 答对后显示并朗读的完整芬兰语 */
    val solved: String get() = if (kind == QKind.NEG) verb.negPhrase(person) else verb.phrase(person)

    /** 对应的中文 */
    val cnSolved: String get() = verb.cnSentence(person, kind == QKind.NEG)
}

fun ending(p: Person, back: Boolean): String = when (p) {
    Person.MINA -> "-n"
    Person.SINA -> "-t"
    Person.HAN -> "拉长元音"
    Person.ME -> "-mme"
    Person.TE -> "-tte"
    Person.HE -> if (back) "-vat" else "-vät"
}

private fun bareEnding(p: Person, back: Boolean) = ending(p, back).removePrefix("-")

/** 第 1 关的词尾卡：puhu + ? */
fun stageOneEnding(p: Person): String = "-" + VerbRepository.puhua.form(p).removePrefix("puhu")

object QuestionMaker {

    /** 每轮 5 题。weight 越大的动词越容易被抽到（没见过的、最近答错的） */
    fun round(stage: Int, weight: (Verb) -> Double, rnd: Random = Random.Default): List<Question> {
        val persons = Person.entries.shuffled(rnd).take(5)   // 一轮里 5 个不同的人称
        if (stage == 1) return persons.map { endingQ(it, rnd) }

        val verbs = pickWeighted(VerbRepository.stage(stage), 5, weight, rnd)
        val kinds: List<QKind> = when (stage) {
            2, 4 -> MutableList(5) { QKind.FORM }.also { it[1 + rnd.nextInt(4)] = QKind.WHO }
            5 -> listOf(QKind.FORM) + listOf(QKind.FORM, QKind.NEG, QKind.NEG, QKind.NEG).shuffled(rnd)
            else -> List(5) { QKind.FORM }
        }
        return verbs.indices.map { i ->
            val v = verbs[i]
            val p = persons[i]
            when (kinds[i]) {
                QKind.WHO -> whoQ(v, p, rnd)
                QKind.NEG -> negQ(v, p, rnd)
                else -> formQ(v, p, rnd)
            }
        }
    }

    fun pickWeighted(pool: List<Verb>, n: Int, weight: (Verb) -> Double, rnd: Random): List<Verb> {
        val left = pool.toMutableList()
        val out = mutableListOf<Verb>()
        while (out.size < n && left.isNotEmpty()) {
            val total = left.sumOf { weight(it).coerceAtLeast(0.01) }
            var r = rnd.nextDouble() * total
            var pick = left.last()
            for (v in left) {
                r -= weight(v).coerceAtLeast(0.01)
                if (r <= 0) { pick = v; break }
            }
            out += pick
            left -= pick
        }
        return out
    }

    fun endingQ(p: Person, rnd: Random): Question {
        val v = VerbRepository.puhua
        val choices = Person.entries.map { o ->
            Choice(stageOneEnding(o), o == p, if (o == p) Mistake.NONE else Mistake.OTHER_PERSON, o)
        }.shuffled(rnd)
        return Question(QKind.ENDING, v, p, choices)
    }

    fun formQ(v: Verb, p: Person, rnd: Random): Question {
        val out = mutableListOf(Choice(v.form(p), true, person = p))
        fun add(label: String?, m: Mistake, o: Person? = null) {
            if (label != null && out.size < 4 && out.none { it.label == label }) out += Choice(label, false, m, o)
        }
        val grade = wrongGrade(v, p)
        if (grade != null) add(grade, Mistake.WRONG_GRADE)
        else {
            val stem = stemTrap(v, p)
            if (stem != null && v.forms.none { it == stem }) add(stem, Mistake.WRONG_STEM)
            else add(v.inf, Mistake.INFINITIVE)
        }
        for (o in Person.entries.filter { it != p }.shuffled(rnd)) add(v.form(o), Mistake.OTHER_PERSON, o)
        return Question(QKind.FORM, v, p, out.shuffled(rnd))
    }

    fun whoQ(v: Verb, p: Person, rnd: Random): Question {
        val others = Person.entries.filter { it != p }.shuffled(rnd).take(3)
        val choices = (others.map { Choice(it.fi, false, Mistake.OTHER_PERSON, it) } + Choice(p.fi, true, person = p))
            .sortedBy { it.person!!.ordinal }   // 人称按 Minä → He 的顺序排，方便孩子找
        return Question(QKind.WHO, v, p, choices)
    }

    fun negQ(v: Verb, p: Person, rnd: Random): Question {
        val right = "${p.neg} ${v.negStem}"
        val out = mutableListOf(Choice(right, true, person = p))
        fun add(label: String, m: Mistake) {
            if (out.size < 4 && out.none { it.label == label }) out += Choice(label, false, m)
        }
        add("${p.neg} ${v.form(p)}", Mistake.KEEP_ENDING)                    // en haluan：词尾没被抢走
        add("${if (p == Person.HAN) "en" else "ei"} ${v.negStem}", Mistake.WRONG_NEG)  // 否定词用错
        add("${p.neg} ${v.inf}", Mistake.INFINITIVE)                          // en haluta
        for (o in Person.entries.filter { it != p }.shuffled(rnd)) add("${o.neg} ${v.negStem}", Mistake.WRONG_NEG)
        return Question(QKind.NEG, v, p, out.shuffled(rnd))
    }

    /** KPT 错误：该变软的没变软（kirjoittan），或者该保持强壮的变软了（kirjoitaa）；反向动词忘了变强（tavaan） */
    fun wrongGrade(v: Verb, p: Person): String? {
        val k = v.kpt ?: return null
        val useStrong = k.reverse || p.strong
        val seg = if (useStrong) k.strongSeg else k.weakSeg
        val other = if (useStrong) k.weakSeg else k.strongSeg
        val form = v.form(p)
        if (!form.startsWith(k.prefix + seg)) return null
        return k.prefix + other + form.substring(k.prefix.length + seg.length)
    }

    /** 词干做错的典型样子（syödän、tullen、halun、tarvin、Hän olee……），没有合适的就返回 null */
    fun stemTrap(v: Verb, p: Person): String? {
        val back = v.back
        fun withEnding(stem: String) =
            if (p == Person.HAN) stem + stem.last() else stem + bareEnding(p, back)
        return when (v.group) {
            VerbGroup.T1 -> null
            VerbGroup.T2 -> if (p == Person.HAN) {
                val s = v.negStem
                if (s.length >= 2 && s[s.length - 1] == s[s.length - 2]) null else s + s.last()   // syöö
            } else v.inf + bareEnding(p, back)                                                    // syödän
            VerbGroup.T3 -> when {
                v.kpt != null -> null
                v.inf == "juosta" -> withEnding("juose")                                          // juosen
                else -> withEnding(v.inf.dropLast(1) + "e")                                        // tullen
            }
            VerbGroup.T4 -> if (v.kpt != null) null else withEnding(v.inf.dropLast(2))             // halun
            VerbGroup.T5 -> withEnding(v.inf.dropLast(2))                                          // tarvin
            VerbGroup.SPECIAL -> when (v.inf) {
                "olla" -> when (p) { Person.HAN -> "olee"; Person.HE -> "olevat"; else -> null }
                "tehdä", "nähdä" ->
                    if (p.strong) v.weakStem + (if (p == Person.HE) "vät" else "")             // tee / teevät
                    else v.strongStem + bareEnding(p, back)                                     // teken
                else -> null
            }
        }
    }
}

/** 中文讲解：每次选错都给 1 句针对这个词、这个人称的原理解析 */
object Hints {

    /** 词干是怎么来的 */
    fun stemLine(v: Verb): String {
        val k = v.kpt
        val tail2 = v.inf.takeLast(2)
        return when {
            v.inf == "olla" -> "olla 不规则：olen, olet, on, olemme, olette, ovat"
            v.inf == "juosta" -> "juosta 去掉 -ta 加 e，s 前面再加 k → juokse-"
            v.group == VerbGroup.SPECIAL -> "${v.inf}：Minä/Sinä/Me/Te 用 ${v.weakStem}-，Hän/He 用 ${v.strongStem}-"
            k != null && k.reverse -> "${v.inf} 反向变强（${k.rule}）：6 个人称都用 ${v.strongStem}-"
            k != null -> "${v.inf}：Hän/He 用强词干 ${v.strongStem}-，其他人称用软词干 ${v.weakStem}-（${k.rule}）"
            v.group == VerbGroup.T1 -> "${v.inf} 去掉最后的 ${v.inf.last()} → ${v.weakStem}-"
            v.group == VerbGroup.T2 -> "${v.inf} 去掉 -$tail2 → ${v.weakStem}-"
            v.group == VerbGroup.T3 -> "${v.inf} 去掉 -$tail2，加 e → ${v.weakStem}-"
            v.group == VerbGroup.T4 -> "${v.inf} 去掉 -$tail2，加 ${v.weakStem.last()} → ${v.weakStem}-"
            else -> "${v.inf} 去掉 -$tail2，加 tse → ${v.weakStem}-"
        }
    }

    /** “怎么变出这个人称的形式”，一句话 */
    fun build(v: Verb, p: Person): String {
        val ans = v.form(p)
        if (v.inf == "olla") {
            return when (p) {
                Person.HAN -> "olla 很特别：Hän on（不是 olee）！"
                Person.HE -> "olla 很特别：He ovat（不是 olevat）！"
                else -> "olla 的词干是 ole-，${p.fi} 加 ${ending(p, true)} → $ans。"
            }
        }
        val k = v.kpt
        val stemPart = when {
            k != null && !k.reverse && p.strong -> "${p.fi} 是大力士💪，词干保持原样 ${v.strongStem}-"
            k != null && !k.reverse -> "${p.fi} 的词干要变软：${v.strongStem}- → ${v.weakStem}-（${k.rule}）"
            k != null -> "${v.inf} 反向变强（${k.rule}）→ ${v.strongStem}-"
            v.group == VerbGroup.SPECIAL -> "${v.inf} 的 ${p.fi} 用 ${v.stem(p)}-"
            else -> stemLine(v)
        }
        val endPart = if (p == Person.HAN) {
            if (ans == v.strongStem) "Hän 直接用 $ans（已经是长音，不用再拉长）"
            else "Hän 把最后一个元音拉长 → $ans"
        } else "${p.fi} 加 ${ending(p, v.back)} → $ans"
        return "$stemPart，$endPart。"
    }

    /** 选错时的解析 */
    fun wrong(q: Question, c: Choice): String {
        val v = q.verb
        val p = q.person
        return when (q.kind) {
            QKind.ENDING -> {
                val o = c.person!!
                val how = if (p == Person.HAN) "把最后的元音拉长：puhu → puhuu"
                else "配 ${stageOneEnding(p)}：puhu + ${stageOneEnding(p).removePrefix("-")} = ${v.form(p)}"
                "「${c.label}」是 ${o.fi}（${o.cn}）的词尾。${p.fi}（${p.cn}）要$how。"
            }
            QKind.FORM -> {
                val lead = when (c.mistake) {
                    Mistake.OTHER_PERSON -> "「${c.label}」是 ${c.person!!.fi} 的说法。"
                    Mistake.INFINITIVE -> "「${c.label}」是原形（字典里的样子），跟 ${p.fi} 在一起要变位。"
                    Mistake.WRONG_GRADE -> if (v.kpt?.reverse == true) "「${c.label}」忘了变强。"
                    else if (p.strong) "「${c.label}」变软了，可 ${p.fi} 不用变软。" else "「${c.label}」还没变软。"
                    Mistake.WRONG_STEM -> "「${c.label}」的词干不对。"
                    else -> ""
                }
                lead + build(v, p)
            }
            QKind.WHO -> {
                val o = c.person!!
                "${o.fi} 要说 ${v.form(o)}。" + whoReason(v, p)
            }
            QKind.NEG -> {
                val neg = p.neg
                when (c.mistake) {
                    Mistake.KEEP_ENDING ->
                        "否定词 $neg 已经抢走了词尾，不能再说 ${v.form(p)}，要用光杆词干：${p.fi} $neg ${v.negStem}。"
                    Mistake.INFINITIVE ->
                        "否定词后面不能用原形 ${v.inf}，要用光杆词干 ${v.negStem}（= ${v.form(Person.MINA)} 去掉 -n）。"
                    else ->
                        "${p.fi}（${p.cn}）的否定词是 $neg，不是 ${c.label.substringBefore(' ')}：${p.fi} $neg ${v.negStem}。"
                }
            }
        }
    }

    fun whoReason(v: Verb, p: Person): String {
        val f = v.form(p)
        return when {
            v.inf == "olla" && p == Person.HAN -> "on 是 olla 的 Hän（他/她）形式。"
            v.inf == "olla" && p == Person.HE -> "ovat 是 olla 的 He（他们）形式。"
            p == Person.HAN && f.length >= 2 && f[f.length - 1] == f[f.length - 2] ->
                "$f 最后是拉长的元音，这是 Hän（他/她）的样子。"
            p == Person.HAN -> "$f 没有人称词尾，这是 Hän（他/她）的样子。"
            else -> "$f 的词尾是 ${ending(p, v.back)}，属于 ${p.fi}（${p.cn}）。"
        }
    }

    /** 答对后的小讲解 */
    fun right(q: Question): String = when (q.kind) {
        QKind.ENDING -> if (q.person == Person.HAN) "Hän 把最后的元音拉长：puhu → puhuu。"
        else "puhu + ${stageOneEnding(q.person).removePrefix("-")} = ${q.verb.form(q.person)}。"
        QKind.FORM -> build(q.verb, q.person)
        QKind.WHO -> whoReason(q.verb, q.person)
        QKind.NEG -> "光杆词干 = Minä 形式去掉 -n：${q.verb.form(Person.MINA)} → ${q.verb.negStem}。"
    }
}
