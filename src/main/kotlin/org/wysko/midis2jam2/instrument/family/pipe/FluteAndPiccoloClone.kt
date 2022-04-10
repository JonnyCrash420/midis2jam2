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
package org.wysko.midis2jam2.instrument.family.pipe

import com.jme3.scene.Spatial
import org.wysko.midis2jam2.particle.SteamPuffer.SteamPuffTexture

/** Contains shared code between the flute and piccolo. */
open class FluteAndPiccoloClone(parent: HandedInstrument, puffType: SteamPuffTexture, puffScale: Float) :
    PuffingClone(parent, puffType, puffScale) {

    override val leftHands: Array<Spatial> = Array(13) {
        parent.context.loadModel("Flute_LeftHand%02d.obj".format(it), "hands.bmp")
    }

    override val rightHands: Array<Spatial> = Array(12) {
        parent.context.loadModel("Flute_RightHand%02d.obj".format(it), "hands.bmp")
    }

    init {
        loadHands()
    }

    override fun moveForPolyphony() {
        offsetNode.setLocalTranslation((5 * indexForMoving()).toFloat(), 0f, (5 * -indexForMoving()).toFloat())
    }
}