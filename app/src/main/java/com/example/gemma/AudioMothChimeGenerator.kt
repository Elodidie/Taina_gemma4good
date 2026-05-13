package com.example.gemma

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/**
 * Kotlin port of the AudioMothChime Python script.
 * Generates an 18 kHz data carrier + musical melody WAV that an AudioMoth
 * can decode to set its clock, GPS coordinates, and deployment ID.
 *
 * Protocol: https://github.com/OpenAcousticDevices/AudioMothChime
 */
object AudioMothChimeGenerator {

    // ─── Constants ────────────────────────────────────────────────────────────

    private const val SAMPLE_RATE = 48000
    private const val CARRIER_FREQUENCY = 18000

    private const val BITS_PER_BYTE = 8
    private const val BITS_IN_INT16 = 16
    private const val BITS_IN_INT32 = 32
    private const val BITS_IN_LAT_LNG = 28

    private const val LATITUDE_PRECISION = 1_000_000.0
    private const val LONGITUDE_PRECISION = 500_000.0

    private const val LENGTH_OF_TIME = 6
    private const val LENGTH_OF_LOCATION = 7
    private const val LENGTH_OF_DEPLOYMENT_ID = 8

    private const val NUMBER_OF_START_BITS = 16
    private const val NUMBER_OF_STOP_BITS = 8

    private const val BIT_RISE = 0.0005
    private const val BIT_FALL = 0.0005
    private const val LOW_BIT_SUSTAIN = 0.004
    private const val HIGH_BIT_SUSTAIN = 0.009
    private const val START_STOP_BIT_SUSTAIN = 0.0065

    private const val NOTE_RISE_DURATION = 0.030
    private const val NOTE_FALL_DURATION = 0.030
    private const val NOTE_LONG_FALL_DURATION = 0.090

    private val HAMMING_CODE = arrayOf(
        intArrayOf(0,0,0,0,0,0,0), intArrayOf(1,1,1,0,0,0,0),
        intArrayOf(1,0,0,1,1,0,0), intArrayOf(0,1,1,1,1,0,0),
        intArrayOf(0,1,0,1,0,1,0), intArrayOf(1,0,1,1,0,1,0),
        intArrayOf(1,1,0,0,1,1,0), intArrayOf(0,0,1,0,1,1,0),
        intArrayOf(1,1,0,1,0,0,1), intArrayOf(0,0,1,1,0,0,1),
        intArrayOf(0,1,0,0,1,0,1), intArrayOf(1,0,1,0,1,0,1),
        intArrayOf(1,0,0,0,0,1,1), intArrayOf(0,1,1,0,0,1,1),
        intArrayOf(0,0,0,1,1,1,1), intArrayOf(1,1,1,1,1,1,1),
    )

    private val NOTE_FREQ = mapOf(
        "C5" to 523, "C#5" to 554, "Db5" to 554, "D5" to 587,
        "D#5" to 622, "Eb5" to 622, "E5" to 659, "F5" to 698,
        "F#5" to 740, "Gb5" to 740, "G5" to 784,
    )

    private val MELODY = listOf(
        "Eb5" to 1, "G5" to 1, "D5" to 1, "F#5" to 1,
        "Db5" to 1, "F5" to 1, "C5" to 1, "E5" to 5,
        "Db5" to 1, "F5" to 1, "C5" to 1, "E5" to 4,
    )

    // ─── BitPacker ────────────────────────────────────────────────────────────

    private class BitPacker(size: Int) {
        val bytes = IntArray(size)
        var index = 0

        fun setBit(value: Boolean) {
            if (value) bytes[index / BITS_PER_BYTE] =
                bytes[index / BITS_PER_BYTE] or (1 shl (index % BITS_PER_BYTE))
            index++
        }

        fun setBits(value: Long, length: Int) {
            for (i in 0 until length) setBit((value and (1L shl i)) != 0L)
        }

