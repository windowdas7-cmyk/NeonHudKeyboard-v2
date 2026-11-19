package com.neonhud.keyboard

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo

class NeonImeService : InputMethodService() {
    override fun onCreateInputView(): View =
        NeonKeyboardView(this, object : NeonKeyboardView.Listener {
            override fun onText(t: String) {
                currentInputConnection?.commitText(t, 1)
            }

            override fun onDelete() {
                val ic = currentInputConnection ?: return
                val sel = ic.getSelectedText(0)
                if (sel != null && sel.isNotEmpty()) ic.commitText("", 1)
                else ic.deleteSurroundingText(1, 0)
            }

            override fun onEnter() {
                val ei = currentInputEditorInfo
                val action = (ei?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
                val noAction = ((ei?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
                if (ei != null && !noAction &&
                    action != EditorInfo.IME_ACTION_NONE &&
                    action != EditorInfo.IME_ACTION_UNSPECIFIED
                ) currentInputConnection?.performEditorAction(action)
                else sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
            }
        })
}
