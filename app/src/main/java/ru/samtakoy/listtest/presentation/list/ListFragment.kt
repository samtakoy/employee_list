package ru.samtakoy.listtest.presentation.list

import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.*
import androidx.core.app.SharedElementCallback
import androidx.core.view.ViewCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.onNavDestinationSelected
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.fragment_employee_list.*
import kotlinx.android.synthetic.main.fragment_employee_list.view.*
import moxy.MvpAppCompatFragment
import moxy.ktx.moxyPresenter
import ru.samtakoy.listtest.R
import ru.samtakoy.listtest.app.Di
import ru.samtakoy.listtest.domain.model.Employee
import ru.samtakoy.listtest.presentation.list.inner.EmployeeListAdapter
import ru.samtakoy.listtest.presentation.list.inner.EmployeeViewHolder
import ru.samtakoy.listtest.presentation.list.inner.InfiniteScrollListener
import ru.samtakoy.listtest.presentation.list.inner.SwipeItemHelper
import ru.samtakoy.listtest.presentation.shared.SharedEmployeeViewModel
import ru.samtakoy.listtest.presentation.transitionPair
import ru.samtakoy.listtest.utils.extensions.positionOf
import javax.inject.Inject
import javax.inject.Provider

private const val TAG = "ListFragment"

class ListFragment : MvpAppCompatFragment(), ListView, SwipeItemHelper.SwipeListener{

    @Inject
    lateinit var presenterProvider: Provider<ListPresenter>

    private val presenter by moxyPresenter { presenterProvider.get() }

    private lateinit var recyclerViewAdapter: EmployeeListAdapter
    private lateinit var recyclerLayoutManager: LinearLayoutManager
    private lateinit var swipeItemHelper: SwipeItemHelper

    private val currentEmployeeSharedModel: SharedEmployeeViewModel by lazy {
        ViewModelProvider(requireActivity()).get(SharedEmployeeViewModel::class.java)
    }

    private val recyclerViewPreDrawListener: ViewTreeObserver.OnPreDrawListener = ViewTreeObserver.OnPreDrawListener {
        resetRecyclerViewPreDrawListeners()
        tryScrollOneItemDown()
        true
    }

    private val recyclerViewPreDrawRestorationListener: ViewTreeObserver.OnPreDrawListener = ViewTreeObserver.OnPreDrawListener {
        resetRecyclerViewPreDrawListeners()
        ensureCurrentItemVisibility()
        startPostponedEnterTransition()
        true
    }

    private fun resetRecyclerViewPreDrawListeners() {
        recyclerView.viewTreeObserver.removeOnPreDrawListener (recyclerViewPreDrawListener)
        recyclerView.viewTreeObserver.removeOnPreDrawListener (recyclerViewPreDrawRestorationListener)
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        Di.appComponent.inject(this)
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view: View = inflater.inflate(R.layout.fragment_employee_list, container, false);

        recyclerViewAdapter = createAdapter()
        setupRecyclerView(view, recyclerViewAdapter)

        // for shared element back transition
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            prepareTransitions()
            if(currentEmployeeSharedModel.isCurrentEmployeeSetted()){
                postponeEnterTransition()
                view.recyclerView.viewTreeObserver.addOnPreDrawListener (recyclerViewPreDrawRestorationListener)
            }
        }else{
            if(currentEmployeeSharedModel.isCurrentEmployeeSetted()){
                view.recyclerView.viewTreeObserver.addOnPreDrawListener (recyclerViewPreDrawRestorationListener)
            }
        }

