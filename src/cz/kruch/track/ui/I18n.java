/*
 * Copyright 2006-2007 Ales Pour <kruhc@seznam.cz>.
 * All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 */

package cz.kruch.track.ui;

import cz.kruch.track.configuration.Config;

import java.util.Hashtable;

/**
 * Localization helper.
 *
 * @author Ales Pour <kruhc@seznam.cz>
 */
public final class I18n {
    public static final String ENGLISH  = "English";
    public static final String CESKY    = "\u010cesky";
    public static final String POLSKI   = "Polski";
    public static final String FRANCAIS = "Fran\u00e7ais";
    public static final String TURKCE   = "T\uc3bcrk\u00e7e";
    public static final String DEUTSCH  = "Deutsch";
    public static final String ITALIANO = "Italiano";
    public static final String ESPANOL  = "Espa\u00f1ol";
    public static final String PYCCKU   = "\u0420\u0443\u0441\u0441\u043a\u0438\u0439";

    public static final String[] LANGUAGES = {
        ENGLISH, CESKY, POLSKI, DEUTSCH, ESPANOL, FRANCAIS, ITALIANO, PYCCKU, TURKCE
    };

    public static final String MENU_START       = "menu.start";
    public static final String MENU_STOP        = "menu.stop";
    public static final String MENU_PAUSE       = "menu.pause";
    public static final String MENU_CONTINUE    = "menu.continue";
    public static final String MENU_LOADMAP     = "menu.loadmap";
    public static final String MENU_LOADATLAS   = "menu.loadatlas";
    public static final String MENU_SETTINGS    = "menu.settings";
    public static final String MENU_INFO        = "menu.info";
    public static final String MENU_EXIT        = "menu.exit";

    static final Hashtable tables = new Hashtable();

