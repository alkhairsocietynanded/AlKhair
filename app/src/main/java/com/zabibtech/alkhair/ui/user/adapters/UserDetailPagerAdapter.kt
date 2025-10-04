package com.zabibtech.alkhair.ui.user.adapters

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class UserDetailPagerAdapter(
    activity: AppCompatActivity,
    private val fragmentsWithTitles: List<Pair<String, Fragment>>
) : FragmentStateAdapter(activity) {

    override fun getItemCount() = fragmentsWithTitles.size

    override fun createFragment(position: Int) = fragmentsWithTitles[position].second

    fun getTitle(position: Int) = fragmentsWithTitles[position].first
}
