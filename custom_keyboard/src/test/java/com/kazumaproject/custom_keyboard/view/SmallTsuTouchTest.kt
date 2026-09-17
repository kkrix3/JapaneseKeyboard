package com.kazumaproject.custom_keyboard.view

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.domain.small_tsu.*
import com.kazumaproject.custom_keyboard.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35])
class SmallTsuTouchTest {
    private class Harness(type: KeyType = KeyType.CROSS_FLICK, binding: DoubleTapBinding? = null,
                          val tapText: String = "た") {
        var reading = ""
        var revision = 0L
        var replacement: String? = null
        var releases = 0
        val owner = Any()
        val session = SmallTsuSession()
        val settings = SmallTsuSettings(enabled=true)
        val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(),
            com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar)
        val view = FlickKeyboardView(context)
        fun snapshot() = SmallTsuSnapshot(owner,1,revision,"kana",reading)
        init {
            view.setKeyboard(KeyboardLayout(
                keys=listOf(KeyData("た",0,0,true,KeyAction.Text(tapText),keyId="ta",keyType=type,doubleTapBinding=binding)),
                flickKeyMaps=mapOf("ta" to listOf(mapOf(FlickDirection.TAP to FlickAction.Input(tapText),
                    FlickDirection.UP_LEFT_FAR to FlickAction.Input("ち")))),columnCount=1,rowCount=1))
            view.kanaGestureObserver=object:KanaGestureObserver {
                override fun down(key:String,eventTime:Long)=session.down(key,eventTime,snapshot(),settings)
                override fun cancel()=session.cancel()
                override fun text(text:String,tap:Boolean,eventTime:Long,dispatch:()->Unit) {
                    session.text(text,tap,eventTime,settings,::snapshot) { next ->
                        replacement=next;dispatch();replacement=null
                    }
                }
            }
            view.setOnKeyboardActionListener(object:FlickKeyboardView.OnKeyboardActionListener {
                override fun onPress(action:KeyAction){}
                override fun onAction(action:KeyAction,isFlick:Boolean) {
                    releases++
                    if(action is KeyAction.Text){reading=replacement ?: reading+action.text;revision++}
                }
                override fun onActionLongPress(action:KeyAction){session.cancel()}
                override fun onActionUpAfterLongPress(action:KeyAction){}
                override fun onFlickDirectionChanged(direction:FlickDirection){}
                override fun onFlickActionLongPress(action:KeyAction){session.cancel()}
                override fun onFlickActionUpAfterLongPress(action:KeyAction,isFlick:Boolean){}
            })
            view.measure(View.MeasureSpec.makeMeasureSpec(240,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(120,View.MeasureSpec.EXACTLY))
            view.layout(0,0,240,120)
        }
        fun event(action:Int,down:Long,time:Long,left:Boolean=false) {
            val key=view.getChildAt(0)
            val e=MotionEvent.obtain(down,time,action,key.left+key.width/2f-(if(left)100f else 0f),key.top+key.height/2f,0)
            view.onTouchEvent(e);e.recycle()
        }
        fun tap(time:Long){event(MotionEvent.ACTION_DOWN,time,time);event(MotionEvent.ACTION_UP,time,time+10)}
    }
    @Test fun realTouchCallbacksAreImmediateAndPairsDoNotOverlap(){
        val h=Harness();h.tap(100);assertEquals("た",h.reading)
        h.tap(200);assertEquals("った",h.reading)
        h.tap(300);h.tap(400);assertEquals("ったった",h.reading);assertEquals(4,h.releases)
    }
    @Test fun secondFlickFinalOutputUsesDownTiming(){
        val h=Harness();h.reading="あ";h.tap(100)
        h.event(MotionEvent.ACTION_DOWN,250,250);h.event(MotionEvent.ACTION_MOVE,250,280,true)
        h.event(MotionEvent.ACTION_UP,250,900,true)
        assertEquals("あっち",h.reading);assertEquals(2,h.releases)
    }
    @Test fun cancellationRetainsFirstInput(){
        val h=Harness();h.tap(100);h.event(MotionEvent.ACTION_DOWN,200,200)
        h.event(MotionEvent.ACTION_CANCEL,200,220);assertEquals("た",h.reading)
        h.tap(300);assertEquals("たた",h.reading)
    }
    @Test fun hiddenViewInvalidatesFirstInput(){
        val h=Harness();h.tap(100);h.view.visibility=View.GONE;h.view.visibility=View.VISIBLE;h.tap(200)
        assertEquals("たた",h.reading)
    }
    @Test fun explicitBindingExcludesAutomaticSmallTsu(){
        val h=Harness(KeyType.NORMAL,DoubleTapBinding(KeyAction.Copy,DoubleTapPolicy.PROMOTE))
        h.tap(100);h.tap(200);assertEquals("たた",h.reading)
    }
    @Test fun multiCharacterFirstTapRemovesItsEntireRange(){
        val h=Harness(tapText="たた");h.tap(100);h.tap(200);assertEquals("ったた",h.reading)
    }
}
