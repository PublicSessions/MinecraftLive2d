package com.ciallo;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Live2d implements ModInitializer {
	public static final String MOD_ID = "live2d";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {

		LOGGER.info("Live2d by P1ay2r");
	}
}