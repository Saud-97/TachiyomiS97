package eu.kanade.tachiyomi.ui.source.browse.repos

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.base.presenter.BaseCoroutinePresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Presenter of [RepoController]. Used to manage the repos for the extensions.
 */
class RepoPresenter(
    private val controller: RepoController,
    private val preferences: PreferencesHelper = Injekt.get(),
) : BaseCoroutinePresenter<RepoController>() {
    private var scope = CoroutineScope(Job() + Dispatchers.Default)

    /**
     * List containing repos.
     */
    private var repos: Set<String>
        get() =
            preferences
                .extensionRepos()
                .get()
                .map { "$it/index.min.json" }
                .sorted()
                .toSet()
        set(value) = preferences.extensionRepos().set(value.map { it.removeSuffix("/index.min.json") }.toSet())

    /**
     * Called when the presenter is created.
     */
    fun getRepos() {
        scope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                controller.updateRepos()
            }
        }
    }

    fun getReposWithCreate(): List<RepoItem> = (listOf(CREATE_REPO_ITEM) + repos).map(::RepoItem)

    fun getRepoUrl(repo: String): String =
        githubRepoRegex
            .find(repo)
            ?.let {
                val (user, repoName) = it.destructured
                "https://github.com/$user/$repoName"
            } ?: repo

    /**
     * Creates and adds a new repo to the database.
     *
     * @param name The name of the repo to create.
     */
    fun createRepo(name: String): Boolean {
        if (isInvalidRepo(name)) return false

        // Do not allow duplicate repos.
        if (repoExists(name)) {
            controller.onRepoExistsError()
            return true
        }

        repos += name
        controller.updateRepos()
        return true
    }

    /**
     * Deletes the repo from the database.
     *
     * @param repo The repo to delete.
     */
    fun deleteRepo(repo: String?) {
        val safeRepo = repo ?: return
        repos -= safeRepo
        controller.updateRepos()
    }

    /**
     * Renames a repo.
     *
     * @param repo The repo to rename.
     * @param name The new name of the repo.
     */
    fun renameRepo(
        repo: String,
        name: String,
    ): Boolean {
        if (!repo.equals(name, true)) {
            if (isInvalidRepo(name)) return false
            repos -= repo
            repos += name
            controller.updateRepos()
        }
        return true
    }

    private fun isInvalidRepo(name: String): Boolean {
        // Do not allow invalid formats
        if (!name.matches(repoRegex)) {
            controller.onRepoInvalidNameError()
            return true
        }
        return false
    }

    /**
     * Returns true if a repo with the given name already exists.
     */
    private fun repoExists(name: String): Boolean = repos.any { it.equals(name, true) }

    companion object {
        private val repoRegex = """^https://.*/index\.min\.json$""".toRegex()
        private val githubRepoRegex = """https://(?:raw.githubusercontent.com|github.com)/(.+?)/(.+?)/.+""".toRegex()
        const val CREATE_REPO_ITEM = "create_repo"
    }
}
