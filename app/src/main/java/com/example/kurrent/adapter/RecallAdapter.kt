package com.example.kurrent.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.kurrent.databinding.ItemRecallCardBinding
import com.example.kurrent.model.RecallItem

class RecallAdapter(
    private var recalls: List<RecallItem>
) : RecyclerView.Adapter<RecallAdapter.RecallViewHolder>() {

    fun updateRecalls(newRecalls: List<RecallItem>) {
        this.recalls = newRecalls
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecallViewHolder {
        val binding = ItemRecallCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return RecallViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecallViewHolder, position: Int) {
        holder.bind(recalls[position])
    }

    override fun getItemCount(): Int = recalls.size

    inner class RecallViewHolder(private val binding: ItemRecallCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(recall: RecallItem) {
            binding.tvRecallDate.text = "RECALL • ${recall.date}"
            binding.tvRecallCategory.text = recall.category
            binding.tvRecallTitle.text = recall.title
            binding.tvRecallFact.text = recall.fact
        }
    }
}
