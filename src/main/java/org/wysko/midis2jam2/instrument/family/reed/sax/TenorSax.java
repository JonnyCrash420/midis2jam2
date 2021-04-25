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

package org.wysko.midis2jam2.instrument.family.reed.sax;

import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Quaternion;
import com.jme3.scene.Node;
import org.wysko.midis2jam2.Midis2jam2;
import org.wysko.midis2jam2.instrument.algorithmic.PressedKeysFingeringManager;
import org.wysko.midis2jam2.instrument.clone.Clone;
import org.wysko.midis2jam2.midi.MidiChannelSpecificEvent;

import java.util.List;

import static org.wysko.midis2jam2.Midis2jam2.rad;

/**
 * The Tenor sax.
 */
public class TenorSax extends Saxophone {
	
	public static final PressedKeysFingeringManager FINGERING_MANAGER = PressedKeysFingeringManager.from(TenorSax.class);
	
	private static final float STRETCH_FACTOR = 0.65f;
	
	/**
	 * Constructs a tenor sax.
	 *
	 * @param context context to midis2jam2
	 * @param events  all events that pertain to this instance of a tenor sax
	 */
	public TenorSax(Midis2jam2 context,
	                List<MidiChannelSpecificEvent> events)
			throws ReflectiveOperationException {
		
		super(context, events, TenorSaxClone.class, FINGERING_MANAGER);
		
		groupOfPolyphony.setLocalTranslation(-11, 34.5f, -22);
		groupOfPolyphony.setLocalScale(1.15f);
	}
	
	/**
	 * Implements {@link Clone}, as tenor sax clones.
	 */
	public class TenorSaxClone extends SaxophoneClone {
		
		/**
		 * Instantiates a new Tenor sax clone.
		 */
		public TenorSaxClone() {
			super(TenorSax.this, STRETCH_FACTOR);
			
			var shinyHornSkin = context.reflectiveMaterial("Assets/HornSkinGrey.bmp");
			var black = new Material(context.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
			black.setColor("Color", ColorRGBA.Black);
			
			this.body = context.getAssetManager().loadModel("Assets/TenorSaxBody.fbx");
			this.bell.attachChild(context.getAssetManager().loadModel("Assets/TenorSaxHorn.obj"));
			
			var bodyNode = ((Node) body);
			
			bodyNode.getChild(0).setMaterial(shinyHornSkin);
			bodyNode.getChild(1).setMaterial(black);
			bell.setMaterial(shinyHornSkin);
			
			modelNode.attachChild(body);
			modelNode.attachChild(bell);
			bell.move(0, -22, 0); // Move bell down to body
			
			animNode.setLocalTranslation(0, 0, 20);
			highestLevel.setLocalRotation(new Quaternion().fromAngles(rad(10), rad(30), 0));
		}
	}
}
