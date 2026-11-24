package com.finnishkids.verbisland

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class VerbDataTest {

    @Test
    fun counts() {
        assertEquals(65, VerbRepository.all.size)
        assertEquals(65, VerbRepository.all.map { it.inf }.toSet().size)
        assertEquals(listOf(17, 14, 20, 14), (2..5).map { VerbRepository.stage(it).size })
        assertEquals(3, VerbRepository.all.count { it.extra })
    }

    @Test
    fun tablesFollowTheRules() {
        for (v in VerbRepository.all) {
            val f = v.forms
            val n = v.inf
            assertEquals(n, 6, f.size)
            assertTrue(n, f[0].endsWith("n"))
            assertEquals(n, v.negStem + "t", f[1])
            assertEquals(n, v.negStem + "mme", f[3])
            assertEquals(n, v.negStem + "tte", f[4])
            // vowel harmony: a/o/u in the word -> -vat, otherwise -vät
            val back = n.any { it in "aou" }
            assertEquals(n, if (back) "vat" else "vät", f[5].takeLast(3))
            // Hän = strong stem with its last vowel lengthened, or the stem itself when it already ends long
            if (n != "olla") {
                val s = v.strongStem
                val end = s.takeLast(2)
                val diphthongs = "ai ei oi ui yi äi öi au eu iu ou ey äy öy iy ie uo yö".split(" ")
                val longEnd = (end.length == 2 && end[0] == end[1] && end[0] in "aeiouyäö") || end in diphthongs
                val expected = if (longEnd) s else s + s.last()
                assertEquals(n, expected, f[2])
            }
            v.kpt?.let { k ->
                assertEquals(n, k.from.substringBefore('['), k.to.substringBefore('['))
                if (k.reverse) assertEquals(n, k.plain(k.to), v.strongStem)
                else {
                    assertEquals(n, k.plain(k.from), v.strongStem)
                    assertEquals(n, k.plain(k.to), v.weakStem)
                }
            }
        }
    }

    @Test
    fun roundsAreWellFormed() {
        val rnd = Random(7)
        for (stage in 1..5) repeat(400) {
            val qs = QuestionMaker.round(stage, { 1.0 }, rnd)
            assertEquals(5, qs.size)
            assertEquals(5, qs.map { it.verb.inf + it.person }.toSet().size)
            for (q in qs) {
                val want = if (q.kind == QKind.ENDING) 6 else 4
                assertEquals(q.toString(), want, q.choices.size)
                assertEquals(q.toString(), want, q.choices.map { it.label }.toSet().size)
                assertEquals(q.toString(), 1, q.choices.count { it.correct })
                for (c in q.choices.filter { !it.correct }) assertTrue(Hints.wrong(q, c).isNotBlank())
            }
        }
    }

    /** Writes every question, trap and hint to build/hints.txt so a person can read them through. */
    @Test
    fun dumpHints() {
        val out = StringBuilder()
        for (v in VerbRepository.all) {
            out.append("\n== ${v.inf} (${v.cn}) stage ${v.stage} ${v.group}\n")
            out.append("   词干：${Hints.stemLine(v)}\n")
            v.note?.let { out.append("   提醒：$it\n") }
            for (p in Person.entries) {
                val q = QuestionMaker.formQ(v, p, Random(1))
                out.append("  ${p.fi} ${v.form(p)} | ${v.cnSentence(p)} | ${v.negPhrase(p)} | ${v.cnSentence(p, true)}\n")
                out.append("     ✓ ${Hints.right(q)}\n")
                for (c in q.choices.filter { !it.correct }) out.append("     ✗ ${c.label}: ${Hints.wrong(q, c)}\n")
                if (v.stage == 5) {
                    val nq = QuestionMaker.negQ(v, p, Random(1))
                    for (c in nq.choices.filter { !it.correct }) out.append("     ✗neg ${c.label}: ${Hints.wrong(nq, c)}\n")
                }
            }
            val w = QuestionMaker.whoQ(v, Person.HAN, Random(1))
            out.append("     who: ${Hints.right(w)}\n")
        }
        for (p in Person.entries) {
            val q = QuestionMaker.endingQ(p, Random(1))
            out.append("ending ${p.fi}: ${Hints.right(q)} / ${Hints.wrong(q, q.choices.first { !it.correct })}\n")
        }
        File("build").mkdirs()
        File("build/hints.txt").writeText(out.toString())
    }
}
