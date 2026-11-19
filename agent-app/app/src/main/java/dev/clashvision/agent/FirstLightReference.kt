package dev.clashvision.agent

import android.content.Context
import com.firstlight.offline.OnDeviceOrtPolicy
import com.firstlight.offline.PolicyModelCatalog
import org.json.JSONObject
import java.io.DataInputStream

/** Run the same observation as the desktop reference, including the recurrent inputs. */
internal object FirstLightReference {
    fun check(context: Context, id: String): Long {
        val tensors = DataInputStream(context.assets.open("firstlight/reference.bin").buffered()).use { stream ->
            Array(OnDeviceOrtPolicy.INPUT_COUNT) {
                val size = stream.readInt()
                require(size in 1..1_000_000)
                ByteArray(size).also { stream.readFully(it) }
            }.also { require(stream.read() == -1) }
        }
        val expected = context.assets.open("firstlight/reference.json").bufferedReader().use {
            JSONObject(it.readText()).getJSONArray(id)
        }
        OnDeviceOrtPolicy(context, PolicyModelCatalog.forId(id)).use { policy ->
            val result = policy.submitStep(tensors).get()
            val actual = listOf(longArrayOf(result.gate), longArrayOf(result.microActionCount),
                result.candidateIndex, result.candidateUid, result.targetCell, result.delayOffsetBin)
            actual.forEachIndexed { index, values ->
                val reference = expected.getJSONArray(index)
                check(values.size == reference.length() && values.indices.all { values[it] == reference.getLong(it) }) {
                    "Izlaz modela $index razlikuje se od referentnog rezultata."
                }
            }
            return result.forwardNanos / 1_000_000
        }
    }
}
