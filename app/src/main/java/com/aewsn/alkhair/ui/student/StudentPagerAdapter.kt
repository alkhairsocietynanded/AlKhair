package com.aewsn.alkhair.ui.student

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.aewsn.alkhair.data.models.User
import com.aewsn.alkhair.ui.student.dashboard.StudentDashboardFragment
import com.aewsn.alkhair.ui.user.fragments.FeesFragment
import com.aewsn.alkhair.ui.user.fragments.ProfileFragment

class StudentPagerAdapter(
    activity: FragmentActivity,
    private val user: User
) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> StudentDashboardFragment()
            1 -> FeesFragment.newInstance(user)
            2 -> ProfileFragment.newInstance(user)
            else -> StudentDashboardFragment()
        }
    }
}
