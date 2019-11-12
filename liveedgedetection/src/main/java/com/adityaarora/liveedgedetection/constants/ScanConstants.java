package com.adityaarora.liveedgedetection.constants;

/**
 * This class defines constants
 */

public class ScanConstants {
    /**
     * Campo JSON con path dell'immagine o del PDF salvato
     */
    public static final String PATH_RESULT = "path";

    /**
     * Campo JSON per identificare il tipo di documento
     */
    public static final String TYPE_RESULT = "type";

    /**
     * Campo JSON per identificare la modalità di acquisizione
     */
    public static final String FROM_MANUAL_MODE = "fromManualMode";

    /**
     * Intestazione del nome dell'immagine croppata e salvata
     */
    public static final String IMAGE_NAME = "crop_";
    public static final String IMAGE_FOLDER = "/Unisalute";
    public static final String SCHEME = "content";
    public static final String PDF_EXT = "pdf";

    /**
     * Intervallo per acquisizione del frame
     */
    public static final int INTERVAL_FRAME = 800;

    /**
     * Soglia minima dello sfondo
     */
    public static final int THRESHOLD = 155;
    public static final int HIGHER_SAMPLING_THRESHOLD = 2200;
    public static final int PHOTO_QUALITY = 90;

    /**
     * Intervallo per visualizzare la modalità manuale
     */
    public static final int SHOW_MANUAL_MODE_INTERVAL = 5000;
}
