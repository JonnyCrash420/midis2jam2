/*
 * Copyright (C) 2022 Jacob Wysko
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see https://www.gnu.org/licenses/.
 */

package org.wysko.midis2jam2.starter

import com.jme3.app.SimpleApplication
import com.jme3.system.AppSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Default
import org.wysko.midis2jam2.DesktopMidis2jam2
import org.wysko.midis2jam2.gui.ExceptionPanel
import org.wysko.midis2jam2.gui.getGraphicsSettings
import org.wysko.midis2jam2.gui.loadSettingsFromFile
import org.wysko.midis2jam2.midi.DesktopMidiFile
import org.wysko.midis2jam2.util.logger
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.io.File
import java.io.IOException
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger
import java.util.regex.Pattern
import javax.imageio.ImageIO
import javax.sound.midi.InvalidMidiDataException
import javax.sound.midi.MidiSystem
import javax.sound.midi.MidiUnavailableException
import javax.sound.midi.Sequencer
import javax.swing.JOptionPane

/** The sequencer that is connected to the synthesizer. */
lateinit var connectedSequencer: Sequencer

/** Loads [connectedSequencer]. */
var loadSequencerJob: Job = CoroutineScope(Default).launch(start = CoroutineStart.LAZY) {
    try {
        connectedSequencer = MidiSystem.getSequencer(true)
        Execution.logger().info("Loaded internal MIDI sequencer")
    } catch (e: MidiUnavailableException) {
        err(
            e,
            "The MIDI sequencer is not available due to resource restrictions, or no sequencer is installed in the system.",
            "Error loading MIDI sequencer"
        )
    }
}

private val DEFAULT_CONFIGURATION = Properties().apply {
    setProperty("graphics_samples", "4")
    setProperty("latency_fix", "0")
}

/** Starts midis2jam2 with given settings. */
object Execution {

    /**
     * Begins midis2jam2 with given settings.
     *
     * @param properties settings to use
     * @param onStart function to call when midis2jam2 is started
     * @param onFinish function to call when midis2jam2 is finished
     */
    fun start(
        properties: Properties,
        onStart: () -> Unit,
        onReady: () -> Unit,
        onFinish: () -> Unit,
    ) {
        logger().debug("execution start")
        System.gc()

        logger().debug("config started (used to be coroutine)")
        @Suppress("NAME_SHADOWING") val properties = Properties().apply {
            this.putAll(DEFAULT_CONFIGURATION)
            this.putAll(properties)
            this.putAll(loadSettingsFromFile())
        }
        logger().debug("calling onstart")
        onStart() // Disable launcher

        /* Get MIDI file */

        logger().debug("parsing sequence")
        val sequence = try {
            logger().debug("dispatched sequence")
            MidiSystem.getSequence(File(properties.getProperty("midi_file")))
        } catch (e: InvalidMidiDataException) {
            err(e, "The MIDI file has bad data.", "Error reading MIDI file", onFinish)
            return
        } catch (e: IOException) {
            err(e, "The MIDI file could not be loaded.", "Error reading MIDI file", onFinish)
            return
        }

        /* Get MIDI device */
        logger().debug("getting midi device")
        val midiDevice = try {
            MidiSystem.getMidiDevice(
                MidiSystem.getMidiDeviceInfo().first { it.name == properties.getProperty("midi_device") }
            )
        } catch (e: MidiUnavailableException) {
            err(
                e,
                "The \"${properties.getProperty("midi_device")}\" MIDI device is unavailable. Are any other applications using it?",
                "Error opening MIDI device",
                onFinish
            )
            return
        } catch (e: IllegalArgumentException) {
            err(
                e,
                "The \"${properties.getProperty("midi_device")}\" MIDI device doesn't currently exist. Did you unplug it?",
                "Error opening MIDI device",
                onFinish
            )
            return
        }

        /* Get sequencer */
        logger().debug("getting sequencer")
        val sequencer = if (properties.getProperty("midi_device") == "Gervill") {
            /* Get internal synth */
            val synthesizer = try {
                logger().debug("getting synthesizer")
                MidiSystem.getSynthesizer()
            } catch (e: MidiUnavailableException) {
                err(
                    e,
                    "The internal synthesizer is not available due to resource restrictions, or no synthesizer is installed in the system.",
                    "Error opening MIDI synthesizer",
                    onFinish
                )
                return
            }

            /* Open synthesizer */
            try {
                logger().debug("opening synthesizer")
                synthesizer.open()
            } catch (e: MidiUnavailableException) {
                err(
                    e,
                    "The MIDI device cannot be opened due to resource restrictions.",
                    "Error opening MIDI synthesizer",
                    onFinish
                )
                return
            } catch (e: SecurityException) {
                err(
                    e,
                    "The MIDI device cannot be opened due to security restrictions.",
                    "Error opening MIDI synthesizer",
                    onFinish
                )
                return
            }

            /* Get SoundFont */
            properties.getProperty("soundfont")?.let { sf2 ->
                logger().debug("gettting sf2")
                getUnconnectedSequencer().also {
                    try {
                        logger().debug("connecting receiver")
                        it.transmitter.receiver = synthesizer.receiver
                        synthesizer.loadAllInstruments(MidiSystem.getSoundbank(File(sf2)))
                    } catch (e: InvalidMidiDataException) {
                        err(e, "The SoundFont file is bad.", "Error loading SoundFont", onFinish)
                        return
                    } catch (e: IOException) {
                        err(e, "Could not load the SoundFont.", "Error loading SoundFont", onFinish)
                        return
                    } catch (e: IllegalArgumentException) {
                        err(e, "midis2jam2 does not support this soundbank.", "Error loading SoundFont", onFinish)
                        return
                    }
                }
            } ?: let {
                logger().debug("no sf2")
                try {
                    connectedSequencer = MidiSystem.getSequencer(true)
                    Execution.logger().info("Loaded internal MIDI sequencer")
                } catch (e: MidiUnavailableException) {
                    err(
                        e,
                        "The MIDI sequencer is not available due to resource restrictions, or no sequencer is installed in the system.",
                        "Error loading MIDI sequencer"
                    )
                }
                connectedSequencer
            }
        } else {
            try {
                logger().debug("opening midi device")
                midiDevice.open()
            } catch (e: MidiUnavailableException) {
                err(
                    e,
                    "The MIDI device cannot be opened due to resource restrictions.",
                    "Error opening MIDI synthesizer",
                    onFinish
                )
                return
            } catch (e: SecurityException) {
                err(
                    e,
                    "The MIDI device cannot be opened due to security restrictions.",
                    "Error opening MIDI synthesizer",
                    onFinish
                )
                return
            }
            logger().debug("getting sequencer 2")
            MidiSystem.getSequencer(false).also {
                it.transmitter.receiver = midiDevice.receiver
            }
        }.also {
            try {
                logger().debug("opening sequencer")
                it.open()
            } catch (e: MidiUnavailableException) {

                err(
                    e,
                    "The MIDI device cannot be opened due to resource restrictions.",
                    "Error opening MIDI synthesizer",
                    onFinish
                )
                return
            } catch (e: SecurityException) {
                err(
                    e,
                    "The MIDI device cannot be opened due to security restrictions.",
                    "Error opening MIDI synthesizer",
                    onFinish
                )
                return
            }
            it.sequence = sequence
        }

        logger().debug("jme3 log setting")
        /* Hush JME */
        Logger.getLogger("com.jme3").level = Level.FINEST

        logger().debug("calling onReady")
        onReady()

        logger().debug("applying graphics")
        /* Apply graphics configuration */
        with(getGraphicsSettings()) {
            stringPropertyNames().forEach {
                properties.setProperty(it, this.getProperty(it))
            }
        }

        logger().debug("ready to start execution thread!")

        Thread({
            M2J2Execution(properties, {
                onFinish.invoke()
                midiDevice?.close()
            }, sequencer).execute()
        }, "midis2jam2 starter").start()
    }

