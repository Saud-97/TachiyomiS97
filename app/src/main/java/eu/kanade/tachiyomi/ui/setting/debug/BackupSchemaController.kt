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
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.databinding.SubDebugControllerBinding
import eu.kanade.tachiyomi.ui.base.controller.BaseController
import eu.kanade.tachiyomi.util.view.copyToClipboard
import eu.kanade.tachiyomi.util.view.scrollViewWith
import kotlinx.serialization.protobuf.schema.ProtoBufSchemaGenerator

class BackupSchemaController : BaseController<SubDebugControllerBinding>() {
    companion object {
        const val title = "Backup file schema"
    }

    private val itemAdapter = ItemAdapter<DebugInfoItem>()
    private val fastAdapter = FastAdapter.with(itemAdapter)
    private val schema = ProtoBufSchemaGenerator.generateSchemaText(Backup.serializer().descriptor)

    override fun getTitle() = title

    override fun createBinding(inflater: LayoutInflater) = SubDebugControllerBinding.inflate(inflater)

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        scrollViewWith(binding.recycler, padBottom = true)
        fastAdapter.setHasStableIds(true)
        binding.recycler.layoutManager = LinearLayoutManager(view.context)
        binding.recycler.adapter = fastAdapter
        itemAdapter.add(DebugInfoItem(schema, false))
    }

    override fun onCreateOptionsMenu(
        menu: Menu,
        inflater: MenuInflater,
    ) {
        inflater.inflate(R.menu.sub_debug_info, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_copy -> copyToClipboard(schema, "Backup file schema", true)
        }
        return super.onOptionsItemSelected(item)
    }
}