    static {
        // english
        Hashtable t = new Hashtable();
        t.put(MENU_START, "Start");
        t.put(MENU_STOP, "Stop");
        t.put(MENU_PAUSE, "Pause");
        t.put(MENU_CONTINUE, "Continue");
        t.put(MENU_LOADMAP, "Load Map");
        t.put(MENU_LOADATLAS, "Load Atlas");
        t.put(MENU_SETTINGS, "Settings");
        t.put(MENU_INFO, "Info");
        t.put(MENU_EXIT, "Exit");
        tables.put(ENGLISH, t);
        // cesky
        t = new Hashtable();
        t.put(MENU_START, "Start");
        t.put(MENU_STOP, "Stop");
        t.put(MENU_PAUSE, "Pauza");
        t.put(MENU_CONTINUE, "Pokra\u010dovat");
        t.put(MENU_LOADMAP, "Nahrát mapu");
        t.put(MENU_LOADATLAS, "Nahrát atlas");
        t.put(MENU_SETTINGS, "Nastavení");
        t.put(MENU_INFO, "Info");
        t.put(MENU_EXIT, "Konec");
        tables.put(CESKY, t);
        // polski
        t = new Hashtable();
        t.put(MENU_START, "Start");
        t.put(MENU_STOP, "Stop");
        t.put(MENU_PAUSE, "Pauza");
        t.put(MENU_CONTINUE, "Dalej");
        t.put(MENU_LOADMAP, "Nowa Mapa");
        t.put(MENU_LOADATLAS, "Nowy Atlas");
        t.put(MENU_SETTINGS, "Ustawienia");
        t.put(MENU_INFO, "Informacje");
        t.put(MENU_EXIT, "Koniec");
        tables.put(POLSKI, t);
        // francais
        t = new Hashtable();
        t.put(MENU_START, "Démarrer");
        t.put(MENU_STOP, "Arr\u00eater");
        t.put(MENU_PAUSE, "Pause");
        t.put(MENU_CONTINUE, "Continuer");
        t.put(MENU_LOADMAP, "Charger carte");
        t.put(MENU_LOADATLAS, "Charger atlas");
        t.put(MENU_SETTINGS, "Param\u00e8tres");
        t.put(MENU_INFO, "Information");
        t.put(MENU_EXIT, "Sortie");
        tables.put(FRANCAIS, t);
        // turkce
        t = new Hashtable();
        t.put(MENU_START, "Ba\uc59flat");
        t.put(MENU_STOP, "Durdur");
        t.put(MENU_PAUSE, "Duraklat");
        t.put(MENU_CONTINUE, "Devam Et");
        t.put(MENU_LOADMAP, "Harita Y\uc3bckle");
        t.put(MENU_LOADATLAS, "Atlas Y\uc3bckle");
        t.put(MENU_SETTINGS, "Ayarlar");
        t.put(MENU_INFO, "Bilgi");
        t.put(MENU_EXIT, "\uc387\uc4b1k\uc4b1\uc59f");
        tables.put(TURKCE, t);
        // deutsch
        t = new Hashtable();
        t.put(MENU_START, "Start");
        t.put(MENU_STOP, "Stop");
        t.put(MENU_PAUSE, "Pause");
        t.put(MENU_CONTINUE, "Fortsetzen");
        t.put(MENU_LOADMAP, "Karte laden");
        t.put(MENU_LOADATLAS, "Atlas laden");
        t.put(MENU_SETTINGS, "Einstellungen");
        t.put(MENU_INFO, "Info");
        t.put(MENU_EXIT, "Beenden");
        tables.put(DEUTSCH, t);
        // italiano
        t = new Hashtable();
        t.put(MENU_START, "Inizio");
        t.put(MENU_STOP, "Fine");
        t.put(MENU_PAUSE, "Pausa");
        t.put(MENU_CONTINUE, "Continua");
        t.put(MENU_LOADMAP, "Carica Mappa");
        t.put(MENU_LOADATLAS, "Carica Atlante");
        t.put(MENU_SETTINGS, "Configurazione");
        t.put(MENU_INFO, "Info");
        t.put(MENU_EXIT, "Uscita");
        tables.put(ITALIANO, t);
        // espanol
        t = new Hashtable();
        t.put(MENU_START, "Iniciar");
        t.put(MENU_STOP, "Parar");
        t.put(MENU_PAUSE, "Pausa");
        t.put(MENU_CONTINUE, "Continuar");
        t.put(MENU_LOADMAP, "Abrir Mapa");
        t.put(MENU_LOADATLAS, "Abrir Atlas");
        t.put(MENU_SETTINGS, "Propiedades");
        t.put(MENU_INFO, "Info");
        t.put(MENU_EXIT, "Salir");
        tables.put(ESPANOL, t);
        // pyccku
        t = new Hashtable();
        t.put(MENU_START, "C\u0442ap\u0442");
        t.put(MENU_STOP, "C\u0442o\u043f");
        t.put(MENU_PAUSE, "\u041fay\u0437a");
        t.put(MENU_CONTINUE, "\u041fpo\u0434o\u043b\u0436\u0438\u0442\u044c");
        t.put(MENU_LOADMAP, "\u0417a\u0433py\u0437\u0438\u0442\u044a \u043aap\u0442y");
        t.put(MENU_LOADATLAS, "\u0417a\u0433py\u0437\u0438\u0442\u044a a\u0442\u043bac");
        t.put(MENU_SETTINGS, "\u041dac\u0442po\u0439\u043a\u0438");
        t.put(MENU_INFO, "\u0418\u043d\u0444op\u043ca\u0446\u0438\u044f");
        t.put(MENU_EXIT, "B\u044b\u0445o\u0434");
        tables.put(PYCCKU, t);
    }

    public static String resolve(String key) {
        String value = null;
        try {
            value = (String) ((Hashtable) tables.get(Config.language)).get(key);
        } catch (NullPointerException e) {
            // not found
        }
        if (value == null) {
            return key;
        }
        return value;
    }
}