    private fun getUnconnectedSequencer() = MidiSystem.getSequencer(false)
}

/** Handles an error. */
fun err(exception: Exception, message: String, title: String, onFinish: () -> Unit = {}) {
    Execution.logger().error(message, exception)
    JOptionPane.showMessageDialog(null, ExceptionPanel(message, exception), title, JOptionPane.ERROR_MESSAGE)
    onFinish.invoke()
}

/* EXECUTORS */

private val defaultSettings = AppSettings(true).apply {
    frameRate = -1
    frequency = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.displayModes.first().refreshRate
    isVSync = true
    isResizable = false
    isGammaCorrection = false
    icons = arrayOf("/ico/icon16.png", "/ico/icon32.png", "/ico/icon128.png", "/ico/icon256.png").map {
        ImageIO.read(this::class.java.getResource(it))
    }.toTypedArray()
    title = "midis2jam2"
    audioRenderer = null
    centerWindow = true
}

private class M2J2Execution(
    val properties: Properties,
    val onFinish: () -> Unit,
    val sequencer: Sequencer,
) : SimpleApplication() {

    fun execute() {
        logger().debug("execution execute")
        val resolution = collectWindowResolution(properties)
        if (properties.getProperty("fullscreen") == "true") { // Set resolution to monitor resolution
            defaultSettings.isFullscreen = true
            defaultSettings.setResolution(screenWidth(), screenHeight())
        } else {
            defaultSettings.isFullscreen = false
            with(resolution) {
                defaultSettings.setResolution(width, height)
            }
        }

        setSettings(defaultSettings)
        setDisplayStatView(false)
        setDisplayFps(false)
        isPauseOnLostFocus = false
        isShowSettings = false
        /* Calculate center */
//        Display.setLocation(
//            ((screenWidth() - resolution.width) / 2) - 7, // This -7 seems really hacky, but it makes it more centered
//            (screenHeight() - resolution.height) / 2 - 30 // Bias to move it up some
//        )
        logger().debug("application starting")
        start()
    }

    override fun stop() {
        stop(false)
        onFinish()
    }

    override fun simpleInitApp() {
        logger().debug("initializing app")
        val midiFile = DesktopMidiFile(File(properties.getProperty("midi_file")))
        DesktopMidis2jam2(
            sequencer = sequencer,
            midiFile,
            properties = properties,
            onFinish
        ).also {
            stateManager.attach(it)
            rootNode.attachChild(it.rootNode)
        }
    }
}

/** Determines the width of the screen. */
fun screenWidth(): Int = Toolkit.getDefaultToolkit().screenSize.width

/** Determines the height of the screen. */
fun screenHeight(): Int = Toolkit.getDefaultToolkit().screenSize.height

/** Obtains the preferred resolution from the config file. */
private fun collectWindowResolution(properties: Properties): Dimension {
    val resRegex = Pattern.compile("""(\d+)\s*x\s*(\d+)""")
    fun defaultDimension() = Dimension(((screenWidth() * 0.95).toInt()), (screenHeight() * 0.85).toInt())

    if (properties.getProperty("resolution") == null) return defaultDimension()
    if (properties.getProperty("resolution").equals("default", ignoreCase = true)) return defaultDimension()
    if (!resRegex.matcher(properties.getProperty("resolution")).matches()) return defaultDimension()

    return with(resRegex.matcher(properties.getProperty("resolution")).also { it.find() }) {
        Dimension(group(1).toInt(), group(2).toInt())
    }
}
