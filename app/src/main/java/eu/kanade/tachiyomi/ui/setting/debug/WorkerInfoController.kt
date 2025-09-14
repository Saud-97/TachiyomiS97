package eu.kanade.tachiyomi.ui.setting.debug

import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.SubDebugControllerBinding
import eu.kanade.tachiyomi.ui.base.controller.BaseCoroutineController
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.view.copyToClipboard
import eu.kanade.tachiyomi.util.view.scrollViewWith
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.merge

class WorkerInfoController : BaseCoroutineController<SubDebugControllerBinding, WorkerInfoPresenter>() {
    companion object {
        const val title = "Worker info"
    }

    override var presenter = WorkerInfoPresenter()

    private val itemAdapter = ItemAdapter<DebugInfoItem>()
    private val fastAdapter = FastAdapter.with(itemAdapter)

    override fun getTitle() = title

    override fun createBinding(inflater: LayoutInflater) = SubDebugControllerBinding.inflate(inflater)

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        scrollViewWith(binding.recycler, padBottom = true)

        fastAdapter.setHasStableIds(true)
        binding.recycler.layoutManager = LinearLayoutManager(view.context)
        binding.recycler.adapter = fastAdapter
        binding.recycler.itemAnimator = null
        viewScope.launchUI {
            merge(presenter.enqueued, presenter.finished, presenter.running).collectLatest {
                itemAdapter.clear()
                itemAdapter.add(DebugInfoItem("Enqueued", true))
                itemAdapter.add(DebugInfoItem(presenter.enqueued.value, false))
                itemAdapter.add(DebugInfoItem("Finished", true))
                itemAdapter.add(DebugInfoItem(presenter.finished.value, false))
                itemAdapter.add(DebugInfoItem("Running", true))
                itemAdapter.add(DebugInfoItem(presenter.running.value, false))
            }
        }
    }

    override fun onCreateOptionsMenu(
        menu: Menu,
        inflater: MenuInflater,
    ) {
        inflater.inflate(R.menu.sub_debug_info, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_copy ->
                copyToClipboard(
                    "${presenter.enqueued.value}\n${presenter.finished.value}\n${presenter.running.value}",
                    "Backup file schema",
                    true,
                )
        }
        return super.onOptionsItemSelected(item)
    }
}
