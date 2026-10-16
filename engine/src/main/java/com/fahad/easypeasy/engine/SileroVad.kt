package com.fahad.easypeasy.engine

import ai.onnxruntime.*
import java.nio.FloatBuffer
import java.nio.LongBuffer

/** Silero v5/v6 16kHz ONNX signature: input [1,576], state [2,1,128], sr scalar.
 * 512 fresh samples + 64-sample context. Must run on the audio IO dispatcher. */
class SileroVad(modelPath: String) : VoiceActivityDetector {
    private val environment = OrtEnvironment.getEnvironment()
    private val session = OrtSession.SessionOptions().use { options ->
        options.setIntraOpNumThreads(1)
        environment.createSession(modelPath, options)
    }
    private var state = FloatArray(256)
    private val context = FloatArray(64)
    override fun probability(samples: FloatArray): Float {
        require(samples.size == 512)
        val input = context + samples
        OnnxTensor.createTensor(environment, FloatBuffer.wrap(input), longArrayOf(1, 576)).use { audio ->
            OnnxTensor.createTensor(environment, FloatBuffer.wrap(state), longArrayOf(2, 1, 128)).use { hidden ->
                OnnxTensor.createTensor(environment, LongBuffer.wrap(longArrayOf(16000)), longArrayOf()).use { rate ->
                    session.run(mapOf("input" to audio, "state" to hidden, "sr" to rate)).use { result ->
                        val p = (result[0].value as Array<*>)[0] as FloatArray
                        val next = result[1].value as Array<*>
                        state = ((next[0] as Array<*>)[0] as FloatArray) + ((next[1] as Array<*>)[0] as FloatArray)
                        samples.copyInto(context, 0, 448, 512)
                        return p[0]
                    }
                }
            }
        }
    }
    override fun reset() { state.fill(0f); context.fill(0f) }
    override fun close() { session.close(); reset() }
}
