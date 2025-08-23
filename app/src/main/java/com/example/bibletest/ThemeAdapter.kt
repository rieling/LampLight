package com.example.bibletest

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.recyclerview.widget.RecyclerView

class ThemeAdapter(
    private val themes: List<AppTheme>,
    private val onThemeSelected: (AppTheme) -> Unit,
    private val onAddTheme: () -> Unit,
    private val onDeleteTheme: ((AppTheme) -> Unit)? = null
) : RecyclerView.Adapter<ThemeAdapter.ThemeViewHolder>() {

    override fun getItemCount(): Int = themes.size + 1 // themes + add button

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThemeViewHolder {
        val button = Button(parent.context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                marginEnd = 16
            }
        }
        return ThemeViewHolder(button)
    }

    override fun onBindViewHolder(holder: ThemeViewHolder, position: Int) {
        val button = holder.button

        if (position < themes.size) {
            val theme = themes[position]
            button.text = theme.name
            button.setBackgroundColor(theme.backgroundColor)
            button.setTextColor(theme.textColor)
            button.setOnClickListener { onThemeSelected(theme) }

            holder.itemView.setOnLongClickListener {
                if (onDeleteTheme != null && !isPrebuiltTheme(theme)) {
                    onDeleteTheme.invoke(theme)
                    true
                } else {
                    false
                }
            }
        } else {
            button.text = "+"
            button.setBackgroundColor(0xFFCCCCCC.toInt())
            button.setTextColor(0xFF000000.toInt())
            button.setOnClickListener { onAddTheme() }
            button.setOnLongClickListener(null) // no delete for "+"
        }
    }

    class ThemeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val button: Button = view as Button

    }

    private fun isPrebuiltTheme(theme: AppTheme): Boolean {
        return theme.name == "Light" || theme.name == "Dark"
    }
}