        return view
    }

    override fun onStart() {
        super.onStart()

        swipeItemHelper.attachToRecyclerView(recyclerView)
        // for test
        presenter.onUiCheckCacheStatus()
    }

    override fun onStop() {

        swipeItemHelper.detachToRecyclerView()

        super.onStop()
    }

    private fun setupRecyclerView(view: View, employeeListAdapter: EmployeeListAdapter) {

        recyclerLayoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)

        view.recyclerView.run{

            layoutManager = recyclerLayoutManager
            adapter = employeeListAdapter

            addOnScrollListener(createInfiniteScrollListener(layoutManager as LinearLayoutManager))

            swipeItemHelper = SwipeItemHelper(requireContext(), this@ListFragment)

        }
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder) {
        // do nothing yet
    }

    private fun createInfiniteScrollListener(linearLayoutManager: LinearLayoutManager): RecyclerView.OnScrollListener {

        return object: InfiniteScrollListener(linearLayoutManager) {
            override fun loadMoreItems() {
                presenter.onUiGetMoreEmployees()
            }
            override fun isLoading(): Boolean = false
        }
    }

    private fun createAdapter(): EmployeeListAdapter {
        return EmployeeListAdapter{ view, employee ->
            presenter.onUiEmployeeClick(employee.id)
        }.apply {
            stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        }
    }

    override fun showMessage(messageId: Int) {
        Snackbar.make(requireActivity().window.decorView, messageId, Snackbar.LENGTH_SHORT).show()
    }

    override fun setData(data: List<Employee>) {

        val isScrollNeededToNewData = recyclerViewAdapter.itemCount > 0
                && recyclerViewAdapter.itemCount < data.size

        recyclerViewAdapter.employeeList = data

        if(isScrollNeededToNewData){
            recyclerView.viewTreeObserver.addOnPreDrawListener (recyclerViewPreDrawListener)
        }
    }

    override fun showDataLoading() {
        //loadingProgress.show()
        loadingProgress.visibility = View.VISIBLE
    }

    override fun hideDataLoading() {

        if(loadingProgress.visibility == View.VISIBLE) {
            //loadingProgress.hide()
            loadingProgress.visibility = View.GONE
        }
    }

    private fun tryScrollOneItemDown() {

        recyclerView.getViewTreeObserver().removeOnPreDrawListener (recyclerViewPreDrawListener)
        if(recyclerLayoutManager.findLastVisibleItemPosition() < recyclerViewAdapter.itemCount){
            recyclerView.post {
                recyclerView.layoutManager!!.startSmoothScroll(
                    createSmoothScrollerToPosition(recyclerLayoutManager.findLastVisibleItemPosition()+1)
                )
            }
        }
    }

    private fun ensureCurrentItemVisibility(){

        val itemPosition = recyclerViewAdapter.employeeList.positionOf(
            currentEmployeeSharedModel.currentEmployeeId
        )

        val firstPos = recyclerLayoutManager.findFirstVisibleItemPosition()
        val lastPos = recyclerLayoutManager.findLastVisibleItemPosition()
        if(itemPosition >= 0 && (itemPosition < firstPos  || itemPosition > lastPos)){
            recyclerView.scrollToPosition(itemPosition)
        }
    }

    private fun createSmoothScrollerToPosition(targetPos: Int): LinearSmoothScroller{
        val smoothScroller = object: LinearSmoothScroller(context){
            override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics?): Float {
                return super.calculateSpeedPerPixel(displayMetrics)*10
            }
        }
        smoothScroller.targetPosition = targetPos
        return smoothScroller
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {

        inflater.inflate(R.menu.menu_list, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return item.onNavDestinationSelected(findNavController()) || super.onOptionsItemSelected(item)
    }

    override fun navigateToEmployeeDetails(employeeId: Int) {
        var holder: EmployeeViewHolder? = null

        currentEmployeeSharedModel.currentEmployeeId = employeeId

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
            holder = getExistsRecyclerItemViewByEmployeeId(employeeId) as EmployeeViewHolder

            if(holder != null){

                val extras = FragmentNavigatorExtras(
                    holder.avatarTrView.transitionPair(),
                    holder.firstNameTrView.transitionPair(),
                    holder.lastNameTrView.transitionPair()
                )
                findNavController().navigate(ListFragmentDirections.toDetailsPager(employeeId), extras)
                return
            }
        }

        // usual navigation
        findNavController().navigate(ListFragmentDirections.toDetailsPager(employeeId))
    }

    private fun getExistsRecyclerItemViewByEmployeeId(employeeId: Int): EmployeeViewHolder?{

        for(i in 0 until recyclerLayoutManager.childCount){
            val view = recyclerLayoutManager.getChildAt(i)
            if(view != null) {
                val viewHolder = recyclerView.getChildViewHolder(view) as EmployeeViewHolder
                if(viewHolder.employeeId == employeeId){
                    return viewHolder
                }
            }
        }
        return null
    }

    private fun prepareTransitions() {
        setExitSharedElementCallback(object : SharedElementCallback() {
            override fun onMapSharedElements(
                names: MutableList<String>,
                sharedElements: MutableMap<String, View>
            ) {
                mapSharedElements(names, sharedElements, currentEmployeeSharedModel.currentEmployeeId)
            }
        })
    }

    private fun mapSharedElements(names: MutableList<String>,
                                  sharedElements: MutableMap<String, View>,
                                  employeeId: Int
    ){
        val holder = getExistsRecyclerItemViewByEmployeeId(employeeId)
        if(holder?.itemView != null) {

            mapOneSharedElements(names, sharedElements, holder.avatarTrView)
            mapOneSharedElements(names, sharedElements, holder.firstNameTrView)
            mapOneSharedElements(names, sharedElements, holder.lastNameTrView)
        }
    }

    private fun mapOneSharedElements(
        names: MutableList<String>,
        sharedElements: MutableMap<String, View>,
        view: View
    ) {
        val transitionName = ViewCompat.getTransitionName(view)
        if(transitionName != null) {

            val namePrefix: String = transitionName.substringBefore(":")
            for(name in names){
                if(name.startsWith(namePrefix)){
                    sharedElements[name] = view
                    return
                }
            }
        }
    }

}