package eu.kanade.tachiyomi.ui.setting.debug

import android.app.Application
import androidx.lifecycle.asFlow
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import eu.kanade.tachiyomi.ui.base.presenter.BaseCoroutinePresenter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class WorkerInfoPresenter : BaseCoroutinePresenter<WorkerInfoController>() {
    private val workManager by lazy { WorkManager.getInstance(Injekt.get<Application>()) }

    val finished by lazy {
        workManager
            .getWorkInfosLiveData(
                WorkQuery.fromStates(
                    WorkInfo.State.SUCCEEDED,
                    WorkInfo.State.FAILED,
                    WorkInfo.State.CANCELLED,
                ),
            ).asFlow()
            .map(::constructString)
            .stateIn(presenterScope, SharingStarted.WhileSubscribed(), "")
    }

    val running by lazy {
        workManager
            .getWorkInfosLiveData(WorkQuery.fromStates(WorkInfo.State.RUNNING))
            .asFlow()
            .map(::constructString)
            .stateIn(presenterScope, SharingStarted.WhileSubscribed(), "")
    }

    val enqueued by lazy {
        workManager
            .getWorkInfosLiveData(WorkQuery.fromStates(WorkInfo.State.ENQUEUED))
            .asFlow()
            .map(::constructString)
            .stateIn(presenterScope, SharingStarted.WhileSubscribed(), "")
    }

    private fun constructString(list: List<WorkInfo>) =
        buildString {
            if (list.isEmpty()) {
                appendLine("-")
            } else {
                val newList = list.toList()
                newList.forEach { workInfo ->
                    appendLine("Id: ${workInfo.id}")
                    appendLine("Tags:")
                    workInfo.tags.forEach {
                        appendLine(" - $it")
                    }
                    appendLine("State: ${workInfo.state}")
                    appendLine()
                }
            }
        }
}
