package io.github.seyud.weave.arch

import android.content.Context
import androidx.activity.ComponentActivity

/**
 * Class for passing transient UI events from ViewModels to activities.
 * (see https://medium.com/google-developers/livedata-with-snackbar-navigation-and-other-events-the-singleliveevent-case-ac2622673150)
 */
abstract class UiEvent

interface ContextExecutor {
    operator fun invoke(context: Context)
}

interface ActivityExecutor {
    operator fun invoke(activity: ComponentActivity)
}
