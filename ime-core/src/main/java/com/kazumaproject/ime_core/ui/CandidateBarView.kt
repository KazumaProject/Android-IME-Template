package com.kazumaproject.ime_core.ui

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.ime_core.candidates.Candidate
import kotlin.math.roundToInt

class CandidateBarView(context: Context) : LinearLayout(context) {

    enum class Mode { SUGGESTION, CONTROLS }
    enum class Placement { TOP, BOTTOM }

    private var mode: Mode = Mode.CONTROLS

    var onCandidateClick: ((Candidate) -> Unit)? = null

    var onClickSettings: (() -> Unit)? = null
    var onClickResize: (() -> Unit)? = null
    var onClickToggleMode: (() -> Unit)? = null
    var onClickDefaultSize: (() -> Unit)? = null

    private val recycler: RecyclerView
    private val adapter = CandidateAdapter(
        onClick = { c -> onCandidateClick?.invoke(c) }
    )

    private val controlsRow: LinearLayout
    private val loadingText: TextView

    init {
        orientation = VERTICAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        setPadding(dp(8), dp(6), dp(8), dp(6))
        setBackgroundColor(Color.parseColor("#FF101012"))

        controlsRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.VISIBLE
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        val btnSettings = smallBtn("⚙") { onClickSettings?.invoke() }
        val btnResize = smallBtn("Resize") { onClickResize?.invoke() }
        val btnMode = smallBtn("Mode") { onClickToggleMode?.invoke() }
        val btnDefault = smallBtn("Default") { onClickDefaultSize?.invoke() }

        controlsRow.addView(btnSettings, LayoutParams(0, dp(36)).apply { weight = 0.8f })
        controlsRow.addView(
            btnResize,
            LayoutParams(0, dp(36)).apply { weight = 1.2f; marginStart = dp(8) })
        controlsRow.addView(
            btnMode,
            LayoutParams(0, dp(36)).apply { weight = 1.2f; marginStart = dp(8) })
        controlsRow.addView(
            btnDefault,
            LayoutParams(0, dp(36)).apply { weight = 1.4f; marginStart = dp(8) })

        recycler = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = this@CandidateBarView.adapter
            visibility = View.GONE
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        loadingText = TextView(context).apply {
            text = "Loading…"
            setTextColor(Color.LTGRAY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            visibility = View.GONE
            setPadding(dp(4), dp(2), dp(4), dp(2))
        }

        addView(controlsRow)
        addView(recycler)
        addView(
            loadingText,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM or Gravity.START
            })
    }

    fun setMode(newMode: Mode) {
        mode = newMode
        if (mode == Mode.SUGGESTION) {
            controlsRow.visibility = View.GONE
            recycler.visibility = View.VISIBLE
        } else {
            setLoading(false)
            recycler.visibility = View.GONE
            controlsRow.visibility = View.VISIBLE
        }
    }

    fun setLoading(loading: Boolean) {
        loadingText.visibility = if (loading && mode == Mode.SUGGESTION) View.VISIBLE else View.GONE
    }

    fun submitCandidates(list: List<Candidate>) {
        adapter.submit(list)
    }

    fun setSelectedIndex(index: Int) {
        adapter.setSelectedIndex(index)
    }

    private fun smallBtn(text: String, onClick: () -> Unit): Button {
        return Button(context).apply {
            this.text = text
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#FF2C2C2E"))
            setOnClickListener { onClick() }
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()

    private class CandidateAdapter(
        private val onClick: (Candidate) -> Unit
    ) : RecyclerView.Adapter<CandidateVH>() {

        private val items = ArrayList<Candidate>()
        private var selectedIndex: Int = -1

        fun submit(list: List<Candidate>) {
            items.clear()
            items.addAll(list)
            // 候補更新で選択が範囲外なら解除
            if (selectedIndex !in items.indices) selectedIndex = -1
            notifyDataSetChanged()
        }

        fun setSelectedIndex(index: Int) {
            selectedIndex = index
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CandidateVH {
            val btn = Button(parent.context).apply {
                isAllCaps = false
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(Color.WHITE)
            }
            return CandidateVH(btn)
        }

        override fun onBindViewHolder(holder: CandidateVH, position: Int) {
            val c = items[position]
            holder.btn.text = c.surface

            val isSel = (position == selectedIndex)
            holder.btn.setBackgroundColor(
                if (isSel) Color.parseColor("#FF3B82F6") else Color.parseColor("#FF1F1F22")
            )

            holder.btn.setOnClickListener { onClick(c) }
        }

        override fun getItemCount(): Int = items.size
    }

    private class CandidateVH(val btn: Button) : RecyclerView.ViewHolder(btn)
}
