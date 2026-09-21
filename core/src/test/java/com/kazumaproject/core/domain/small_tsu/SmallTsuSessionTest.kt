package com.kazumaproject.core.domain.small_tsu

import org.junit.Assert.*
import org.junit.Test

class SmallTsuSessionTest {
    private class Harness(initial: String = "") {
        val session = SmallTsuSession()
        var settings = SmallTsuSettings(enabled = true)
        var owner = Any()
        var reading = initial
        var revision = 0L
        var generation = 1L
        var eligible = true
        var context = "kana"
        var calls = 0
        fun snapshot() = if (eligible) SmallTsuSnapshot(owner, generation, revision, context, reading) else null
        fun down(time: Long, key: String = "layout:ka") = session.down(key, time, snapshot(), settings)
        fun up(text: String, time: Long, tap: Boolean = true) {
            session.text(text, tap, time, settings, ::snapshot) { replacement ->
                calls++; reading = replacement ?: (reading + text); revision++
            }
        }
        fun stroke(text: String, down: Long, up: Long = down + 10, tap: Boolean = true, key: String = "layout:ka") {
            down(down, key); up(text, up, tap)
        }
    }
    @Test fun firstTapIsSynchronousAndSecondTapReplaces() {
        val h = Harness(); h.stroke("か", 0); assertEquals("か", h.reading); assertEquals(1, h.calls)
        h.stroke("か", 110); assertEquals("っか", h.reading); assertEquals(2, h.calls)
    }
    @Test fun differentSecondOutputAndExistingReading() {
        val h = Harness("あ"); h.stroke("た", 0); h.stroke("ち", 110, tap=false); assertEquals("あっち", h.reading)
        val b = Harness("ば"); b.stroke("さ", 0); b.stroke("じ", 110, tap=false); assertEquals("ばっじ", b.reading)
    }
    @Test fun wholeFirstOperationRangeIsReplaced() {
        val h = Harness("あ"); h.stroke("こう", 0); h.stroke("しゃ", 110, tap=false); assertEquals("あっしゃ", h.reading)
    }
    @Test fun multiCharacterSecondOutputs() {
        for ((text, result) in listOf("こう" to "っこう", "しゃ" to "っしゃ", "シャ" to "ッシャ", "コー" to "ッコー")) {
            val h=Harness();h.stroke("た",0);h.stroke(text,110,tap=false);assertEquals(result,h.reading)
        }
    }
    @Test fun pairsDoNotOverlap() {
        val h=Harness(); val expected=listOf("か","っか","っかか","っかっか")
        expected.forEachIndexed { i, value -> h.stroke("か",i*100L);assertEquals(value,h.reading) }
    }
    @Test fun pauseStartsNewPair() {
        val h=Harness();h.stroke("か",0);h.stroke("か",211);h.stroke("か",311);assertEquals("かっか",h.reading)
    }
    @Test fun intervalImmediatelyBeforeAtAndAfterBoundary() {
        for (gap in listOf(199L,200L,201L)) {
            val h=Harness();h.stroke("か",0);h.stroke("か",10+gap);assertEquals(if(gap<=200)"っか" else "かか",h.reading)
        }
    }
    @Test fun secondUpCanBeAfterDeadline() {
        val h=Harness();h.stroke("た",0);h.stroke("ち",210,900,tap=false);assertEquals("っち",h.reading)
    }
    @Test fun backwardEventTimeDoesNotPair() {
        val h=Harness();h.stroke("か",100);h.stroke("か",90);assertEquals("かか",h.reading)
    }
    @Test fun firstFlickDoesNotArm() {
        val h=Harness();h.stroke("き",0,tap=false);h.stroke("か",110);assertEquals("きか",h.reading)
    }
    @Test fun differentKeyAndLayoutDoNotPair() {
        for(key in listOf("layout:ta","other-layout:ka")) {
            val h=Harness();h.stroke("か",0);h.stroke("か",110,key=key);assertEquals("かか",h.reading)
        }
    }
    @Test fun cancelAndLongPressAndMultiTouchInvalidateWithoutDeletingFirst() {
        repeat(3) { val h=Harness();h.stroke("か",0);h.down(110);h.session.cancel();assertEquals("か",h.reading)
            h.up("き",200,false);assertEquals("かき",h.reading) }
    }
    @Test fun offAndUnsafeFallbackNeverLoseInput() {
        val h=Harness();h.settings=h.settings.copy(enabled=false);h.stroke("か",0);h.stroke("か",110);assertEquals("かか",h.reading)
        h.settings=h.settings.copy(enabled=true);h.stroke("か",300);h.eligible=false;h.stroke("か",400);assertEquals("かかかか",h.reading)
    }
    @Test fun revisionsSessionsOwnersAndContextsInvalidate() {
        repeat(4) { which -> val h=Harness();h.stroke("か",0)
            when(which){0->h.revision+=2;1->h.generation++;2->h.owner=Any();3->h.context="other"}
            h.stroke("か",110);assertEquals("かか",h.reading)
        }
    }
    @Test fun mutationBetweenSecondDownAndUpInvalidates() {
        val h=Harness();h.stroke("か",0);h.down(110);h.revision+=2;h.up("き",200,false);assertEquals("かき",h.reading)
    }
    @Test fun excludedOutputIsEmittedAndPairIsConsumed() {
        for(text in listOf("な","ん","っか","ッカ","漢字","ab","か な","か。","😀","ｶ","ヵ")) {
            val h=Harness();h.stroke("か",0);h.stroke(text,110);assertEquals("か$text",h.reading)
            h.stroke("か",210);assertEquals("か${text}か",h.reading)
        }
    }
    @Test fun settingsChangeInvalidates() {
        val h=Harness();h.stroke("か",0);h.settings=h.settings.copy(intervalMillis=300);h.stroke("か",110);assertEquals("かか",h.reading)
    }
    @Test fun allRowsCanBeEnabledAndNRemainsExcluded() {
        for(text in listOf("あ","な","ま","や","ら","わ")) {
            val h=Harness();h.settings=h.settings.copy(rows=SmallTsuSettings.ALL_ROWS.toSet());h.stroke(text,0);h.stroke(text,110);assertEquals("っ$text",h.reading)
        }
        assertNull(SmallTsuKana.prefix("ん",SmallTsuSettings.ALL_ROWS.toSet()))
    }
    @Test fun normalizationDoesNotChangeOutput() {
        for(text in listOf("か\u3099","カ\u3099","か゛","ハ゜")) {
            val h=Harness();h.stroke("た",0);h.stroke(text,110);assertEquals((if(text.first() in 'ァ'..'ヴ')"ッ" else "っ")+text,h.reading)
        }
    }
    @Test fun nonAppendAndCommitCannotArm() {
        val h=Harness("あ");h.down(0)
        h.session.text("か",true,10,h.settings,h::snapshot){h.reading="か";h.revision++}
        h.stroke("か",110);assertEquals("かか",h.reading)
        h.down(300);h.session.text("か",true,310,h.settings,h::snapshot){h.session.cancel();h.reading+="か";h.revision++}
        h.stroke("か",410);assertEquals("かかかか",h.reading)
    }
}
