/*
 * Copyright (C) Dan Block 2013
 */
package com.resurrection.blowtorch2.lib.service;

/** Service -> Launcher. One-way for the same reason as IWindowCallback: the
 *  launcher lives in the UI process, and this fires from the service on a
 *  timer, so a synchronous call would eventually land on a frozen process. */
oneway interface ILauncherCallback {
	void connectionDisconnected();
}