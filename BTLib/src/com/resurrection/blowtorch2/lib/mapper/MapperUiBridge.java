package com.resurrection.blowtorch2.lib.mapper;

/**
 * Thin UI hook for open/close/mode of the mapper overlay.
 * {@link com.resurrection.blowtorch2.lib.window.MainWindow} (or
 * {@link MapperOverlayController}) implements this and registers via
 * {@link MapperController#setUiBridge}.
 */
public interface MapperUiBridge {

	/** Show the mapper overlay (floating or fullscreen per settings). */
	void openMapUi();

	/** Hide the mapper overlay. */
	void closeMapUi();

	/** Toggle visibility. */
	void toggleMapUi();

	/**
	 * Preferred presentation mode.
	 *
	 * @param fullscreen true = fullscreen overlay; false = floating window
	 */
	void setMapMode(boolean fullscreen);

	/** Center the view on the current player tile (if known). */
	void centerOnPlayer();

	/**
	 * Zoom the map view.
	 *
	 * @param action {@code in}, {@code out}, {@code reset}, or a float scale factor
	 */
	void zoomMap(String action);

	/** Request a redraw after map/controller state changed. */
	void onMapModelChanged();
}
