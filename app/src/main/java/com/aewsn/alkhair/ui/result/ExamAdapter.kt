package com.aewsn.alkhair.ui.result

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aewsn.alkhair.data.models.Exam
import com.aewsn.alkhair.databinding.ItemExamBinding
import com.aewsn.alkhair.utils.visible
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExamAdapter(
    private val exams: List<Exam>,
    private val onClick: (Exam) -> Unit
) : RecyclerView.Adapter<ExamAdapter.ExamViewHolder>() {

    inner class ExamViewHolder(private val binding: ItemExamBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(exam: Exam) {
            binding.tvExamTitle.text = exam.title
            
            val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
            val start = dateFormat.format(Date(exam.startDate))
            val end = dateFormat.format(Date(exam.endDate))
            binding.tvExamDate.text = "$start - $end"
            
            if (exam.isPublished) {
                binding.chipStatus.text = "Published"
                binding.chipStatus.setTextColor(android.graphics.Color.parseColor("#059669"))
                binding.chipStatus.setBackgroundResource(com.aewsn.alkhair.R.drawable.bg_status_badge)
            } else {
                binding.chipStatus.text = "Draft"
                binding.chipStatus.setTextColor(android.graphics.Color.parseColor("#D97706"))
                binding.chipStatus.setBackgroundResource(com.aewsn.alkhair.R.drawable.bg_status_badge_draft)
            }
            binding.chipStatus.visible()
            
            binding.root.setOnClickListener { onClick(exam) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExamViewHolder {
        val binding = ItemExamBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ExamViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ExamViewHolder, position: Int) {
        holder.bind(exams[position])
    }

    override fun getItemCount(): Int = exams.size
}
