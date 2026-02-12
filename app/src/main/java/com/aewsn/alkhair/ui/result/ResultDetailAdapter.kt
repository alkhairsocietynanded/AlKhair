package com.aewsn.alkhair.ui.result

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aewsn.alkhair.databinding.ItemResultSubjectBinding
import com.aewsn.alkhair.ui.result.ResultViewModel.ResultUiModel

class ResultDetailAdapter(
    private val results: List<ResultUiModel>
) : RecyclerView.Adapter<ResultDetailAdapter.ResultViewHolder>() {

    inner class ResultViewHolder(private val binding: ItemResultSubjectBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ResultUiModel) {
            if (item.studentName.isNotEmpty()) {
                binding.tvSubjectName.text = "${item.studentName}\n${item.subjectName}"
            } else {
                binding.tvSubjectName.text = item.subjectName
            }
            
            // Format marks: 85.0 -> 85 if whole number
            val marks = if (item.result.marksObtained % 1 == 0.0) {
                item.result.marksObtained.toInt().toString()
            } else {
                item.result.marksObtained.toString()
            }
            
            val total = if (item.result.totalMarks % 1 == 0.0) {
                item.result.totalMarks.toInt().toString()
            } else {
                item.result.totalMarks.toString()
            }
            
            binding.tvMarks.text = "$marks/$total"
            binding.tvGradeOriginal.text = item.result.grade ?: ""
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultViewHolder {
        val binding = ItemResultSubjectBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ResultViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ResultViewHolder, position: Int) {
        holder.bind(results[position])
    }

    override fun getItemCount(): Int = results.size
}
