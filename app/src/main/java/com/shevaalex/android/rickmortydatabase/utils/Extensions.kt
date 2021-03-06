package com.shevaalex.android.rickmortydatabase.utils

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.navigation.NavDirections
import androidx.navigation.fragment.FragmentNavigator
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.shevaalex.android.rickmortydatabase.R

/**
 * Use everywhere except from Activity (Custom View, Fragment, Dialogs, DialogFragments).
 */
fun View.hideKeyboard() {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(windowToken, 0)
}

/**
 * shows a dialog with given text @param errorMessage
 */
fun Activity.displayErrorDialog(errorMessage: String?) {
    MaterialDialog(this)
            .show {
                title(R.string.dialog_error_title)
                message(text = errorMessage)
                positiveButton(text = "OK")
            }
}

fun Fragment.setupToolbarWithNavController(toolbar: Toolbar) {
    val navController = findNavController()
    //Set the action bar to show appropriate title, set top level destinations
    val appBarConfiguration = AppBarConfiguration(setOf(
            R.id.charactersListFragment,
            R.id.locationsListFragment,
            R.id.episodesListFragment))
    toolbar.setupWithNavController(
            navController,
            appBarConfiguration
    )
}

fun <T> Fragment.getSearchSuggectionsAdapter(list: List<T>): ArrayAdapter<T> =
        ArrayAdapter(
                requireContext(),
                R.layout.item_search_suggestions,
                list)

fun <T> Fragment.getRecentQueriesAdapter(list: List<T>): ArrayAdapter<T> =
        ArrayAdapter(
                requireContext(),
                R.layout.item_recent_suggestions,
                list)

fun Fragment.clearUi(toolbar: Toolbar) {
    toolbar.findViewById<SearchView>(R.id.search_view)?.clearFocus()
    view?.requestFocus()
    view?.hideKeyboard()
}

fun <T : RecyclerView.ViewHolder> Fragment.setGridOrLinearRecyclerView(
        recyclerView: RecyclerView,
        adapter: RecyclerView.Adapter<T>?) {
    if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        val spanCount = calculateNumberOfRows(requireContext())
        val gridLayoutManager = GridLayoutManager(
                requireContext(),
                spanCount,
                RecyclerView.HORIZONTAL,
                false
        )
        recyclerView.layoutManager = gridLayoutManager
        recyclerView.setOnApplyWindowInsetsListener { v, insets ->
            val insetsCompat = WindowInsetsCompat.toWindowInsetsCompat(insets)
            val systemWindow = insetsCompat.getInsets(
                    WindowInsetsCompat.Type.statusBars()
            )
            v.updatePadding(top = systemWindow.top)
            insets
        }
        // apply spacing to gridlayout
        val rowSpacing = getDimensPx(requireContext(), R.dimen.item_grid_spacing)
        val itemDecoration = CustomItemDecoration(
                spanCount = spanCount,
                minimalSpacing = rowSpacing
        )
        recyclerView.addItemDecoration(itemDecoration)
    } else {
        val linearLayoutManager = LinearLayoutManager(requireContext())
        recyclerView.layoutManager = linearLayoutManager
    }
    recyclerView.setHasFixedSize(true)
    //prevent the adapter to restore the list position
    adapter?.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT
    //set the adapter to recyclerview
    recyclerView.adapter = adapter
}

fun View.setTopPaddingForStatusBar() {
    this.setOnApplyWindowInsetsListener { v, insets ->
        val insetsCompat = WindowInsetsCompat.toWindowInsetsCompat(insets)
        val systemWindow = insetsCompat.getInsets(
                WindowInsetsCompat.Type.statusBars()
        )
        v.updatePadding(top = systemWindow.top)
        insets
    }
}

/**
 * extension function to fix navigation double-click crash
 * Author: Abner Escócio
 * https://stackoverflow.com/a/65959445/11836178
 */
fun Fragment.safeNavigate(
        directions: NavDirections,
        extras: FragmentNavigator.Extras
) {
    val navController = findNavController()
    val destination = navController.currentDestination as FragmentNavigator.Destination
    if (javaClass.name == destination.className) {
        navController.navigate(directions, extras)
    }
}

fun Fragment.safeNavigate(directions: NavDirections) {
    val navController = findNavController()
    val destination = navController.currentDestination as FragmentNavigator.Destination
    if (javaClass.name == destination.className) {
        navController.navigate(directions)
    }
}