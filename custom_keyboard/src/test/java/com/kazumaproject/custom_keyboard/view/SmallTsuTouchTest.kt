package com.kazumaproject.custom_keyboard.view

import android.app.Activity
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import com.kazumaproject.core.data.popup.TfbiPopupPresentationMode
import com.kazumaproject.core.domain.small_tsu.*
import com.kazumaproject.custom_keyboard.data.*
import com.kazumaproject.custom_keyboard.haptics.InputHapticContext
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35])
class SmallTsuTouchTest {
    private class Harness(type: KeyType = KeyType.CROSS_FLICK, binding: DoubleTapBinding? = null,
                          val tapText: String = "た", layout: KeyboardLayout? = null) {
        var reading = ""
        var revision = 0L
        var replacement: String? = null
        var releases = 0
        val legacyFlickFlags = mutableListOf<Boolean>()
        val hapticContexts = mutableListOf<InputHapticContext>()
        val owner = Any()
        val session = SmallTsuSession()
        val settings = SmallTsuSettings(enabled=true)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val context = ContextThemeWrapper(activity,
            com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar)
        val view = FlickKeyboardView(context)
        fun snapshot() = SmallTsuSnapshot(owner,1,revision,"kana",reading)
        init {
            view.setTfbiPopupPresentationMode(TfbiPopupPresentationMode.GUIDE_ABOVE_KEY)
            view.setKeyboard(layout ?: KeyboardLayout(
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
                    legacyFlickFlags.add(isFlick)
                    if(action is KeyAction.Text){reading=replacement ?: reading+action.text;revision++}
                }
                override fun onCommittedAction(
                    action: KeyAction,
                    isFlick: Boolean,
                    hapticContext: InputHapticContext
                ) {
                    hapticContexts += hapticContext
                    onAction(action, isFlick)
                }
                override fun onActionLongPress(action:KeyAction){session.cancel()}
                override fun onActionUpAfterLongPress(action:KeyAction){}
                override fun onFlickDirectionChanged(direction:FlickDirection){}
                override fun onFlickActionLongPress(action:KeyAction){session.cancel()}
                override fun onFlickActionUpAfterLongPress(action:KeyAction,isFlick:Boolean){}
            })
            activity.setContentView(view)
            view.measure(View.MeasureSpec.makeMeasureSpec(240,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(120,View.MeasureSpec.EXACTLY))
            view.layout(0,0,240,120)
        }
        fun event(action:Int,down:Long,time:Long,left:Boolean=false) {
            val key=view.getChildAt(0)
            val e=MotionEvent.obtain(down,time,action,key.left+key.width/2f-(if(left)100f else 0f),key.top+key.height/2f,0)
            view.onTouchEvent(e);e.recycle()
        }
        fun tap(time:Long){event(MotionEvent.ACTION_DOWN,time,time);event(MotionEvent.ACTION_UP,time,time+10)}
        fun at(action:Int,down:Long,time:Long,x:Float,y:Float) {
            val key=view.getChildAt(0)
            val e=MotionEvent.obtain(down,time,action,key.left+key.width*x,key.top+key.height*y,0)
            view.onTouchEvent(e);e.recycle()
        }
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
    @Test fun heldTapDoesNotArmOrConsumeAPairEvenWithoutRunningTheTimer(){
        val h=Harness();h.event(MotionEvent.ACTION_DOWN,100,100);h.event(MotionEvent.ACTION_UP,100,2200)
        h.tap(2250);assertEquals("たた",h.reading)
        h.event(MotionEvent.ACTION_DOWN,2300,2300);h.event(MotionEvent.ACTION_UP,2300,4500)
        assertEquals("たたた",h.reading)
    }
    @Test fun builtInKanaSurfacesKeepImmediateTapAndPairIdentity(){
        for(style in listOf("default","circle","sumire","second-flick","third-flick","center-guide-flick")) {
            val h=Harness(layout=singleSa(style));h.tap(100);assertEquals(style,"さ",h.reading)
            h.tap(200);assertEquals(style,"っさ",h.reading)
        }
    }
    @Test fun secondStageUsesFinalVoicedOutput(){
        val h=Harness(layout=singleSa("second-flick"));h.reading="ば";h.tap(100)
        h.at(MotionEvent.ACTION_DOWN,200,200,.5f,.5f)
        h.at(MotionEvent.ACTION_MOVE,200,210,0f,.5f)
        h.at(MotionEvent.ACTION_MOVE,200,220,.1f,1f)
        h.at(MotionEvent.ACTION_UP,200,900,.1f,1f)
        assertEquals("ばっじ",h.reading)
    }
    @Test fun circleAndHierarchyKeepTheirExistingImeCallbackSemantics(){
        for(style in listOf("circle","third-flick")) {
            val h=Harness(layout=singleSa(style));h.tap(100);h.tap(200)
            assertEquals(style,"っさ",h.reading)
            assertEquals(style,listOf(true,true),h.legacyFlickFlags)
        }
    }
    @Test fun hierarchicalStageUsesFinalMultiCharacterOutput(){
        val h=Harness(layout=singleSa("third-flick"));h.tap(100)
        h.at(MotionEvent.ACTION_DOWN,200,200,.5f,.5f)
        h.at(MotionEvent.ACTION_MOVE,200,210,0f,.5f)
        h.at(MotionEvent.ACTION_MOVE,200,220,.65f,1f)
        h.at(MotionEvent.ACTION_UP,200,900,.65f,1f)
        assertEquals("っしょ",h.reading)
        assertEquals(2,h.releases)
        assertEquals(listOf(false,true),h.hapticContexts.map { it.isTwoStepFlick })
    }
    private fun singleSa(style:String):KeyboardLayout {
        val source=KeyboardDefaultLayouts.createFinalLayout(KeyboardInputMode.HIRAGANA,
            emptyMap(),"flick",style)
        return KeyboardLayout(keys=listOf(source.keys.first{it.label=="さ"}.copy(row=0,column=0)),
            flickKeyMaps=source.flickKeyMaps,columnCount=1,rowCount=1,
            circularFlickKeyMaps=source.circularFlickKeyMaps,
            twoStepFlickKeyMaps=source.twoStepFlickKeyMaps,hierarchicalFlickMaps=source.hierarchicalFlickMaps)
    }
}