        fun encodeTime(timestampUnix: Long) {
            setBits(timestampUnix and 0xFFFFFFFFL, BITS_IN_INT32)
            setBits(0L, BITS_IN_INT16) // UTC timezone
        }

        fun encodeLocation(latitude: Double, longitude: Double) {
            val mask = (1L shl BITS_IN_LAT_LNG) - 1L
            val intLat = (latitude.coerceIn(-90.0, 90.0) * LATITUDE_PRECISION).roundToLong()
            val intLng = (longitude.coerceIn(-180.0, 180.0) * LONGITUDE_PRECISION).roundToLong()
            setBits(intLat and mask, BITS_IN_LAT_LNG)
            setBits(intLng and mask, BITS_IN_LAT_LNG)
        }

        // Stores deployment bytes in reverse order, matching the AudioMoth protocol.
        fun encodeDeploymentId(deploymentBytes: IntArray) {
            for (i in 0 until LENGTH_OF_DEPLOYMENT_ID) {
                bytes[index / BITS_PER_BYTE] =
                    deploymentBytes[LENGTH_OF_DEPLOYMENT_ID - 1 - i] and 0xFF
                index += BITS_PER_BYTE
            }
        }
    }

    // ─── CRC16 ────────────────────────────────────────────────────────────────

    private const val CRC_POLY = 0x1021

    private fun updateCrc16(crc: Int, incr: Int): Int {
        val xor = (crc shr 15) and 1
        var out = (crc shl 1) and 0xFFFF
        if (incr > 0) out += 1
        if (xor > 0) out = out xor CRC_POLY
        return out
    }

    private fun createCrc16(dataBytes: IntArray): IntArray {
        var crc = 0
        for (b in dataBytes) {
            for (x in 7 downTo 0) crc = updateCrc16(crc, b and (1 shl x))
        }
        repeat(16) { crc = updateCrc16(crc, 0) }
        return intArrayOf(crc and 0xFF, (crc shr 8) and 0xFF)
    }

    // ─── Hamming(7,4) encode ──────────────────────────────────────────────────

    private fun hammingEncode(dataBytes: IntArray): IntArray {
        val bits = mutableListOf<Int>()
        for (b in dataBytes) {
            val low = b and 0x0F
            val high = (b and 0xF0) shr 4
            for (x in 0 until 7) {
                bits.add(HAMMING_CODE[low][x])
                bits.add(HAMMING_CODE[high][x])
            }
        }
        return bits.toIntArray()
    }

    // ─── Waveform generation ──────────────────────────────────────────────────

    private class OscState(var amp: Double = 0.0, var x: Double = 1.0, var y: Double = 0.0)

