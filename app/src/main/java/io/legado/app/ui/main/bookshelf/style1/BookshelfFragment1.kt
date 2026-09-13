package io.legado.app.ui.main.bookshelf.style1

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.databinding.FragmentBookshelf1Binding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.ui.book.group.GroupEditDialog
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.main.bookshelf.BaseBookshelfFragment
import io.legado.app.ui.main.bookshelf.style1.books.BooksFragment
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding

/**
 * 书架界面
 */
class BookshelfFragment1() : BaseBookshelfFragment(R.layout.fragment_bookshelf1),
    TabLayout.OnTabSelectedListener,
    SearchView.OnQueryTextListener {

    constructor(position: Int) : this() {
        val bundle = Bundle()
        bundle.putInt("position", position)
        arguments = bundle
    }

    private val binding by viewBinding(FragmentBookshelf1Binding::bind)
    private val adapter by lazy { TabFragmentPageAdapter(childFragmentManager, viewLifecycleOwner.lifecycle) }
    private val tabLayout: TabLayout by lazy { binding.tabLayout }
    private val bookGroups = mutableListOf<BookGroup>()
    private var fakeDragAnimator: ValueAnimator? = null
    override val groupId: Long get() = selectedGroup?.groupId ?: 0

    override val books: List<Book>
        get() {
            val fragment = findFragment(groupId)
            return fragment?.getBooks() ?: emptyList()
        }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        setSupportToolbar(binding.titleBar.toolbar)
        binding.titleBar.setTitleTextAppearance(R.style.TextAppearance_Legado_PageTitle)
        initView()
        initBookGroupData()
    }

    private val selectedGroup: BookGroup?
        get() = bookGroups.getOrNull(tabLayout.selectedTabPosition)

    /**
     * FragmentStateAdapter 以 getItemId 作为 Fragment tag 保存 ("f" + itemId)
     */
    private fun findFragment(groupId: Long): BooksFragment? {
        return childFragmentManager.findFragmentByTag("f$groupId") as? BooksFragment
    }

    private fun initView() {
        binding.viewPagerBookshelf.offscreenPageLimit = 1
        binding.viewPagerBookshelf.adapter = adapter
        binding.viewPagerBookshelf.registerOnPageChangeCallback(pageChangeCallback)
        tabLayout.addOnTabSelectedListener(this)
        tabLayout.tabMode = TabLayout.MODE_SCROLLABLE
        tabLayout.setSelectedTabIndicatorColor(requireContext().accentColor)
        upSortLabel()
        binding.tvSort.setOnClickListener { showSortMenu() }
    }

    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        private var scrollState = ViewPager2.SCROLL_STATE_IDLE

        override fun onPageScrollStateChanged(state: Int) {
            scrollState = state
        }

        override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
            // 胶囊指示器实时跟随页面滑动，与内容保持同步（与官方 TabLayoutMediator 一致）
            if (position in bookGroups.indices) {
                tabLayout.setScrollPosition(position, positionOffset, true, true)
            }
        }

        override fun onPageSelected(position: Int) {
            val tab = tabLayout.getTabAt(position) ?: return
            if (tabLayout.selectedTabPosition == position) {
                return
            }
            // 滑动/settle 过程中指示器由 onPageScrolled 驱动，select 不再另起动画
            tabLayout.selectTab(tab, scrollState == ViewPager2.SCROLL_STATE_IDLE)
        }
    }

    /**
     * 点击 tab 用 fakeDrag 模拟匀减速滑动，比 setCurrentItem 的默认动画更平滑
     */
    private fun smoothScrollTo(position: Int) {
        val viewPager = binding.viewPagerBookshelf
        if (position == viewPager.currentItem || position !in bookGroups.indices) {
            return
        }
        val distance = (position - viewPager.currentItem) * viewPager.width.toFloat()
        if (!viewPager.beginFakeDrag()) {
            viewPager.setCurrentItem(position, true)
            return
        }
        fakeDragAnimator?.cancel()
        var lastOffset = 0f
        fakeDragAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener { animation ->
                if (viewPager.isFakeDragging) {
                    val offset = -distance * (animation.animatedValue as Float)
                    viewPager.fakeDragBy(offset - lastOffset)
                    lastOffset = offset
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (viewPager.isFakeDragging) {
                        viewPager.endFakeDrag()
                    }
                }
            })
            start()
        }
    }

    private fun upSortLabel() {
        binding.tvSort.text = when (AppConfig.bookshelfSort) {
            1 -> getString(R.string.bookshelf_px_1)
            2 -> getString(R.string.bookshelf_px_2)
            3 -> getString(R.string.bookshelf_px_3)
            4 -> getString(R.string.bookshelf_px_4)
            5 -> getString(R.string.bookshelf_px_5)
            else -> getString(R.string.bookshelf_px_0)
        }
    }

    private fun showSortMenu() {
        val popup = PopupMenu(requireContext(), binding.tvSort)
        for (i in 0..5) {
            val titleRes = when (i) {
                1 -> R.string.bookshelf_px_1
            2 -> R.string.bookshelf_px_2
            3 -> R.string.bookshelf_px_3
                4 -> R.string.bookshelf_px_4
                5 -> R.string.bookshelf_px_5
                else -> R.string.bookshelf_px_0
            }
            popup.menu.add(0, i, i, titleRes)
        }
        popup.setOnMenuItemClickListener {
            if (AppConfig.bookshelfSort != it.itemId) {
                AppConfig.bookshelfSort = it.itemId
                upSortLabel()
                upSort()
            }
            true
        }
        popup.show()
    }

    override fun onDestroyView() {
        fakeDragAnimator?.cancel()
        binding.viewPagerBookshelf.unregisterOnPageChangeCallback(pageChangeCallback)
        super.onDestroyView()
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        SearchActivity.start(requireContext(), query)
        return false
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        return false
    }

    @Synchronized
    override fun upGroup(data: List<BookGroup>) {
        if (data.isEmpty()) {
            appDb.bookGroupDao.enableGroup(BookGroup.IdAll)
        } else {
            if (data != bookGroups) {
                bookGroups.clear()
                bookGroups.addAll(data)
                syncFragments()
                adapter.notifyDataSetChanged()
                upTabs()
            }
        }
    }

    private fun upTabs() {
        tabLayout.removeOnTabSelectedListener(this)
        tabLayout.removeAllTabs()
        for (i in bookGroups.indices) {
            tabLayout.addTab(tabLayout.newTab().setText(bookGroups[i].groupName), false)
        }
        for (i in bookGroups.indices) {
            tabLayout.getTabAt(i)?.view?.apply {
                setOnClickListener {
                    AppConfig.saveTabPosition = i
                    tabLayout.getTabAt(i)?.select()
                    smoothScrollTo(i)
                }
                setOnLongClickListener {
                    showDialogFragment(GroupEditDialog(bookGroups[i]))
                    true
                }
            }
        }
        tabLayout.addOnTabSelectedListener(this)
        if (bookGroups.isNotEmpty()) {
            val position = AppConfig.saveTabPosition.coerceIn(0, bookGroups.size - 1)
            binding.viewPagerBookshelf.setCurrentItem(position, false)
        }
    }

    override fun upSort() {
        syncFragments()
        adapter.notifyDataSetChanged()
    }

    private fun syncFragments() {
        for (group in bookGroups) {
            findFragment(group.groupId)?.let { fragment ->
                fragment.setEnableRefresh(group.enableRefresh)
                val bookSort = group.getRealBookSort()
                if (fragment.bookSort != bookSort) {
                    fragment.upBookSort(bookSort)
                }
            }
        }
    }

    override fun onTabReselected(tab: TabLayout.Tab) {
        selectedGroup?.let { group ->
            findFragment(group.groupId)?.let {
                toastOnUi("${group.groupName}(${it.getBooksCount()})")
            }
        }
    }

    override fun onTabUnselected(tab: TabLayout.Tab) = Unit

    override fun onTabSelected(tab: TabLayout.Tab) {
        AppConfig.saveTabPosition = tab.position
    }

    override fun gotoTop() {
        findFragment(groupId)?.gotoTop()
    }

    private inner class TabFragmentPageAdapter(
        fm: FragmentManager,
        lifecycle: Lifecycle
    ) : FragmentStateAdapter(fm, lifecycle) {

        override fun getItemCount(): Int {
            return bookGroups.size
        }

        override fun createFragment(position: Int): Fragment {
            val group = bookGroups[position]
            return BooksFragment(position, group)
        }

        /**
         * 以 groupId 作为稳定 item id，分组变化时仅重建对应页
         */
        override fun getItemId(position: Int): Long {
            return bookGroups[position].groupId
        }

        override fun containsItem(itemId: Long): Boolean {
            return bookGroups.any { it.groupId == itemId }
        }

    }
}
