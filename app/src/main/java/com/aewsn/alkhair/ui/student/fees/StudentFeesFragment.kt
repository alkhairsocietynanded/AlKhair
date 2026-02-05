package com.aewsn.alkhair.ui.student.fees

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.aewsn.alkhair.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class StudentFeesFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_student_fees, container, false)
    }
}