    private fun addWaveformComponent(
        samples: MutableList<Float>,
        freq: Int,
        phase: Double,
        rise: Double,
        sustain: Double,
        fall: Double,
        state: OscState,
    ) {
        val nRise    = (rise    * SAMPLE_RATE).roundToInt()
        val nSustain = (sustain * SAMPLE_RATE).roundToInt()
        val nFall    = (fall    * SAMPLE_RATE).roundToInt()
        val total    = nRise + nSustain + nFall
        val theta    = 2.0 * PI * freq / SAMPLE_RATE

        for (k in 0 until total) {
            if (k < nRise)
                state.amp = min(PI / 2, state.amp + (PI / 2) / nRise)
            if (k >= nRise + nSustain)
                state.amp = max(0.0, state.amp - (PI / 2) / nFall)

            val s = sin(state.amp)
            val volume = s * s
            samples.add((volume * phase * state.x).toFloat())

            val xNew = state.x * cos(theta) - state.y * sin(theta)
            val yNew = state.x * sin(theta) + state.y * cos(theta)
            state.x = xNew
            state.y = yNew
        }
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Generate the full chime as a FloatArray of mono samples at 48 kHz.
     * [deploymentHex] must be exactly 16 lowercase hex characters.
     */
    fun generate(
        timestampUnix: Long,
        latitude: Double,
        longitude: Double,
        deploymentHex: String,
    ): FloatArray {
        require(deploymentHex.length == 16) { "deploymentHex must be 16 chars" }

        val depBytes = IntArray(LENGTH_OF_DEPLOYMENT_ID) { i ->
            deploymentHex.substring(i * 2, i * 2 + 2).toInt(16)
        }

        // Pack all fields into bytes
        val packerSize = LENGTH_OF_TIME + LENGTH_OF_LOCATION + LENGTH_OF_DEPLOYMENT_ID
        val packer = BitPacker(packerSize)
        packer.encodeTime(timestampUnix)
        packer.encodeLocation(latitude, longitude)
        packer.encodeDeploymentId(depBytes)

        val dataBytes = packer.bytes
        val crc = createCrc16(dataBytes)
        val allBytes = dataBytes + crc
        val bitSequence = hammingEncode(allBytes)

        // ── Carrier waveform (18 kHz data channel) ───────────────────────────
        val carrier = mutableListOf<Float>()
        val cs = OscState()
        var phase = 1.0

        repeat(NUMBER_OF_START_BITS) {
            addWaveformComponent(carrier, CARRIER_FREQUENCY, phase,
                BIT_RISE, START_STOP_BIT_SUSTAIN, BIT_FALL, cs)
            phase *= -1.0
        }
        for (bit in bitSequence) {
            val sustain = if (bit == 1) HIGH_BIT_SUSTAIN else LOW_BIT_SUSTAIN
            addWaveformComponent(carrier, CARRIER_FREQUENCY, phase,
                BIT_RISE, sustain, BIT_FALL, cs)
            phase *= -1.0
        }
        repeat(NUMBER_OF_STOP_BITS) {
            addWaveformComponent(carrier, CARRIER_FREQUENCY, phase,
                BIT_RISE, START_STOP_BIT_SUSTAIN, BIT_FALL, cs)
            phase *= -1.0
        }

        // ── Melody waveform ───────────────────────────────────────────────────
        val melody = mutableListOf<Float>()
        val ms = OscState()
        val sumDurations = MELODY.sumOf { it.second }
        val noteSustain = (carrier.size.toDouble() / SAMPLE_RATE
                - MELODY.size * (NOTE_RISE_DURATION + NOTE_FALL_DURATION)
                + NOTE_FALL_DURATION - NOTE_LONG_FALL_DURATION) / sumDurations

        for ((i, note) in MELODY.withIndex()) {
            val (name, dur) = note
            val fallDur = if (i == MELODY.size - 1) NOTE_LONG_FALL_DURATION else NOTE_FALL_DURATION
            addWaveformComponent(melody, NOTE_FREQ[name] ?: 523, 1.0,
                NOTE_RISE_DURATION, noteSustain * dur, fallDur, ms)
        }

        // ── Mix: carrier at 25 %, melody at 50 % ─────────────────────────────
        val len = min(carrier.size, melody.size)
        return FloatArray(len) { i -> carrier[i] / 4f + melody[i] / 2f }
    }

    /** Write a FloatArray of mono samples as a 16-bit PCM WAV file. */
    fun writeWav(file: File, samples: FloatArray) {
        val dataSize = samples.size * 2
        val buf = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray())
        buf.putInt(36 + dataSize)
        buf.put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray())
        buf.putInt(16)
        buf.putShort(1)                     // PCM
        buf.putShort(1)                     // mono
        buf.putInt(SAMPLE_RATE)
        buf.putInt(SAMPLE_RATE * 2)         // byte rate
        buf.putShort(2)                     // block align
        buf.putShort(16)                    // bits per sample
        buf.put("data".toByteArray())
        buf.putInt(dataSize)
        for (s in samples) buf.putShort((s.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
        file.writeBytes(buf.array())
    }
}
