package app.nanogone

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nanogone.editor.EditorScreen
import app.nanogone.editor.EditorViewModel
import app.nanogone.home.HomeScreen
import app.nanogone.ui.DawnBackground
import app.nanogone.ui.DawnTheme

class MainActivity : ComponentActivity() {

    private val vm: EditorViewModel by viewModels()

    private val picker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.open(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) photoFrom(intent)?.let(vm::open)
        setContent {
            DawnTheme {
                val ui by vm.ui.collectAsStateWithLifecycle()
                DawnBackground {
                    if (ui.photo == null) {
                        HomeScreen(ui.busy, ui.message, ui.brains, ui.deepStatus, ui.deepCanAdd, onAddDeep = vm::addDeepBrain) {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    } else {
                        BackHandler { vm.close() }
                        EditorScreen(ui, vm, onBack = vm::close)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        photoFrom(intent)?.let(vm::open)
    }

    private fun photoFrom(i: Intent?): Uri? = when (i?.action) {
        Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(i, Intent.EXTRA_STREAM, Uri::class.java)
        Intent.ACTION_EDIT, Intent.ACTION_VIEW -> i.data
        else -> null
    }
}
