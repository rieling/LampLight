package com.example.bibletest

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView

class ThemeAdapter(
    private val themes: List<FontSettingsActivity.Theme>,
    private val onThemeSelected: ((FontSettingsActivity.Theme) -> Unit)? = null
) : RecyclerView.Adapter<ThemeAdapter.ThemeViewHolder>() {

    private var selectedIndex = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThemeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_theme, parent, false)
        return ThemeViewHolder(view)
    }

    override fun getItemCount() = themes.size

    override fun onBindViewHolder(holder: ThemeViewHolder, position: Int) {
        val theme = themes[position]
        holder.bind(theme, position == selectedIndex)

        holder.itemView.setOnClickListener {
            val adapterPosition = holder.adapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) {
                selectedIndex = adapterPosition
                notifyDataSetChanged()
                onThemeSelected?.invoke(themes[adapterPosition])
            }
        }

    }

    class ThemeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val bgView: View = itemView.findViewById(R.id.theme_bg)
        private val checkView: View = itemView.findViewById(R.id.theme_selected_check)

        fun bind(theme: FontSettingsActivity.Theme, isSelected: Boolean) {
            bgView.setBackgroundColor(Color.parseColor(theme.backgroundColor))
            checkView.visibility = if (isSelected) View.VISIBLE else View.GONE
        }
    }
}