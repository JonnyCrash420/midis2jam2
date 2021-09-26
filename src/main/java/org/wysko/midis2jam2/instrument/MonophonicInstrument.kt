/*
 * Copyright (C) 2021 Jacob Wysko
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
package org.wysko.midis2jam2.instrument

import com.jme3.scene.Node
import org.wysko.midis2jam2.Midis2jam2
import org.wysko.midis2jam2.instrument.algorithmic.FingeringManager
import org.wysko.midis2jam2.instrument.clone.Clone
import org.wysko.midis2jam2.midi.MidiChannelSpecificEvent
import java.lang.reflect.Constructor

/**
 * A monophonic instrument is any instrument that can only play one note at a time (e.g., saxophones, clarinets,
 * ocarinas, etc.). Because this limitation is lifted in MIDI files, midis2jam2 needs to visualize polyphony by spawning
 * "clones" of an instrument. These clones will only appear when necessary.
 *
 * It happens to be that every monophonic instrument is also a [SustainedInstrument].
 *
 * @see Clone
 */
abstract class MonophonicInstrument protected constructor(
	context: Midis2jam2,
	eventList: List<MidiChannelSpecificEvent>,
	cloneClass: Class<out Clone>,
	val manager: FingeringManager<*>?
) : SustainedInstrument(context, eventList) {

	/**
	 * Node contains all clones.
	 */
	@JvmField
	val groupOfPolyphony = Node()

	/**
	 * The list of clones this monophonic instrument needs to effectively display all notes.
	 */
	val clones: List<Clone>

	/**
	 * Since MIDI channels that play monophonic instruments can play with polyphony, we need to calculate the number of
	 * "clones" needed to visualize this and determine which note events shall be assigned to which clones, using the
	 * least number of clones.
	 *
	 * @param instrument the monophonic instrument that is handling the clones
	 * @param cloneClass the class of the [Clone] to instantiate
	 * @throws ReflectiveOperationException is usually thrown if an error occurs in the clone constructor
	 */
	@Throws(ReflectiveOperationException::class)
	protected fun calculateClones(
		instrument: MonophonicInstrument,
		cloneClass: Class<out Clone?>
	): List<Clone> {
		val calcClones: MutableList<Clone> = ArrayList()
		val constructor: Constructor<*> = cloneClass.getDeclaredConstructor(instrument.javaClass)
		calcClones.add(constructor.newInstance(instrument) as Clone)
		for (i in notePeriods.indices) {
			for (j in notePeriods.indices) {
				if (j == i && i != notePeriods.size - 1) continue
				val comp1 = notePeriods[i]
				val comp2 = notePeriods[j]
				if (comp1.startTick() > comp2.endTick()) continue
				if (comp1.endTick() < comp2.startTick()) {
					calcClones[0].notePeriods.add(comp1)
					break
				}
				/* Check if notes are overlapping */if (comp1.startTick() >= comp2.startTick() && comp1.startTick() <= comp2.endTick()) {
					var added = false
					for (clone in calcClones) {
						if (!clone.isPlaying(comp1.startTick() + context.file.division / 4)) {
							clone.notePeriods.add(comp1)
							added = true
							break
						}
					}
					if (!added) {
						val e = constructor.newInstance(instrument) as Clone
						e.notePeriods.add(comp1)
						calcClones.add(e)
					}
				} else {
					calcClones[0].notePeriods.add(comp1)
				}
				break
			}
		}
		return calcClones
	}

	override fun tick(time: Double, delta: Float) {
		super.tick(time, delta)

		/* Tick clones */
		clones.forEach { it.tick(time, delta) }
	}

	init {
		clones = calculateClones(this, cloneClass)
		clones.forEach { groupOfPolyphony.attachChild(it.offsetNode) }
		instrumentNode.attachChild(groupOfPolyphony)
	}
}