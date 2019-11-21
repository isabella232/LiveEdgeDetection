package com.adityaarora.liveedgedetection.constants;

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
    public static final String ACQUISITION_MODE = "acquisitionMode";

    /**
     * Intestazione del nome dell'immagine croppata e salvata
     */
    public static final String IMAGE_NAME = "crop_";
    public static final String IMAGE_FOLDER = "/Unisalute"; // TODO Sostituire con percorso reale quando verrà implementata la funziionalità
    public static final String SCHEME = "content";
    public static final String PDF_EXT = "pdf";
    public static final String[] MIME_TYPES = { "image/*", "application/pdf" };

    /**
     * Intervallo per acquisizione del frame
     */
    public static final int INTERVAL_FRAME = 700;

    /**
     * Soglia minima dello sfondo
     */
    public static final int BACKGROUND_THRESHOLD = 155;
    public static final int HIGHER_SAMPLING_THRESHOLD = 2200;
    public static final int PHOTO_QUALITY = 80;

    /**
     * Intervallo per visualizzare la modalità manuale
     */
    public static final int SHOW_MANUAL_MODE_INTERVAL = 5000;
}
