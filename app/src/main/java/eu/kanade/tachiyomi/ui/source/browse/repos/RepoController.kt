package eu.kanade.tachiyomi.ui.source.browse.repos

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.core.net.toUri
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.CategoriesControllerBinding
import eu.kanade.tachiyomi.ui.base.SmallToolbarInterface
import eu.kanade.tachiyomi.ui.base.controller.BaseController
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.isOnline
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.liftAppbarWith
import eu.kanade.tachiyomi.util.view.snack

/**
 * Controller to manage the repos for the user's extensions.
 */
class RepoController(
    bundle: Bundle? = null,
) : BaseController<CategoriesControllerBinding>(bundle),
    FlexibleAdapter.OnItemClickListener,
    SmallToolbarInterface,
    RepoAdapter.RepoItemListener {
    constructor(repoUrl: String) : this(
        Bundle().apply {
            putString(REPO_URL, repoUrl)
        },
    ) {
        presenter.createRepo(repoUrl)
    }

    /**
     * Adapter containing repo items.
     */
    private var adapter: RepoAdapter? = null

    /**
     * Undo helper used for restoring a deleted repo.
     */
    private var snack: Snackbar? = null

    /**
     * Creates the presenter for this controller. Not to be manually called.
     */
    private val presenter = RepoPresenter(this)

    /**
     * Returns the toolbar title to show when this controller is attached.
     */
    override fun getTitle(): String? = resources?.getString(R.string.extension_repos)

    override fun createBinding(inflater: LayoutInflater) = CategoriesControllerBinding.inflate(inflater)

    /**
     * Called after view inflation. Used to initialize the view.
     *
     * @param view The view of this controller.
     */
    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        liftAppbarWith(binding.recycler, true)

        adapter = RepoAdapter(this@RepoController)
        binding.recycler.layoutManager = LinearLayoutManager(view.context)
        binding.recycler.setHasFixedSize(true)
        binding.recycler.adapter = adapter
        adapter?.isPermanentDelete = false

        presenter.getRepos()
    }

    /**
     * Called when the view is being destroyed. Used to release references and remove callbacks.
     *
     * @param view The view of this controller.
     */
    override fun onDestroyView(view: View) {
        // Manually call callback to delete repos if required
        snack?.dismiss()
        view.clearFocus()
        confirmDelete()
        snack = null
        adapter = null
        super.onDestroyView(view)
    }

    override fun handleBack(): Boolean {
        view?.clearFocus()
        confirmDelete()
        return super.handleBack()
    }

    /**
     * Called from the presenter when the repos are updated.
     *
     */
    fun updateRepos() {
        adapter?.updateDataSet(presenter.getReposWithCreate())
        adapter?.addItem(0, InfoRepoMessage())
    }

    /**
     * Called when an item in the list is clicked.
     *
     * @param position The position of the clicked item.
     * @return true if this click should enable selection mode.
     */
    override fun onItemClick(
        view: View?,
        position: Int,
    ): Boolean {
        adapter?.resetEditing(position)
        return true
    }

    override fun onLogoClick(position: Int) {
        val repo = (adapter?.getItem(position) as? RepoItem)?.repo ?: return
        val repoUrl = presenter.getRepoUrl(repo)
        if (isNotOnline()) return

        if (repoUrl.isBlank()) {
            activity?.toast(R.string.url_not_set_click_again)
        } else {
            activity?.openInBrowser(repoUrl.toUri())
        }
    }

    private fun isNotOnline(showSnackbar: Boolean = true): Boolean {
        if (activity == null || !activity!!.isOnline()) {
            if (showSnackbar) view?.snack(R.string.no_network_connection)
            return true
        }
        return false
    }

    override fun onRepoRename(
        position: Int,
        newName: String,
    ): Boolean {
        val repo = (adapter?.getItem(position) as? RepoItem)?.repo ?: return false
        if (newName.isBlank()) {
            activity?.toast(R.string.repo_cannot_be_blank)
            return false
        }
        if (repo == RepoPresenter.CREATE_REPO_ITEM) {
            return (presenter.createRepo(newName))
        }
        return (presenter.renameRepo(repo, newName))
    }

    override fun onItemDelete(position: Int) {
        activity!!
            .materialAlertDialog()
            .setTitle(R.string.confirm_repo_deletion)
            .setMessage(
                activity!!.getString(
                    R.string.delete_repo_confirmation,
                    (adapter!!.getItem(position) as RepoItem).repo,
                ),
            ).setPositiveButton(R.string.delete) { _, _ ->
                deleteRepo(position)
            }.setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun deleteRepo(position: Int) {
        adapter?.removeItem(position)
        snack =
            view?.snack(R.string.snack_repo_deleted, Snackbar.LENGTH_INDEFINITE) {
                var undoing = false
                setAction(R.string.undo) {
                    adapter?.restoreDeletedItems()
                    undoing = true
                }
                addCallback(
                    object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                        override fun onDismissed(
                            transientBottomBar: Snackbar?,
                            event: Int,
                        ) {
                            super.onDismissed(transientBottomBar, event)
                            if (!undoing) confirmDelete()
                        }
                    },
                )
            }
        (activity as? MainActivity)?.setUndoSnackBar(snack)
    }

    fun confirmDelete() {
        val adapter = adapter ?: return
        presenter.deleteRepo(adapter.deletedItems.map { (it as RepoItem).repo }.firstOrNull())
        adapter.confirmDeletion()
        snack = null
    }

    /**
     * Called from the presenter when a repo already exists.
     */
    fun onRepoExistsError() {
        activity?.toast(R.string.error_repo_exists)
    }

    /**
     * Called from the presenter when a invalid repo is made
     */
    fun onRepoInvalidNameError() {
        activity?.toast(R.string.invalid_repo_name)
    }

    companion object {
        const val REPO_URL = "repo_url"
    }
}
