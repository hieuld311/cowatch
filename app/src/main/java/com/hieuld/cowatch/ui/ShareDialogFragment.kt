package com.hieuld.cowatch.ui

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.hieuld.cowatch.R
import com.hieuld.cowatch.display.DisplayInfo

class ShareDialogFragment : DialogFragment() {

    interface Callback {
        fun onStartSharing(displayIds: Set<Int>)
        fun onShareDialogDismissedWithoutSharing()
    }

    private val selectedDisplayIds = mutableSetOf<Int>()
    private var sharingStarted = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val callback = requireActivity() as Callback
        val frontActivity = requireActivity() as FrontPlayerActivity

        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_share, null)

        val cbShareAll = view.findViewById<CheckBox>(R.id.cbShareAll)
        val displayContainer = view.findViewById<LinearLayout>(R.id.displayContainer)
        val btnStartSharing = view.findViewById<Button>(R.id.btnStartSharing)

        val displays: List<DisplayInfo> = frontActivity.getShareTargets()

        if (displays.isEmpty()) {
            val emptyCheckBox = CheckBox(requireContext()).apply {
                text = "No secondary display found"
                isEnabled = false
            }

            displayContainer.addView(emptyCheckBox)
        }

        val displayCheckboxes = displays.map { displayInfo ->
            CheckBox(requireContext()).apply {
                text = "${displayInfo.name} / id=${displayInfo.displayId}"
                isChecked = false

                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        selectedDisplayIds.add(displayInfo.displayId)
                    } else {
                        selectedDisplayIds.remove(displayInfo.displayId)
                    }
                }

                displayContainer.addView(this)
            }
        }

        cbShareAll.setOnCheckedChangeListener { _, checked ->
            displayCheckboxes.forEach { it.isChecked = checked }
        }

        btnStartSharing.setOnClickListener {
            sharingStarted = true
            callback.onStartSharing(selectedDisplayIds.toSet())
            dismiss()
        }

        return AlertDialog.Builder(requireContext())
            .setView(view)
            .create()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)

        if (!sharingStarted) {
            (activity as? Callback)?.onShareDialogDismissedWithoutSharing()
        }
    }
}