package eu.kanade.tachiyomi.ui.source.browse.repos

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import eu.davidea.viewholders.FlexibleViewHolder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.CategoriesItemBinding
import eu.kanade.tachiyomi.util.system.getResourceColor

/**
 * Holder used to display repo items.
 *
 * @param view The view used by repo items.
 * @param adapter The adapter containing this holder.
 */
class RepoHolder(
    view: View,
    val adapter: RepoAdapter,
) : FlexibleViewHolder(view, adapter) {
    private val binding = CategoriesItemBinding.bind(view)

    init {
        binding.editButton.setOnClickListener {
            submitChanges()
        }
    }

    private var createRepo = false
    private var regularDrawable: Drawable? = null

    /**
     * Binds this holder with the given repo.
     *
     * @param repo The repo to bind.
     */
    fun bind(repo: String) {
        // Set capitalized title.
        binding.image.isVisible = false
        binding.editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitChanges()
            }
            true
        }
        createRepo = repo == RepoPresenter.CREATE_REPO_ITEM
        if (createRepo) {
            binding.title.text = itemView.context.getString(R.string.action_add_repo)
            binding.title.setTextColor(
                ContextCompat.getColor(itemView.context, R.color.material_on_background_disabled),
            )
            regularDrawable = ContextCompat.getDrawable(itemView.context, R.drawable.ic_add_24dp)
            binding.editButton.setImageDrawable(null)
            binding.editText.setText("")
            binding.editText.hint = ""
        } else {
            binding.title.text = repo
            binding.title.maxLines = 2
            binding.title.setTextColor(itemView.context.getResourceColor(R.attr.colorOnBackground))
            regularDrawable = ContextCompat.getDrawable(itemView.context, R.drawable.ic_github_24dp)
            binding.reorder.setOnClickListener {
                adapter.repoItemListener.onLogoClick(flexibleAdapterPosition)
            }
            binding.editText.setText(binding.title.text)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun isEditing(editing: Boolean) {
        itemView.isActivated = editing
        binding.title.isInvisible = editing
        binding.editText.isInvisible = !editing
        if (editing) {
            binding.editText.inputType = InputType.TYPE_TEXT_VARIATION_URI
            binding.editText.requestFocus()
            binding.editText.selectAll()
            binding.editButton.setImageDrawable(ContextCompat.getDrawable(itemView.context, R.drawable.ic_check_24dp))
            binding.editButton.drawable
                .mutate()
                .setTint(itemView.context.getResourceColor(R.attr.colorSecondary))
            showKeyboard()
            if (!createRepo) {
                binding.reorder.setImageDrawable(
                    ContextCompat.getDrawable(itemView.context, R.drawable.ic_delete_24dp),
                )
                binding.reorder.setOnClickListener {
                    adapter.repoItemListener.onItemDelete(flexibleAdapterPosition)
                    hideKeyboard()
                }
            }
        } else {
            if (!createRepo) {
                binding.reorder.setOnClickListener {
                    adapter.repoItemListener.onLogoClick(flexibleAdapterPosition)
                }
                binding.editButton.setImageDrawable(
                    ContextCompat.getDrawable(itemView.context, R.drawable.ic_edit_24dp),
                )
            } else {
                binding.editButton.setImageDrawable(null)
                binding.reorder.setOnTouchListener { _, _ -> true }
            }
            binding.editText.clearFocus()
            binding.editButton.drawable?.mutate()?.setTint(
                ContextCompat.getColor(itemView.context, R.color.gray_button),
            )
            binding.reorder.setImageDrawable(regularDrawable)
        }
    }

    private fun submitChanges() {
        if (binding.editText.isVisible) {
            if (adapter.repoItemListener.onRepoRename(flexibleAdapterPosition, binding.editText.text.toString())) {
                isEditing(false)
                if (!createRepo) {
                    binding.title.text = binding.editText.text.toString()
                }
            }
        } else {
            itemView.performClick()
        }
        hideKeyboard()
    }

    private fun showKeyboard() {
        val inputMethodManager = itemView.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.showSoftInput(binding.editText, WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
    }

    private fun hideKeyboard() {
        val inputMethodManager = itemView.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(binding.editText.windowToken, 0)
    }
}
