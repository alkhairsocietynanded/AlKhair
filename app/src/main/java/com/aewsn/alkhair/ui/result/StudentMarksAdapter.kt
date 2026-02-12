package com.aewsn.alkhair.ui.result

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aewsn.alkhair.databinding.ItemStudentMarksBinding

class StudentMarksAdapter(
    private val entries: MutableList<ResultViewModel.StudentMarkEntry>
) : RecyclerView.Adapter<StudentMarksAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemStudentMarksBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var textWatcher: TextWatcher? = null

        fun bind(entry: ResultViewModel.StudentMarkEntry, position: Int) {
            binding.tvStudentName.text = entry.studentName

            // Remove old watcher to avoid duplicate triggers
            textWatcher?.let { binding.etMarks.removeTextChangedListener(it) }

            // Set existing marks if any
            if (entry.marksObtained >= 0) {
                binding.etMarks.setText(entry.marksObtained.toString())
            } else {
                binding.etMarks.text = null
            }

            // Add new watcher
            textWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val marks = s.toString().toIntOrNull() ?: -1
                    entries[position] = entries[position].copy(marksObtained = marks)
                }
            }
            binding.etMarks.addTextChangedListener(textWatcher)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemStudentMarksBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(entries[position], position)
    }

    override fun getItemCount() = entries.size

    fun getEntries(): List<ResultViewModel.StudentMarkEntry> = entries.toList()
}
